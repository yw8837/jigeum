package com.moa.babylanguage

import android.app.Activity
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
            assertEquals(2, f.play.launches)
        }
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

    private class Fixture : AutoCloseable {
        var now = 1_790_000_000_000L
        val entitlements = SignedTestEntitlements { now }
        val play = FakePlay()
        val manager = BillingManager(entitlements, { listener -> play.apply { this.listener = listener } }, { now })
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
        override fun acknowledge(purchaseToken: String, callback: (BillingResult) -> Unit) { acknowledged += purchaseToken; callback(result()) }
        override fun endConnection() { isReady = false }
        fun finishQuery(purchases: List<Purchase>, code: Int = BillingClient.BillingResponseCode.OK) { queries.removeFirst()(result(code), purchases) }
        fun emit(purchases: List<Purchase>) = listener.onPurchasesUpdated(result(), purchases.toMutableList())
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
