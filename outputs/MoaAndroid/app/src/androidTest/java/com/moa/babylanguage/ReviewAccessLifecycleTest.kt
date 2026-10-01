package com.moa.babylanguage

import android.app.Activity
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@RunWith(AndroidJUnit4::class)
class ReviewAccessLifecycleTest {
    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private val code = "synthetic-review-code-for-tests-only-0123456789"
    private val packageName = "com.moa.babylanguage"

    @Test fun codeAloneCannotUnlockAndEmptyPlayOwnershipDoesNotRevokeReviewGrant() = onMain {
        Fixture().use { f ->
            f.manager.activateReviewAccess(code)
            assertEquals(ReviewAccessStatus.VERIFYING, f.manager.state.value.reviewStatus)
            assertFalse(f.manager.hasCurrentEntitlement())
            f.review.finish(VerificationResult.Verified(f.envelope()))
            assertTrue(f.manager.hasCurrentEntitlement())
            assertEquals(EntitlementSource.PLAY_REVIEW, f.manager.state.value.entitlementSource)
            f.play.finishEmptyQuery()
            assertTrue(f.manager.hasCurrentEntitlement())
            assertEquals(0, f.review.clears)
            f.manager.restore()
            f.play.finishEmptyQuery()
            assertTrue(f.manager.hasCurrentEntitlement())
        }
    }

    @Test fun invalidSignatureWrongPurposeAndWrongCodeFailClosed() = onMain {
        Fixture().use { f ->
            f.manager.activateReviewAccess("too-short")
            assertEquals(0, f.review.calls)
            assertFalse(f.manager.hasCurrentEntitlement())
            f.manager.activateReviewAccess(code)
            f.review.finish(VerificationResult.Rejected)
            assertFalse(f.manager.hasCurrentEntitlement())
            f.manager.activateReviewAccess(code)
            f.review.finish(VerificationResult.Verified(f.envelope(review = false)))
            assertFalse(f.manager.hasCurrentEntitlement())
            assertEquals(0, f.review.accepted)
            f.manager.activateReviewAccess(code)
            val bad = JSONObject(f.envelope()).put("signature", Base64.encodeToString(ByteArray(70), Base64.NO_WRAP)).toString()
            f.review.finish(VerificationResult.Verified(bad))
            assertFalse(f.manager.hasCurrentEntitlement())
            assertEquals(0, f.review.accepted)
        }
    }

    @Test fun expiryStopsNewProtectedActionsAndOnlineRefreshRecovers() = onMain {
        Fixture().use { f ->
            f.unlock()
            val access = AccessPolicy(f.manager::hasCurrentEntitlement)
            val camera = checkNotNull(access.beginMedia(AppFeature.VISUAL_ANALYSIS))
            val video = checkNotNull(access.beginMedia(AppFeature.VISUAL_ANALYSIS))
            assertFalse(access.isCurrent(camera)) // New media generation invalidates the first session.
            assertTrue(access.isCurrent(video))
            f.now += 3_600_000
            assertFalse(f.manager.hasCurrentEntitlement())
            assertFalse(access.isCurrent(video))
            assertFalse(access.canUse(AppFeature.PDF))
            for (feature in AppFeature.entries.filter { !it.requiresPass }) assertTrue(access.canUse(feature))
            f.manager.refresh()
            f.review.finish(VerificationResult.Verified(f.envelope()))
            assertTrue(access.canUse(AppFeature.PDF))
            assertNotNull(access.beginMedia(AppFeature.VISUAL_ANALYSIS))
        }
    }

    @Test fun outagePreservesOnlyExistingSignedTimeAndRevocationRemovesReviewAccess() = onMain {
        Fixture().use { f ->
            f.unlock()
            f.now += 61_000
            f.manager.refresh()
            f.review.finish(VerificationResult.Unavailable)
            assertTrue(f.manager.hasCurrentEntitlement())
            assertEquals(ReviewAccessStatus.ACTIVE, f.manager.state.value.reviewStatus)
            f.now += 61_000
            f.manager.refresh()
            f.review.finish(VerificationResult.Rejected)
            assertFalse(f.manager.hasCurrentEntitlement())
            assertEquals(EntitlementSource.NONE, f.manager.state.value.entitlementSource)
            assertEquals(1, f.review.clears)
        }
    }

