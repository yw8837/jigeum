package com.moa.babylanguage

import android.app.Activity
import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Play owns purchases. Only a current server-signed entitlement unlocks the album. */
class BillingManager(context: Context) : PurchasesUpdatedListener, AutoCloseable {
    private val verifier = PurchaseVerifier(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(BillingUiState(
        status = if (verifier.isConfigured) BillingStatus.CONNECTING else BillingStatus.UNAVAILABLE,
        isPremium = verifier.cachedEntitlement(),
        message = if (verifier.isConfigured) null else "구독 확인 서비스가 아직 설정되지 않았어요. 무료 기능은 계속 사용할 수 있어요."
    ))
    val state: StateFlow<BillingUiState> = mutableState
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build())
        .enableAutoServiceReconnection()
        .build()
    private var closed = false
    private var connecting = false
    private var catalogInFlight = false
    private var queryInFlight = false
    private var restoreRequested = false
    private var generation = 0L
    private var verification: Job? = null
    private var expiry: Job? = null
    private var productDetails: ProductDetails? = null
    private val offers = mutableMapOf<BillingPlan, ProductDetails.SubscriptionOfferDetails>()

    init { refresh() }

    fun refresh() {
        if (closed) return
        recheckEntitlement()
        if (!verifier.isConfigured) return
        if (!client.isReady) { connect(); return }
        queryCatalog()
        // Returning from the Play activity may precede its purchase callback.
        if (mutableState.value.status != BillingStatus.PURCHASING && verification?.isActive != true) queryPurchases()
    }

    fun restore() {
        if (closed) return
        recheckEntitlement()
        if (!verifier.isConfigured) {
            setState(BillingStatus.UNAVAILABLE, "구독 확인 서비스가 설정되지 않았어요. 무료 기능은 계속 사용할 수 있어요.")
            return
        }
        if (mutableState.value.status == BillingStatus.PURCHASING || verification?.isActive == true) return
        restoreRequested = true
        if (!client.isReady) { connect(); return }
        queryPurchases()
    }

