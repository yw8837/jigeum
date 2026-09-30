package com.moa.babylanguage

import android.app.Activity
import android.os.SystemClock
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Real manager/coroutines + controlled Play replies. All test keys and fakes stay in androidTest. */
@RunWith(AndroidJUnit4::class)
class BillingManagerLifecycleTest {
    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync { block() }

    @Test fun pendingPurchaseDoesNotUnlockAndVerifiedCompletionUnlocksOnce() = onMain {
        Fixture().use { f ->
            f.play.finishQuery(listOf(purchase("pending-token", pending = true)))
            assertEquals(BillingStatus.PENDING, f.manager.state.value.status)
            assertFalse(f.manager.hasCurrentEntitlement())
            assertEquals(0, f.entitlements.verifyCalls)
            f.play.emit(listOf(purchase("completed-token")))
            assertEquals(BillingStatus.VERIFYING, f.manager.state.value.status)
            assertFalse(f.manager.hasCurrentEntitlement())
            f.entitlements.finish("completed-token", f.valid())
            assertTrue(f.manager.hasCurrentEntitlement())
            assertEquals(BillingStatus.PREMIUM, f.manager.state.value.status)
            assertEquals(listOf("completed-token"), f.play.acknowledged)
        }
    }

    @Test fun currentActionGateRejectsExpiredGrantBeforeDisplayTimerTicks() = onMain {
        Fixture().use { f ->
            f.unlock()
            assertTrue(f.manager.state.value.isPremium)
            f.now += 3_600_000
            // The clock advances without waiting for a UI coroutine timer.
            assertTrue(f.manager.state.value.isPremium)
            assertFalse(f.manager.hasCurrentEntitlement())
            assertFalse(f.manager.state.value.isPremium)
            assertEquals(1, f.entitlements.accepted)
        }
    }

    @Test fun outagePreservesOnlyRemainingSignedTimeAndExplicitRejectionRevokes() = onMain {
        Fixture().use { f ->
            f.unlock()
            f.manager.restore()
            f.play.finishQuery(listOf(purchase("offline-token", acknowledged = true)))
            f.entitlements.finish("offline-token", VerificationResult.Unavailable)
            assertTrue(f.manager.hasCurrentEntitlement())
            assertTrue(f.manager.state.value.message!!.contains("임시"))
            f.manager.restore()
            f.play.finishQuery(listOf(purchase("revoked-token", acknowledged = true)))
            f.entitlements.finish("revoked-token", VerificationResult.Rejected)
            assertFalse(f.manager.hasCurrentEntitlement())
            assertFalse(f.manager.state.value.isPremium)
        }
    }

    @Test fun duplicateRestoreRefreshQueriesAreCollapsedAndEmptyRestoreRevokes() = onMain {
        Fixture().use { f ->
            f.unlock()
            repeat(10) { f.manager.restore(); f.manager.refresh() }
            assertEquals(2, f.play.queryCalls)
            f.play.finishQuery(emptyList())
            assertFalse(f.manager.hasCurrentEntitlement())
            assertEquals("복원할 활성 구독이 없어요.", f.manager.state.value.message)
        }
    }

    @Test fun staleEmptyQueryCannotRevokeNewerPurchaseEvent() = onMain {
        Fixture().use { f ->
            // The initialization query is still outstanding when Play delivers a purchase.
            f.play.emit(listOf(purchase("newer-token")))
            f.entitlements.finish("newer-token", f.valid())
            assertTrue(f.manager.hasCurrentEntitlement())
            f.play.finishQuery(emptyList())
            assertTrue(f.manager.hasCurrentEntitlement())
            assertEquals(BillingStatus.PREMIUM, f.manager.state.value.status)
        }
    }

    @Test fun lateVerificationCannotUnlockAfterPendingReplacementOrClose() = onMain {
        Fixture().use { f ->
            f.play.finishQuery(listOf(purchase("old-token")))
            f.play.emit(listOf(purchase("waiting-token", pending = true)))
            // Non-cancellable fake transport deliberately returns after cancellation.
            f.entitlements.finish("old-token", f.valid())
            assertFalse(f.manager.hasCurrentEntitlement())
            assertEquals(0, f.entitlements.accepted)
            assertEquals(BillingStatus.PENDING, f.manager.state.value.status)
            f.play.emit(listOf(purchase("closed-token")))
            f.manager.close()
            f.entitlements.finish("closed-token", f.valid())
            assertFalse(f.manager.hasCurrentEntitlement())
            assertEquals(0, f.entitlements.accepted)
            assertTrue(f.play.acknowledged.isEmpty())
        }
    }