    @Test fun unconfiguredServiceNeverCallsReviewerOrCreatesPlayClient() = onMain {
        Fixture(configured = false).use { f ->
            f.manager.activateReviewAccess(code)
            repeat(5) { f.manager.refresh(); f.manager.restore() }
            assertFalse(f.manager.hasCurrentEntitlement())
            assertEquals(ReviewAccessStatus.UNAVAILABLE, f.manager.state.value.reviewStatus)
            assertEquals(0, f.review.calls)
            assertEquals(0, f.gatewayCreations)
        }
    }

    @Test fun deactivateAndCloseRejectLateNetworkReplies() = onMain {
        Fixture().use { f ->
            f.manager.activateReviewAccess(code)
            f.manager.deactivateReviewAccess()
            f.review.finish(VerificationResult.Verified(f.envelope()))
            assertFalse(f.manager.hasCurrentEntitlement())
            assertEquals(0, f.review.accepted)
            f.manager.activateReviewAccess(code)
            f.manager.close()
            f.review.finish(VerificationResult.Verified(f.envelope()))
            assertFalse(f.manager.hasCurrentEntitlement())
            assertEquals(0, f.review.accepted)
        }
    }

    @Test fun reviewRemovalDoesNotRevokeRealPurchaseGrant() = onMain {
        Fixture().use { f ->
            f.unlock()
            f.purchase.grant = f.envelope(review = false)
            assertTrue(f.manager.hasCurrentEntitlement())
            assertEquals(EntitlementSource.PLAY_PURCHASE, f.manager.state.value.entitlementSource)
            f.manager.deactivateReviewAccess()
            assertTrue(f.manager.hasCurrentEntitlement())
            assertEquals(EntitlementSource.PLAY_PURCHASE, f.manager.state.value.entitlementSource)
            assertEquals(0, f.purchase.clears)
        }
    }

    @Test fun restartAndRebootRequireRealCurrentReviewGrantThenRecoverOnline() = onMain {
        Fixture().use { f ->
            f.unlock()
            f.manager.close()
            f.newManager()
            assertTrue(f.manager.hasCurrentEntitlement())
            // Restart automatically requests a fresh grant without requiring the code again.
            assertEquals(2, f.review.calls)
            f.review.finish(VerificationResult.Verified(f.envelope()))
            f.manager.close()
            f.review.boot++
            f.newManager()
            assertFalse(f.manager.hasCurrentEntitlement())
            f.review.finish(VerificationResult.Verified(f.envelope()))
            assertTrue(f.manager.hasCurrentEntitlement())
        }
    }

    @Test fun refreshCallsAreCollapsedAndFailuresCannotRetryWithoutBound() = onMain {
        Fixture().use { f ->
            f.unlock()
            f.now += 61_000
            repeat(20) { f.manager.refresh() }
            assertEquals(2, f.review.calls)
            f.review.finish(VerificationResult.Unavailable)
            repeat(20) { f.manager.refresh() }
            assertEquals(2, f.review.calls)
            f.now += 60_000
            f.manager.refresh()
            assertEquals(3, f.review.calls)
        }
    }