    fun purchase(activity: Activity, plan: BillingPlan) {
        if (closed || activity.isFinishing || activity.isDestroyed) return
        recheckEntitlement()
        if (!verifier.isConfigured) { setState(BillingStatus.UNAVAILABLE, "구독 확인 서비스가 설정되지 않았어요."); return }
        // Both the UI and manager guard duplicate taps; pending purchases cannot be charged again.
        if (mutableState.value.isPremium || mutableState.value.status != BillingStatus.READY || queryInFlight) return
        val details = productDetails
        val offer = offers[plan]
        if (!client.isReady || details == null || offer == null) {
            setState(BillingStatus.ERROR, "구독 상품을 불러오지 못했어요. 다시 확인해 주세요.")
            return
        }
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).setOfferToken(offer.offerToken).build()
        )).build()
        invalidateVerification()
        setState(BillingStatus.PURCHASING, null)
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            setState(BillingStatus.ERROR, "Google Play 결제 화면을 열지 못했어요. 다시 확인해 주세요.")
        }
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
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(PurchaseVerifier.PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS).build()
        )).build()
        client.queryProductDetailsAsync(params) { result, detailsResult ->
            catalogInFlight = false
            if (closed) return@queryProductDetailsAsync
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                productDetails = null
                offers.clear()
                mutableState.value = mutableState.value.copy(options = emptyList())
                if (!busy()) setState(BillingStatus.ERROR, "구독 상품을 불러오지 못했어요. 다시 확인해 주세요.")
                return@queryProductDetailsAsync
            }
            productDetails = detailsResult.productDetailsList.firstOrNull { it.productId == PurchaseVerifier.PRODUCT_ID }
            offers.clear()
            val details = productDetails
            val options = details?.subscriptionOfferDetails.orEmpty().mapNotNull { offer ->
                val plan = BillingPlan.entries.firstOrNull { it.basePlanId == offer.basePlanId }
                val phase = offer.pricingPhases.pricingPhaseList.singleOrNull()
                val correctTerms = when (plan) {
                    BillingPlan.MONTHLY -> phase?.billingPeriod == "P1M" && phase.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING
                    BillingPlan.THREE_MONTH_PREPAID -> phase?.billingPeriod == "P3M" && phase.recurrenceMode == ProductDetails.RecurrenceMode.NON_RECURRING
                    null -> false
                }
                // Hide unexpected promotional/term configurations instead of misdescribing a charge.
                if (plan == null || offer.offerId != null || phase == null || !correctTerms) return@mapNotNull null
                offers[plan] = offer
                BillingOption(plan, details!!.productId, offer.basePlanId, phase.formattedPrice, phase.billingPeriod,
                    if (plan == BillingPlan.MONTHLY) "AUTO_RENEWING" else "PREPAID")
            }
            val before = mutableState.value
            mutableState.value = before.copy(
                products = details?.let { listOf(BillingProduct(it.productId, it.title, it.description)) }.orEmpty(),
                options = options,
                status = if (busy()) before.status else idleStatus(options.isNotEmpty()),
                message = if (busy()) before.message else if (options.isEmpty()) "Google Play에 구독 상품이 아직 표시되지 않아요." else before.message
            )
        }
    }

    private fun queryPurchases() {
        if (queryInFlight) return
        queryInFlight = true
        val token = generation
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            queryInFlight = false
            if (closed || token != generation) return@queryPurchasesAsync
            val restoring = restoreRequested
            restoreRequested = false
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                setState(BillingStatus.ERROR, "구매 내역을 확인하지 못했어요. 인터넷 연결을 확인해 주세요.")
                return@queryPurchasesAsync
            }
            handlePurchases(purchases, restoring)
        }
    }

    private fun handlePurchases(purchases: List<Purchase>, restoring: Boolean) {
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
            // A malformed purchase list cannot cause an unbounded sequence of network calls.
            for (purchase in purchases.take(4)) {
                val result = verifier.verify(PurchaseVerifier.PRODUCT_ID, purchase.purchaseToken)
                if (closed || token != generation) return@launch
                when (result) {
                    is VerificationResult.Verified -> {
                        verified = verifier.accept(result.envelope)
                        if (verified && !purchase.isAcknowledged) acknowledge(purchase, token)
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
                entitlement -> "이전에 확인한 구독을 임시로 사용 중이에요. 연결되면 다시 확인해 주세요."
                !unavailable -> "현재 이용할 수 있는 구독이 없어요. Google Play 구독 상태를 확인해 주세요."
                else -> "구독을 확인하지 못했어요. 잠시 후 다시 확인해 주세요."
            })
        }
    }

    private fun acknowledge(purchase: Purchase, token: Long) {
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { result ->
            if (!closed && token == generation && result.responseCode != BillingClient.BillingResponseCode.OK) {
                mutableState.value = mutableState.value.copy(message = "결제 확인이 지연되고 있어요. 앱을 다시 열어 확인해 주세요.")
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (closed) return
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> if (purchases?.any { PurchaseVerifier.PRODUCT_ID in it.products } == true) handlePurchases(purchases, false) else {
                setState(idleStatus(), null); refresh()
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> setState(idleStatus(), "결제를 취소했어요.")
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> { setState(idleStatus(), null); restore() }
            else -> setState(BillingStatus.ERROR, "결제를 완료하지 못했어요. 다시 확인해 주세요.")
        }
    }

    private fun invalidateVerification() { generation++; verification?.cancel(); verification = null }
    private fun busy() = mutableState.value.status in setOf(BillingStatus.PURCHASING, BillingStatus.PENDING, BillingStatus.VERIFYING)
    private fun idleStatus(hasOptions: Boolean = mutableState.value.options.isNotEmpty()) = when {
        mutableState.value.isPremium -> BillingStatus.PREMIUM
        hasOptions -> BillingStatus.READY
        else -> BillingStatus.UNAVAILABLE
    }
    private fun recheckEntitlement() {
        expiry?.cancel()
        val wasPremium = mutableState.value.isPremium
        val validUntil = verifier.cachedExpiryMs()
        mutableState.value = mutableState.value.copy(isPremium = validUntil != null)
        if (wasPremium && validUntil == null && !busy()) setState(idleStatus(), "구독 확인 기간이 끝났어요. 인터넷에 연결해 다시 확인해 주세요.")
        if (validUntil != null) expiry = scope.launch {
            // Recheck wall-clock changes too; holding the screen open cannot keep an expired grant.
            delay((validUntil - System.currentTimeMillis()).coerceIn(1, 60_000))
            recheckEntitlement()
        }
    }
    private fun setState(status: BillingStatus, message: String?) { mutableState.value = mutableState.value.copy(status = status, message = message) }
    override fun close() {
        if (closed) return
        closed = true
        generation++
        scope.cancel()
        client.endConnection()
    }
}