    @Test fun purchaseDoubleTapAndCancellationDoNotLeaveStuckPurchaseState() = onMain {
        Fixture().use { f ->
            f.play.finishQuery(emptyList())
            val activity = Activity()
            f.manager.purchase(activity, BillingPlan.MONTHLY)
            f.manager.purchase(activity, BillingPlan.MONTHLY)
            assertEquals(1, f.play.launches)
            assertEquals(BillingStatus.PURCHASING, f.manager.state.value.status)
            f.manager.refresh()
            assertEquals(BillingStatus.PURCHASING, f.manager.state.value.status)
            f.play.listener.onPurchasesUpdated(result(BillingClient.BillingResponseCode.USER_CANCELED), null)
            assertEquals(BillingStatus.READY, f.manager.state.value.status)
            assertFalse(f.manager.hasCurrentEntitlement())
            f.manager.purchase(activity, BillingPlan.MONTHLY)
            assertEquals(1, f.play.launches)
            f.play.finishQuery(emptyList())
            f.manager.purchase(activity, BillingPlan.MONTHLY)
            assertEquals(2, f.play.launches)
        }
    }

    @Test fun foregroundRecoveryWaitsForGraceThenRequiresCurrentEmptyQueryBeforeRetry() = onMain {
        Fixture().use { f ->
            f.play.finishQuery(emptyList())
            val activity = Activity()
            f.manager.purchase(activity, BillingPlan.MONTHLY)
            repeat(5) { f.manager.refresh(); f.manager.restore() }
            assertEquals(1, f.play.queryCalls)
            assertEquals(BillingStatus.PURCHASING, f.manager.state.value.status)
            f.elapsed += 120_000
            repeat(5) { f.manager.refresh(); f.manager.restore() }
            assertEquals(2, f.play.queryCalls)
            f.manager.purchase(activity, BillingPlan.MONTHLY)
            assertEquals(1, f.play.launches)
            f.play.finishQuery(emptyList())
            assertEquals(BillingStatus.READY, f.manager.state.value.status)
            f.manager.purchase(activity, BillingPlan.MONTHLY)
            assertEquals(2, f.play.launches)
            f.elapsed += 120_000
            f.manager.refresh()
            f.play.finishQuery(emptyList(), BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
            assertEquals(BillingStatus.ERROR, f.manager.state.value.status)
            f.manager.purchase(activity, BillingPlan.MONTHLY)
            assertEquals(2, f.play.launches)
            f.manager.refresh()
            f.play.finishQuery(emptyList())
            f.manager.purchase(activity, BillingPlan.MONTHLY)
            assertEquals(3, f.play.launches)
        }
    }

    @Test fun latePurchaseSupersedesRecoveryAndStaleReplyCannotClearNewQuery() = onMain {
        Fixture().use { f ->
            f.play.finishQuery(emptyList())
            f.manager.purchase(Activity(), BillingPlan.MONTHLY)
            f.elapsed += 120_000
            f.manager.refresh()
            f.play.emit(listOf(purchase("late-purchase")))
            f.entitlements.finish("late-purchase", f.valid())
            assertTrue(f.manager.hasCurrentEntitlement())
            f.manager.restore()
            assertEquals(3, f.play.queryCalls)
            f.play.finishQuery(emptyList()) // Older recovery reply.
            f.manager.restore()
            assertEquals(3, f.play.queryCalls) // Current query remains outstanding.
            f.play.finishQuery(listOf(purchase("late-purchase", acknowledged = true)))
            f.entitlements.finish("late-purchase", f.valid())
            f.play.listener.onPurchasesUpdated(result(BillingClient.BillingResponseCode.USER_CANCELED), null)
            assertEquals(BillingStatus.PREMIUM, f.manager.state.value.status)
            assertTrue(f.manager.hasCurrentEntitlement())
        }
    }

    @Test fun missingCallbackTimerRequeriesAndRecoveredPurchaseStillRequiresVerification() {
        lateinit var f: Fixture
        onMain {
            f = Fixture(realRecoveryClock = true, callbackTimeoutMs = 60, queryTimeoutMs = 1_000)
            f.play.finishQuery(emptyList())
            f.manager.purchase(Activity(), BillingPlan.MONTHLY)
        }
        try {
            awaitMain { f.play.queryCalls == 2 }
            onMain {
                assertEquals(BillingStatus.PURCHASING, f.manager.state.value.status)
                f.play.finishQuery(listOf(purchase("timer-recovered")))
                assertFalse(f.manager.hasCurrentEntitlement())
                f.entitlements.finish("timer-recovered", f.valid())
                assertTrue(f.manager.hasCurrentEntitlement())
                assertEquals(listOf("timer-recovered"), f.play.acknowledged)
            }
        } finally { onMain { f.close() } }
    }

    @Test fun lostRecoveryQueryTimesOutAndItsLateReplyCannotAuthorizeNextLaunch() {
        lateinit var f: Fixture
        onMain {
            f = Fixture(realRecoveryClock = true, callbackTimeoutMs = 60, queryTimeoutMs = 300)
            f.play.finishQuery(emptyList())
            f.manager.purchase(Activity(), BillingPlan.MONTHLY)
        }
        try {
            awaitMain { f.manager.state.value.status == BillingStatus.ERROR && f.play.queryCalls == 2 }
            onMain {
                f.manager.refresh()
                assertEquals(3, f.play.queryCalls)
                f.play.finishQuery(emptyList()) // Timed-out response must be ignored.
                f.manager.purchase(Activity(), BillingPlan.MONTHLY)
                assertEquals(1, f.play.launches)
                f.play.finishQuery(emptyList())
                f.manager.purchase(Activity(), BillingPlan.MONTHLY)
                assertEquals(2, f.play.launches)
            }
        } finally { onMain { f.close() } }
    }

    @Test fun acknowledgementFailureIsVisibleAndRequeryRetriesAfterRecreation() = onMain {
        Fixture().use { f ->
            f.play.acknowledgeCode = BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE
            f.unlock()
            assertTrue(f.manager.hasCurrentEntitlement())
            assertTrue(f.manager.state.value.message!!.contains("결제 확인이 지연"))
            f.play.acknowledgeCode = BillingClient.BillingResponseCode.OK
            f.manager.restore()
            f.play.finishQuery(listOf(purchase("requeried-token")))
            f.entitlements.finish("requeried-token", f.valid())
            assertEquals(listOf("valid-token", "requeried-token"), f.play.acknowledged)
            assertEquals("구독을 복원했어요.", f.manager.state.value.message)
            f.manager.close()
            val nextPlay = FakePlay()
            val next = BillingManager(f.entitlements, { listener -> nextPlay.apply { this.listener = listener } }, { f.now })
            try {
                nextPlay.finishQuery(listOf(purchase("valid-token")))
                f.entitlements.finish("valid-token", f.valid())
                assertTrue(next.hasCurrentEntitlement())
                assertEquals(listOf("valid-token"), nextPlay.acknowledged)
                assertEquals(null, next.state.value.message)
            } finally { next.close() }
        }
    }

    @Test fun lostAcknowledgementResponseRetriesAndLateFirstReplyIsIgnored() {
        lateinit var f: Fixture
        onMain {
            f = Fixture(queryTimeoutMs = 250, acknowledgeRetryDelayMs = 20)
            f.play.holdAcknowledgements = true
            f.unlock()
        }
        try {
            awaitMain { f.play.acknowledged.size == 2 }
            onMain {
                assertTrue(f.manager.state.value.message!!.contains("결제 확인이 지연"))
                f.play.finishAcknowledgement(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
                f.play.finishAcknowledgement(BillingClient.BillingResponseCode.OK)
                assertTrue(f.manager.hasCurrentEntitlement())
                assertEquals(null, f.manager.state.value.message)
                assertEquals(2, f.play.acknowledged.size)
            }
        } finally { onMain { f.close() } }
    }

    @Test fun closeCancelsPurchaseDeadlineAndIgnoresLostAcknowledgementCallback() {
        lateinit var f: Fixture
        lateinit var acknowledging: Fixture
        onMain {
            f = Fixture(realRecoveryClock = true, callbackTimeoutMs = 60)
            f.play.finishQuery(emptyList())
            f.manager.purchase(Activity(), BillingPlan.MONTHLY)
            f.close()
            acknowledging = Fixture(queryTimeoutMs = 40, acknowledgeRetryDelayMs = 20)
            acknowledging.play.holdAcknowledgements = true
            acknowledging.unlock()
            acknowledging.close()
            acknowledging.play.finishAcknowledgement(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
        }
        Thread.sleep(150)
        onMain {
            assertEquals(1, f.play.queryCalls)
            assertEquals(1, acknowledging.play.acknowledged.size)
            assertEquals(null, acknowledging.manager.state.value.message)
            assertFalse(acknowledging.manager.hasCurrentEntitlement())
        }
    }

    private fun awaitMain(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 3_000
        do {
            var reached = false
            onMain { reached = condition() }
            if (reached) return
            Thread.sleep(10)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Billing recovery deadline did not complete")
    }

    @Test fun queryErrorDoesNotCreateEntitlementAndPreviousGrantExpiresOffline() = onMain {
        Fixture().use { f ->
            f.play.finishQuery(emptyList(), BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
            assertFalse(f.manager.hasCurrentEntitlement())
            f.manager.restore()
            f.play.finishQuery(listOf(purchase("restored-token")))
            f.entitlements.finish("restored-token", f.valid())
            assertTrue(f.manager.hasCurrentEntitlement())
            f.manager.restore()
            f.play.finishQuery(emptyList(), BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
            assertTrue(f.manager.hasCurrentEntitlement())
            f.now += 3_600_000
            assertFalse(f.manager.hasCurrentEntitlement())
        }
    }

    @Test fun restoreAfterRecreationRechecksSignedCacheAndInvalidSignatureCannotUnlock() = onMain {
        Fixture().use { f ->
            f.unlock()
            f.manager.close()
            val nextPlay = FakePlay()
            val next = BillingManager(f.entitlements, { listener -> nextPlay.apply { this.listener = listener } }, { f.now })
            try {
                assertTrue(next.hasCurrentEntitlement())
                nextPlay.finishQuery(listOf(purchase("tampered-token")))
                f.entitlements.finish("tampered-token", VerificationResult.Verified("{}"))
                // A bad new signature does not replace a previously valid, unexpired grant.
                assertTrue(next.hasCurrentEntitlement())
                assertEquals(1, f.entitlements.accepted)
                f.now += 3_600_000
                assertFalse(next.hasCurrentEntitlement())
            } finally { next.close() }
        }
        Fixture().use { f ->
            f.play.finishQuery(listOf(purchase("invalid-token")))
            f.entitlements.finish("invalid-token", VerificationResult.Verified("{}"))
            assertFalse(f.manager.hasCurrentEntitlement())
            assertEquals(0, f.entitlements.accepted)
        }
    }

    private class Fixture(
        realRecoveryClock: Boolean = false,
        callbackTimeoutMs: Long = 120_000,
        queryTimeoutMs: Long = 30_000,
        acknowledgeRetryDelayMs: Long = 5_000
    ) : AutoCloseable {
        var now = 1_790_000_000_000L
        var elapsed = 10_000L
        val entitlements = SignedTestEntitlements { now }
        val play = FakePlay()
        val manager = BillingManager(entitlements, { listener -> play.apply { this.listener = listener } }, { now },
            { if (realRecoveryClock) SystemClock.elapsedRealtime() else elapsed }, callbackTimeoutMs, queryTimeoutMs, acknowledgeRetryDelayMs)
        fun valid() = VerificationResult.Verified(entitlements.envelope(now + 3_600_000))
        fun unlock() {
            play.finishQuery(listOf(purchase("valid-token")))
            entitlements.finish("valid-token", valid())
            assertTrue(manager.hasCurrentEntitlement())
        }
        override fun close() { manager.close() }
    }

    private class FakePlay : BillingGateway {
        lateinit var listener: PurchasesUpdatedListener
        override var isReady = true
        var queryCalls = 0
        var launches = 0
        var acknowledgeCode = BillingClient.BillingResponseCode.OK
        var holdAcknowledgements = false
        private val acknowledgements = ArrayDeque<(BillingResult) -> Unit>()
        val acknowledged = mutableListOf<String>()
        private val queries = ArrayDeque<(BillingResult, List<Purchase>) -> Unit>()
        override fun startConnection(listener: BillingClientStateListener) { isReady = true; listener.onBillingSetupFinished(result()) }
        override fun queryCatalog(callback: (BillingResult, BillingCatalog) -> Unit) {
            callback(result(), BillingCatalog(listOf(BillingProduct("jigeum_album", "TEST 전체이용권", "TEST")), listOf(
                BillingOption(BillingPlan.MONTHLY, "jigeum_album", "monthly", "TEST ₩4,900", "P1M", "AUTO_RENEWING")
            )))
        }
        override fun queryPurchases(callback: (BillingResult, List<Purchase>) -> Unit) { queryCalls++; queries.addLast(callback) }
        override fun purchase(activity: Activity, plan: BillingPlan): BillingResult { launches++; return result() }
        override fun acknowledge(purchaseToken: String, callback: (BillingResult) -> Unit) {
            acknowledged += purchaseToken
            if (holdAcknowledgements) acknowledgements.addLast(callback) else callback(result(acknowledgeCode))
        }
        override fun endConnection() { isReady = false }
        fun finishQuery(purchases: List<Purchase>, code: Int = BillingClient.BillingResponseCode.OK) { queries.removeFirst()(result(code), purchases) }
        fun emit(purchases: List<Purchase>) = listener.onPurchasesUpdated(result(), purchases.toMutableList())
        fun finishAcknowledgement(code: Int) { acknowledgements.removeFirst()(result(code)) }
    }

    private class SignedTestEntitlements(private val clock: () -> Long) : BillingEntitlements {
        override val isConfigured = true
        var accepted = 0
        var verifyCalls = 0
        private val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        private val installation = "94be8be6-04c9-4b85-9994-69113bf442d0"
        private var cached: String? = null
        private val pending = mutableMapOf<String, Continuation<VerificationResult>>()
        override fun cachedEntitlement() = cached?.let { valid(it) } == true
        override fun cachedExpiryMs(): Long? {
            if (!cachedEntitlement()) return null
            val payload = Base64.decode(JSONObject(cached!!).getString("payload"), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            return JSONObject(String(payload, Charsets.UTF_8)).getLong("expiresAtMs")
        }
        override fun clear() { cached = null }
        override fun accept(envelope: String): Boolean {
            if (!valid(envelope)) return false
            accepted++; cached = envelope; return true
        }
        override suspend fun verify(productId: String, purchaseToken: String): VerificationResult = suspendCoroutine {
            assertEquals("jigeum_album", productId)
            verifyCalls++; pending[purchaseToken] = it
        }
        fun finish(token: String, value: VerificationResult) { pending.remove(token)!!.resume(value) }
        private fun valid(value: String) = verifySignedEntitlement(value, keys.public, installation, "com.moa.babylanguage", clock())
        fun envelope(expiry: Long): String {
            val bytes = JSONObject().put("version", 1).put("packageName", "com.moa.babylanguage")
                .put("productId", "jigeum_album").put("installationId", installation).put("basePlanId", "monthly")
                .put("issuedAtMs", clock()).put("expiresAtMs", expiry).put("subscriptionExpiryMs", expiry)
                .toString().toByteArray(Charsets.UTF_8)
            val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(keys.private); update(bytes) }.sign()
            fun encode(value: ByteArray) = Base64.encodeToString(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            return JSONObject().put("payload", encode(bytes)).put("signature", encode(signature)).toString()
        }
    }

    companion object {
        private fun result(code: Int = BillingClient.BillingResponseCode.OK) = BillingResult.newBuilder().setResponseCode(code).build()
        private fun purchase(token: String, pending: Boolean = false, acknowledged: Boolean = false) = Purchase(JSONObject()
            .put("productIds", JSONArray().put("jigeum_album")).put("purchaseToken", token)
            .put("purchaseState", if (pending) 4 else 0).put("acknowledged", acknowledged).toString(), "TEST_ONLY")
    }
}