    private inner class Fixture(private val configured: Boolean = true) : AutoCloseable {
        var now = 1_790_000_000_000L
        val installation = UUID.randomUUID().toString()
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val play = EmptyPlay()
        var gatewayCreations = 0
        val purchase = SignedPurchase(this, configured)
        val review = SignedReview(this, configured)
        lateinit var manager: BillingManager
        init { newManager() }
        fun newManager() {
            manager = BillingManager(purchase, { gatewayCreations++; play }, clock = { now }, recoveryClock = { now }, reviewer = review)
        }
        fun envelope(review: Boolean = true): String {
            val claims = JSONObject().put("version", 1).put("grantSource", if (review) "play-review" else "play-purchase")
                .put("basePlanId", if (review) "play-review" else "monthly").put("reviewAccessId", "play-review-v1")
                .put("productId", "jigeum_album").put("packageName", packageName).put("installationId", installation)
                .put("issuedAtMs", now).put("expiresAtMs", now + 3_600_000).put("subscriptionExpiryMs", now + 86_400_000)
                .toString().toByteArray(Charsets.UTF_8)
            val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(keys.private); update(claims) }.sign()
            fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            return JSONObject().put("payload", encode(claims)).put("signature", encode(signature)).toString()
        }
        fun unlock() { manager.activateReviewAccess(code); review.finish(VerificationResult.Verified(envelope())) }
        override fun close() { manager.close() }
    }

    private inner class SignedPurchase(private val f: Fixture, override val isConfigured: Boolean) : BillingEntitlements {
        var grant: String? = null
        var clears = 0
        override fun cachedEntitlement() = cachedExpiryMs() != null
        override fun cachedExpiryMs(): Long? = grant?.takeIf {
            verifySignedEntitlement(it, f.keys.public, f.installation, packageName, f.now)
        }?.let { JSONObject(String(Base64.decode(JSONObject(it).getString("payload"), Base64.URL_SAFE), Charsets.UTF_8)).getLong("expiresAtMs") }
        override fun clear() { grant = null; clears++ }
        override fun accept(envelope: String): Boolean = false
        override suspend fun verify(productId: String, purchaseToken: String) = VerificationResult.Unavailable
    }

    private inner class SignedReview(private val f: Fixture, override val isConfigured: Boolean) : ReviewEntitlements {
        var grant: String? = null
        var codeValue: String? = null
        var boot = 7
        var anchor: EntitlementClockAnchor? = null
        var calls = 0
        var accepted = 0
        var clears = 0
        private var reply: Continuation<VerificationResult>? = null
        override fun storedCode() = codeValue
        override fun cachedExpiryMs(): Long? {
            val current = grant ?: return null
            if (!verifySignedReviewEntitlement(current, f.keys.public, f.installation, packageName, f.now)) return null
            val claims = JSONObject(String(Base64.decode(JSONObject(current).getString("payload"), Base64.URL_SAFE), Charsets.UTF_8))
            val remaining = remainingEntitlementMs(claims.getLong("issuedAtMs"), claims.getLong("expiresAtMs"),
                claims.getLong("subscriptionExpiryMs"), anchor, f.now, f.now, boot) ?: return null
            return f.now + remaining
        }
        override fun clear() { grant = null; codeValue = null; anchor = null; clears++ }
        override fun accept(envelope: String, code: String): Boolean {
            if (!verifySignedReviewEntitlement(envelope, f.keys.public, f.installation, packageName, f.now)) return false
            grant = envelope; codeValue = code
            anchor = EntitlementClockAnchor(ENTITLEMENT_CACHE_VERSION, f.now, f.now, boot)
            accepted++
            return true
        }
        override suspend fun verify(code: String): VerificationResult = suspendCoroutine { calls++; reply = it }
        fun finish(result: VerificationResult) { val pending = checkNotNull(reply); reply = null; pending.resume(result) }
    }

    private class EmptyPlay : BillingGateway {
        override val isReady = true
        private var callback: ((BillingResult, List<Purchase>) -> Unit)? = null
        override fun startConnection(listener: BillingClientStateListener) = Unit
        override fun queryCatalog(callback: (BillingResult, BillingCatalog) -> Unit) = callback(ok(), BillingCatalog(emptyList(), emptyList()))
        override fun queryPurchases(callback: (BillingResult, List<Purchase>) -> Unit) { this.callback = callback }
        fun finishEmptyQuery() { val pending = checkNotNull(callback); callback = null; pending(ok(), emptyList()) }
        override fun purchase(activity: Activity, plan: BillingPlan): BillingResult = ok()
        override fun acknowledge(purchaseToken: String, callback: (BillingResult) -> Unit) = callback(ok())
        override fun endConnection() = Unit
        private fun ok() = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build()
    }
}
