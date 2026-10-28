package com.moa.babylanguage

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.annotation.MainThread
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.resume

/** Play owns purchases. Only a current server-signed entitlement unlocks full access. */
class BillingManager internal constructor(
    private val verifier: BillingEntitlements,
    gatewayFactory: (PurchasesUpdatedListener) -> BillingGateway,
    private val clock: () -> Long = System::currentTimeMillis,
    private val recoveryClock: () -> Long = SystemClock::elapsedRealtime,
    private val purchaseCallbackTimeoutMs: Long = 120_000,
    private val purchaseQueryTimeoutMs: Long = 30_000,
    private val acknowledgeRetryDelayMs: Long = 5_000,
    private val reviewer: ReviewEntitlements? = null,
    private val accessCodes: CodeEntitlements? = null
) : PurchasesUpdatedListener, AutoCloseable {
    constructor(context: Context) : this(
        PurchaseVerifier(context.applicationContext),
        { listener -> PlayBillingGateway(context.applicationContext, listener) },
        reviewer = ReviewAccessVerifier(context.applicationContext),
        accessCodes = AccessCodeVerifier(context.applicationContext)
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(BillingUiState(
        status = if (verifier.isConfigured) BillingStatus.CONNECTING else BillingStatus.UNAVAILABLE,
        isPremium = verifier.cachedEntitlement(),
        message = if (verifier.isConfigured) null else "구독 확인 서비스가 아직 설정되지 않았어요. 무료 기능은 계속 사용할 수 있어요.",
        reviewStatus = if (reviewer?.isConfigured == true) ReviewAccessStatus.INACTIVE else ReviewAccessStatus.UNAVAILABLE,
        codeStatus = if (accessCodes?.isConfigured == true) ReviewAccessStatus.INACTIVE else ReviewAccessStatus.UNAVAILABLE
    ))
    val state: StateFlow<BillingUiState> = mutableState
    // Unconfigured installations use only free features and never need a Play client.
    private val clientDelegate = lazy(LazyThreadSafetyMode.NONE) { gatewayFactory(this) }
    private val client by clientDelegate
    private var closed = false
    private var connecting = false
    private var catalogInFlight = false
    private var queryInFlight: Long? = null
    private var querySequence = 0L
    private var queryDeadline: Job? = null
    private var purchaseDeadline: Long? = null
    private var purchaseRecovery: Job? = null
    private var purchaseHistoryConfirmed = false
    private var restoreRequested = false
    private var generation = 0L
    private var verification: Job? = null
    private var acknowledgement: Job? = null
    private var expiry: Job? = null
    private var reviewGeneration = 0L
    private var reviewVerification: Job? = null
    private var nextReviewRetryElapsed = 0L
    private var codeGeneration = 0L
    private var codeVerification: Job? = null
    private var nextCodeRetryElapsed = 0L

    init { refresh() }

    /** Every protected action must call this; StateFlow.isPremium is only a display snapshot. */
    @MainThread
    fun hasCurrentEntitlement(): Boolean {
        if (closed) return false
        recheckEntitlement()
        return mutableState.value.isPremium
    }

    fun refresh() {
        if (closed) return
        recheckEntitlement()
        refreshReviewAccess()
        refreshAccessCode()
        if (!verifier.isConfigured) return
        if (purchaseDeadline != null) {
            recoverPurchaseIfDue()
            return
        }
        if (!client.isReady) { connect(); return }
        queryCatalog()
        // Returning from the Play activity may precede its purchase callback.
        if (mutableState.value.status != BillingStatus.PURCHASING && verification?.isActive != true) queryPurchases()
    }

    /** The code goes to the verifier; it is never a local switch or proof of Play ownership. */
    @MainThread
    fun activateReviewAccess(code: String) {
        if (closed) return
        val clean = code.trim()
        if (!validReviewCode(clean)) {
            updateReview(ReviewAccessStatus.ERROR, "심사 안내에 있는 접근 코드를 그대로 입력해 주세요.")
            return
        }
        if (reviewer?.isConfigured != true) {
            updateReview(ReviewAccessStatus.UNAVAILABLE, "심사 접근 서비스가 아직 설정되지 않았어요.")
            return
        }
        verifyReviewAccess(clean, activation = true)
    }

    @MainThread
    fun deactivateReviewAccess() {
        if (closed) return
        reviewGeneration++
        reviewVerification?.cancel()
        reviewVerification = null
        reviewer?.clear()
        nextReviewRetryElapsed = 0L
        updateReview(if (reviewer?.isConfigured == true) ReviewAccessStatus.INACTIVE else ReviewAccessStatus.UNAVAILABLE,
            "심사 접근을 해제했어요. 구매 이용권과 기존 기록은 유지돼요.")
        recheckEntitlement()
    }

    private fun refreshReviewAccess() {
        if (closed || reviewer?.isConfigured != true || reviewVerification?.isActive == true) return
        val code = reviewer.storedCode() ?: return
        if (recoveryClock() < nextReviewRetryElapsed) return
        verifyReviewAccess(code, activation = false)
    }

    private fun verifyReviewAccess(code: String, activation: Boolean) {
        val review = reviewer ?: return
        if (reviewVerification?.isActive == true) return
        val token = ++reviewGeneration
        nextReviewRetryElapsed = recoveryClock() + 60_000
        updateReview(ReviewAccessStatus.VERIFYING, "심사 이용권을 안전하게 확인하고 있어요.")
        reviewVerification = scope.launch {
            val result = try { review.verify(code) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { VerificationResult.Unavailable }
            if (closed || token != reviewGeneration) return@launch
            val accepted = result is VerificationResult.Verified && review.accept(result.envelope, code)
            if (result == VerificationResult.Rejected && !activation) review.clear()
            recheckEntitlement()
            val active = review.cachedExpiryMs() != null
            if (active && !busy()) setState(BillingStatus.PREMIUM, null)
            updateReview(if (active) ReviewAccessStatus.ACTIVE else ReviewAccessStatus.ERROR, when {
                accepted -> "Play 심사 접근이 활성화됐어요. 결제 없이 모든 기능을 확인할 수 있어요."
                result == VerificationResult.Rejected -> "심사 접근 코드가 유효하지 않거나 해제됐어요. 심사 안내를 확인해 주세요."
                active -> "이전에 서명 확인한 심사 이용권을 사용 중이에요. 연결되면 다시 확인해 주세요."
                else -> "심사 접근을 확인하지 못했어요. 인터넷 연결 후 다시 확인해 주세요."
            })
        }
    }

    @MainThread
    fun activateAccessCode(code: String) {
        if (closed) return
        val clean = code.trim()
        if (!validReviewCode(clean)) {
            updateCode(ReviewAccessStatus.ERROR, "전달받은 이용 코드를 그대로 붙여 넣어 주세요.")
            return
        }
        if (accessCodes?.isConfigured != true) {
            updateCode(ReviewAccessStatus.UNAVAILABLE, "이용 코드 확인 서비스를 연결하지 못했어요.")
            return
        }
        verifyAccessCode(clean, activation = true)
    }

    @MainThread
    fun deactivateAccessCode() {
        if (closed) return
        codeGeneration++; codeVerification?.cancel(); codeVerification = null
        accessCodes?.clear(); nextCodeRetryElapsed = 0L
        updateCode(if (accessCodes?.isConfigured == true) ReviewAccessStatus.INACTIVE else ReviewAccessStatus.UNAVAILABLE,
            "이 기기의 코드 적용을 해제했어요. 구매 구독과 기존 기록은 유지돼요. 할인 코드를 다른 기기로 옮기는 기능은 아니에요.")
        recheckEntitlement()
    }

    private fun refreshAccessCode() {
        if (closed || accessCodes?.isConfigured != true || codeVerification?.isActive == true ||
            recoveryClock() < nextCodeRetryElapsed) return
        accessCodes.storedCode()?.let { verifyAccessCode(it, activation = false) }
    }

    private fun verifyAccessCode(code: String, activation: Boolean) {
        val access = accessCodes ?: return
        if (codeVerification?.isActive == true) return
        val token = ++codeGeneration
        nextCodeRetryElapsed = recoveryClock() + 60_000
        updateCode(ReviewAccessStatus.VERIFYING, "이용 코드를 확인하고 있어요.")
        codeVerification = scope.launch {
            val result = try { access.verify(code) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { VerificationResult.Unavailable }
            if (closed || token != codeGeneration) return@launch
            val accepted = result is VerificationResult.Verified && runCatching { access.accept(result.envelope, code) }.getOrDefault(false)
            if (result == VerificationResult.Rejected && !activation) access.clear()
            recheckEntitlement()
            val grant = access.cachedGrant()
            if (accepted && !busy()) setState(idleStatus(), null)
            updateCode(if (grant != null) ReviewAccessStatus.ACTIVE else ReviewAccessStatus.ERROR, when {
                accepted && grant?.kind == AccessCodeKind.INVITATION -> "초대 이용권이 적용됐어요. 자동 결제 없이 기간 제한 없이 이용해요."
                accepted -> "출시 할인 코드가 등록됐어요. 할인 상품과 Google Play 결제창의 금액을 확인해 주세요."
                result == VerificationResult.Rejected -> "유효하지 않거나 다른 기기에 등록된 코드예요. 전달받은 코드를 확인해 주세요."
                grant != null -> "이전에 확인한 코드 혜택을 사용 중이에요. 연결되면 다시 확인해 주세요."
                else -> "코드를 확인하지 못했어요. 인터넷에 연결해 다시 시도해 주세요."
            })
        }
    }

    private fun updateCode(status: ReviewAccessStatus, message: String?) {
        mutableState.value = mutableState.value.copy(codeStatus = status, codeMessage = message)
    }

    private fun updateReview(status: ReviewAccessStatus, message: String?) {
        mutableState.value = mutableState.value.copy(reviewStatus = status, reviewMessage = message)
    }

    fun restore() {
        if (closed) return
        recheckEntitlement()
        if (!verifier.isConfigured) {
            setState(BillingStatus.UNAVAILABLE, "구독 확인 서비스가 설정되지 않았어요. 무료 기능은 계속 사용할 수 있어요.")
            return
        }
        if (purchaseDeadline != null) {
            restoreRequested = true
            recoverPurchaseIfDue()
            return
        }
        if (verification?.isActive == true) return
        restoreRequested = true
        if (!client.isReady) { connect(); return }
        queryPurchases()
    }

    fun purchase(activity: Activity, plan: BillingPlan) {
        if (closed || activity.isFinishing || activity.isDestroyed) return
        recheckEntitlement()
        if (!verifier.isConfigured) { setState(BillingStatus.UNAVAILABLE, "구독 확인 서비스가 설정되지 않았어요."); return }
        if (plan == BillingPlan.LAUNCH_MONTHLY && accessCodes?.cachedGrant()?.kind != AccessCodeKind.LAUNCH_OFFER) {
            setState(BillingStatus.ERROR, "출시 할인 코드를 먼저 등록해 주세요. 일반 가격으로 결제하지 않았어요.")
            return
        }
        // Both the UI and manager guard duplicate taps; pending purchases cannot be charged again.
        if (mutableState.value.isPremium || mutableState.value.status != BillingStatus.READY || queryInFlight != null || !purchaseHistoryConfirmed) return
        if (!client.isReady || mutableState.value.options.none { it.plan == plan }) {
            setState(BillingStatus.ERROR, "구독 상품을 불러오지 못했어요. 다시 확인해 주세요.")
            return
        }
        invalidateVerification()
        purchaseHistoryConfirmed = false
        setState(BillingStatus.PURCHASING, null)
        purchaseDeadline = recoveryClock() + purchaseCallbackTimeoutMs
        purchaseRecovery = scope.launch {
            delay(purchaseCallbackTimeoutMs)
            recoverPurchaseIfDue()
        }
        val result = client.purchase(activity, plan)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            finishPurchaseLaunch()
            setState(BillingStatus.ERROR, "Google Play 결제 화면을 열지 못했어요. 다시 확인해 주세요.")
        }
    }

    /** Give Play's return callback time to arrive before an empty query may end a launch. */
    private fun recoverPurchaseIfDue() {
        val deadline = purchaseDeadline ?: return
        if (closed || recoveryClock() < deadline || queryInFlight != null) return
        if (!client.isReady) {
            finishPurchaseLaunch()
            setState(BillingStatus.ERROR, "Google Play 연결을 다시 확인해 주세요. 구매 내역을 확인한 뒤 다시 시도할 수 있어요.")
            return
        }
        queryPurchases()
    }

    private fun finishPurchaseLaunch() {
        purchaseDeadline = null
        purchaseRecovery?.cancel()
        purchaseRecovery = null
    }

    private fun connect() {
        if (closed || connecting) return
        connecting = true
        setState(BillingStatus.CONNECTING, null)
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (closed) return
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
                else setState(BillingStatus.ERROR, "Google Play에 연결할 수 없어요. 무료 기능은 계속 사용할 수 있어요.")
            }
            override fun onBillingServiceDisconnected() {
                connecting = false
                if (!closed) setState(BillingStatus.ERROR, "Google Play 연결이 끊겼어요. 다시 확인해 주세요.")
            }
        })
    }

    private fun queryCatalog() {
        if (catalogInFlight) return
        catalogInFlight = true
        client.queryCatalog catalogResult@ { result, catalog ->
            catalogInFlight = false
            if (closed) return@catalogResult
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                mutableState.value = mutableState.value.copy(options = emptyList())
                if (!busy()) setState(BillingStatus.ERROR, "구독 상품을 불러오지 못했어요. 다시 확인해 주세요.")
                return@catalogResult
            }
            val options = catalog.options
            val before = mutableState.value
            mutableState.value = before.copy(
                products = catalog.products,
                options = options,
                status = if (busy()) before.status else idleStatus(options.isNotEmpty()),
                message = if (busy()) before.message else if (options.isEmpty()) "Google Play에 구독 상품이 아직 표시되지 않아요." else before.message
            )
        }
    }

    private fun queryPurchases() {
        if (queryInFlight != null) return
        val request = ++querySequence
        queryInFlight = request
        val token = generation
        queryDeadline = scope.launch {
            delay(purchaseQueryTimeoutMs)
            if (closed || queryInFlight != request || token != generation) return@launch
            queryInFlight = null
            queryDeadline = null
            purchaseHistoryConfirmed = false
            finishPurchaseLaunch()
            restoreRequested = false
            setState(BillingStatus.ERROR, "구매 내역 확인이 지연되고 있어요. 다시 확인한 뒤 결제해 주세요.")
        }
        client.queryPurchases purchasesResult@ { result, purchases ->
            if (closed || token != generation || queryInFlight != request) return@purchasesResult
            queryInFlight = null
            queryDeadline?.cancel()
            queryDeadline = null
            finishPurchaseLaunch()
            val restoring = restoreRequested
            restoreRequested = false
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                purchaseHistoryConfirmed = false
                setState(BillingStatus.ERROR, "구매 내역을 확인하지 못했어요. 인터넷 연결을 확인해 주세요.")
                return@purchasesResult
            }
            handlePurchases(purchases, restoring)
        }
    }

    private fun handlePurchases(purchases: List<Purchase>, restoring: Boolean) {
        finishPurchaseLaunch()
        restoreRequested = false
        purchaseHistoryConfirmed = false
        val relevant = purchases.filter { PurchaseVerifier.PRODUCT_ID in it.products }
        val purchased = relevant.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.distinctBy { it.purchaseToken }
        when {
            purchased.isNotEmpty() -> verifyPurchases(purchased, restoring)
            relevant.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> {
                invalidateVerification()
                setState(BillingStatus.PENDING, "결제가 보류 중이에요. Google Play에서 완료되면 다시 확인해 주세요.")
            }
            else -> {
                invalidateVerification()
                verifier.clear()
                recheckEntitlement()
                purchaseHistoryConfirmed = true
                setState(idleStatus(), if (restoring) "복원할 활성 구독이 없어요." else null)
            }
        }
    }

    private fun verifyPurchases(purchases: List<Purchase>, restoring: Boolean) {
        invalidateVerification()
        val token = generation
        setState(BillingStatus.VERIFYING, "Google Play 구독을 확인하고 있어요.")
        verification = scope.launch {
            var verified = false
            var unavailable = false
            var unacknowledged: Purchase? = null
            // A malformed purchase list cannot cause an unbounded sequence of network calls.
            for (purchase in purchases.take(4)) {
                val result = verifier.verify(PurchaseVerifier.PRODUCT_ID, purchase.purchaseToken)
                if (closed || token != generation) return@launch
                when (result) {
                    is VerificationResult.Verified -> {
                        verified = verifier.accept(result.envelope)
                        if (verified && !purchase.isAcknowledged && !acknowledgesPurchase(result.envelope, purchase.purchaseToken)) unacknowledged = purchase
                        if (verified) break
                        unavailable = true
                    }
                    VerificationResult.Unavailable -> unavailable = true
                    VerificationResult.Rejected -> Unit
                }
            }
            if (closed || token != generation) return@launch
            if (!verified && !unavailable) verifier.clear()
            recheckEntitlement()
            val entitlement = mutableState.value.isPremium
            setState(if (entitlement) BillingStatus.PREMIUM else BillingStatus.ERROR, when {
                verified -> if (restoring) "구독을 복원했어요." else null
                entitlement -> "이전에 확인한 이용권을 임시로 사용 중이에요. 연결되면 다시 확인해 주세요."
                !unavailable -> "현재 이용할 수 있는 구독이 없어요. Google Play 구독 상태를 확인해 주세요."
                else -> "구독을 확인하지 못했어요. 잠시 후 다시 확인해 주세요."
            })
            unacknowledged?.let { acknowledge(it, token) }
        }
    }

    private fun acknowledge(purchase: Purchase, token: Long) {
        acknowledgement = scope.launch {
            repeat(3) { attempt ->
                val result = withTimeoutOrNull(purchaseQueryTimeoutMs) {
                    suspendCancellableCoroutine<BillingResult> { continuation ->
                        client.acknowledge(purchase.purchaseToken) { reply ->
                            if (continuation.isActive) continuation.resume(reply)
                        }
                    }
                }
                if (closed || token != generation) return@launch
                if (result?.responseCode == BillingClient.BillingResponseCode.OK) {
                    if (mutableState.value.message == "결제 확인이 지연되고 있어요. 앱을 다시 열어 확인해 주세요.") {
                        mutableState.value = mutableState.value.copy(message = null)
                    }
                    return@launch
                }
                mutableState.value = mutableState.value.copy(message = "결제 확인이 지연되고 있어요. 앱을 다시 열어 확인해 주세요.")
                if (attempt < 2) delay(acknowledgeRetryDelayMs * (attempt + 1))
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (closed) return
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> if (purchases?.any { PurchaseVerifier.PRODUCT_ID in it.products } == true) handlePurchases(purchases, false) else {
                if (verification?.isActive == true) return
                finishPurchaseLaunch()
                setState(idleStatus(), null); refresh()
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> if (purchaseDeadline != null) {
                finishPurchaseLaunch()
                setState(idleStatus(), "결제를 취소했어요.")
                // Reconcile even cancellation before allowing another launch.
                queryPurchases()
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                if (verification?.isActive == true) return
                finishPurchaseLaunch(); setState(idleStatus(), null); restore()
            }
            else -> if (purchaseDeadline != null) {
                finishPurchaseLaunch()
                setState(BillingStatus.ERROR, "결제를 완료하지 못했어요. 다시 확인해 주세요.")
            }
        }
    }

    private fun invalidateVerification() {
        generation++
        verification?.cancel()
        verification = null
        acknowledgement?.cancel()
        acknowledgement = null
        queryInFlight = null
        queryDeadline?.cancel()
        queryDeadline = null
    }
    private fun busy() = purchaseDeadline != null || mutableState.value.status in setOf(BillingStatus.PURCHASING, BillingStatus.PENDING, BillingStatus.VERIFYING)
    private fun idleStatus(hasOptions: Boolean = mutableState.value.options.isNotEmpty()) = when {
        mutableState.value.isPremium -> BillingStatus.PREMIUM
        hasOptions -> BillingStatus.READY
        else -> BillingStatus.UNAVAILABLE
    }
    private fun recheckEntitlement() {
        expiry?.cancel()
        val wasPremium = mutableState.value.isPremium
        val purchaseUntil = verifier.cachedExpiryMs()
        val reviewUntil = reviewer?.cachedExpiryMs()
        val codeGrant = accessCodes?.cachedGrant()
        val invitationUntil = codeGrant?.takeIf { it.kind == AccessCodeKind.INVITATION }?.expiresAtMs
        val validUntil = listOfNotNull(purchaseUntil, reviewUntil, invitationUntil).maxOrNull()
        val refreshUntil = listOfNotNull(validUntil, codeGrant?.expiresAtMs).maxOrNull()
        val before = mutableState.value
        mutableState.value = before.copy(isPremium = validUntil != null,
            entitlementSource = when {
                purchaseUntil != null -> EntitlementSource.PLAY_PURCHASE
                reviewUntil != null -> EntitlementSource.PLAY_REVIEW
                invitationUntil != null -> EntitlementSource.INVITATION
                else -> EntitlementSource.NONE
            },
            reviewStatus = when {
                reviewer?.isConfigured != true -> ReviewAccessStatus.UNAVAILABLE
                before.reviewStatus == ReviewAccessStatus.VERIFYING -> ReviewAccessStatus.VERIFYING
                reviewUntil != null -> ReviewAccessStatus.ACTIVE
                before.reviewStatus == ReviewAccessStatus.ACTIVE -> ReviewAccessStatus.ERROR
                else -> before.reviewStatus
            },
            codeKind = codeGrant?.kind ?: AccessCodeKind.NONE,
            codeStatus = when {
                accessCodes?.isConfigured != true -> ReviewAccessStatus.UNAVAILABLE
                before.codeStatus == ReviewAccessStatus.VERIFYING -> ReviewAccessStatus.VERIFYING
                codeGrant != null -> ReviewAccessStatus.ACTIVE
                before.codeStatus == ReviewAccessStatus.ACTIVE -> ReviewAccessStatus.ERROR
                else -> before.codeStatus
            },
            codeMessage = if (codeGrant == null && before.codeStatus == ReviewAccessStatus.ACTIVE)
                "코드를 다시 확인해야 해요. 인터넷에 연결해 이용 코드를 확인해 주세요." else before.codeMessage,
            reviewMessage = if (reviewUntil == null && before.reviewStatus == ReviewAccessStatus.ACTIVE)
                "심사 이용권 확인 기간이 끝났어요. 인터넷에 연결해 다시 확인해 주세요." else before.reviewMessage)
        if (wasPremium && validUntil == null && !busy()) setState(idleStatus(), "구독 확인 기간이 끝났어요. 인터넷에 연결해 다시 확인해 주세요.")
        if (refreshUntil != null) expiry = scope.launch {
            // Recheck wall-clock changes too; holding the screen open cannot keep an expired grant.
            delay((refreshUntil - clock()).coerceIn(1, 60_000))
            recheckEntitlement()
            // Renew the install-bound review grant after expiry or near its deadline.
            if (reviewUntil == null || reviewUntil - clock() <= 300_000) refreshReviewAccess()
            if (codeGrant == null || codeGrant.expiresAtMs - clock() <= 300_000) refreshAccessCode()
        }
    }
    private fun setState(status: BillingStatus, message: String?) { mutableState.value = mutableState.value.copy(status = status, message = message) }
    override fun close() {
        if (closed) return
        closed = true
        generation++
        reviewGeneration++
        codeGeneration++
        scope.cancel()
        if (clientDelegate.isInitialized()) client.endConnection()
    }
}

internal data class BillingCatalog(val products: List<BillingProduct>, val options: List<BillingOption>)

/** Test implementations live only in androidTest; there is no runtime switch or intent override. */
internal interface BillingGateway {
    val isReady: Boolean
    fun startConnection(listener: BillingClientStateListener)
    fun queryCatalog(callback: (BillingResult, BillingCatalog) -> Unit)
    fun queryPurchases(callback: (BillingResult, List<Purchase>) -> Unit)
    fun purchase(activity: Activity, plan: BillingPlan): BillingResult
    fun acknowledge(purchaseToken: String, callback: (BillingResult) -> Unit)
    fun endConnection()
}

/** Production adapter always queries the Play SDK and launches its real purchase sheet. */
private class PlayBillingGateway(context: Context, listener: PurchasesUpdatedListener) : BillingGateway {
    private val client = BillingClient.newBuilder(context)
        .setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build())
        .enableAutoServiceReconnection()
        .build()
    private var productDetails: ProductDetails? = null
    private val offers = mutableMapOf<BillingPlan, ProductDetails.SubscriptionOfferDetails>()
    override val isReady: Boolean get() = client.isReady
    override fun startConnection(listener: BillingClientStateListener) = client.startConnection(listener)
    override fun queryCatalog(callback: (BillingResult, BillingCatalog) -> Unit) {
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(PurchaseVerifier.PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS).build()
        )).build()
        client.queryProductDetailsAsync(params) { result, detailsResult ->
            productDetails = if (result.responseCode == BillingClient.BillingResponseCode.OK)
                detailsResult.productDetailsList.firstOrNull { it.productId == PurchaseVerifier.PRODUCT_ID } else null
            offers.clear()
            val details = productDetails
            val options = details?.subscriptionOfferDetails.orEmpty().mapNotNull { offer ->
                val option = checkedBillingOption(details!!.productId, offer.basePlanId, offer.offerId,
                    offer.pricingPhases.pricingPhaseList.map { phase ->
                        OfferPricePhase(phase.priceAmountMicros, phase.priceCurrencyCode, phase.formattedPrice,
                            phase.billingPeriod, phase.recurrenceMode, phase.billingCycleCount)
                    }) ?: return@mapNotNull null
                offers[option.plan] = offer
                option
            }
            callback(result, BillingCatalog(details?.let { listOf(BillingProduct(it.productId, it.title, it.description)) }.orEmpty(), options))
        }
    }
    override fun queryPurchases(callback: (BillingResult, List<Purchase>) -> Unit) {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
            callback(result, purchases)
        }
    }
    override fun purchase(activity: Activity, plan: BillingPlan): BillingResult {
        val details = productDetails
        val offer = offers[plan]
        if (!client.isReady || details == null || offer == null) return BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE).build()
        return client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).setOfferToken(offer.offerToken).build()
        )).build())
    }
    override fun acknowledge(purchaseToken: String, callback: (BillingResult) -> Unit) {
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build()) { callback(it) }
    }
    override fun endConnection() = client.endConnection()
}
