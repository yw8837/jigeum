package com.moa.babylanguage

import android.app.Activity
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.billingclient.api.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@RunWith(AndroidJUnit4::class)
class AccessCodeTest {
    private val code = "synthetic-access-code-never-issued-0123456789"
    private val app = "com.moa.babylanguage"
    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    @Test fun signaturePurposeCodeDeviceAndTermsAreBound() {
        val f = SignedFixture()
        for (kind in listOf("invitation", "launch-offer")) {
            val valid = f.envelope(kind)
            assertNotNull(f.claims(valid))
            assertFalse(verifySignedEntitlement(valid, f.keys.public, f.install, app, f.now))
            assertFalse(verifySignedReviewEntitlement(valid, f.keys.public, f.install, app, f.now))
            assertNull(verifiedAccessCodeClaims(valid, f.keys.public, f.install, app, code + "x", f.now))
            assertNull(verifiedAccessCodeClaims(valid, f.keys.public, UUID.randomUUID().toString(), app, code, f.now))
            assertNull(verifiedAccessCodeClaims(valid, f.keys.public, f.install, "other.app", code, f.now))
            assertNull(f.claims(JSONObject(valid).put("signature", "AA").toString()))
            assertNull(f.claims(f.envelope(kind) { it.put("grantSource", "play-purchase") }))
            assertNull(f.claims(f.envelope(kind) { it.put("expiresAtMs", f.now + 86_400_001) }))
            assertNull(f.claims(f.envelope(kind) { it.put("issuedAtMs", f.now + 300_001) }))
            assertNull(f.claims(f.envelope(kind) { it.put("subscriptionExpiryMs", f.now + 1) }))
            assertNull(f.claims(f.envelope(kind) { it.put("productId", "another") }))
        }
        for ((key, value) in listOf("introductoryCycles" to 1, "introductoryPriceMicros" to 4900000000L,
            "renewalPriceMicros" to 5900000000L, "currency" to "USD", "offerId" to "other", "billingPeriod" to "P2M")) {
            assertNull(f.claims(f.envelope("launch-offer") { it.put(key, value) }))
        }
        val valid = f.envelope()
        f.now += 86_400_000
        assertNull(f.claims(valid))
        for (bad in listOf("", "null", "[]", "x".repeat(16385))) assertNull(f.claims(bad))
    }

    @Test fun discountNeverUnlocksWhileInvitationSurvivesEmptyOwnershipAndRestore() = onMain {
        ManagerFixture().use { f ->
            f.manager.activateAccessCode(code)
            assertFalse(f.manager.hasCurrentEntitlement())
            f.codes.finish(VerificationResult.Verified(f.signed.envelope("launch-offer")))
            assertFalse(f.manager.hasCurrentEntitlement())
            assertEquals(AccessCodeKind.LAUNCH_OFFER, f.manager.state.value.codeKind)
            assertFalse(AccessPolicy(f.manager::hasCurrentEntitlement).canUse(AppFeature.PDF))
            f.manager.deactivateAccessCode()
            f.activate()
            assertTrue(f.manager.hasCurrentEntitlement())
            assertEquals(EntitlementSource.INVITATION, f.manager.state.value.entitlementSource)
            f.manager.restore()
            assertTrue(f.manager.hasCurrentEntitlement())
        }
    }

    @Test fun expiryOfflineRevocationAndRebootFailClosedThenOnlineRecovers() = onMain {
        ManagerFixture().use { f ->
            f.activate()
            f.signed.now += 61_000
            f.manager.refresh(); f.codes.finish(VerificationResult.Unavailable)
            assertTrue(f.manager.hasCurrentEntitlement())
            f.signed.now += 86_400_000
            assertFalse(f.manager.hasCurrentEntitlement())
            f.manager.refresh(); f.codes.finish(VerificationResult.Verified(f.signed.envelope()))
            assertTrue(f.manager.hasCurrentEntitlement())
            f.manager.close(); f.codes.boot++; f.newManager()
            assertFalse(f.manager.hasCurrentEntitlement())
            f.codes.finish(VerificationResult.Verified(f.signed.envelope()))
            assertTrue(f.manager.hasCurrentEntitlement())
            f.signed.now += 61_000
            f.manager.refresh(); f.codes.finish(VerificationResult.Rejected)
            assertFalse(f.manager.hasCurrentEntitlement())
            assertEquals(AccessCodeKind.NONE, f.manager.state.value.codeKind)
        }
    }

    @Test fun failedReplacementRetainsPreviousAndRemovalPreservesPurchase() = onMain {
        ManagerFixture().use { f ->
            f.activate()
            f.manager.activateAccessCode(code + "x")
            f.codes.finish(VerificationResult.Rejected)
            assertTrue(f.manager.hasCurrentEntitlement())
            f.purchase.until = f.signed.now + 3600000
            f.manager.deactivateAccessCode()
            assertTrue(f.manager.hasCurrentEntitlement())
            assertEquals(EntitlementSource.PLAY_PURCHASE, f.manager.state.value.entitlementSource)
        }
    }

    @Test fun staleAndClosedCallbacksCannotRegrantAndRefreshIsBounded() = onMain {
        ManagerFixture().use { f ->
            f.manager.activateAccessCode(code)
            f.manager.deactivateAccessCode()
            f.codes.finish(VerificationResult.Verified(f.signed.envelope()))
            assertFalse(f.manager.hasCurrentEntitlement())
            f.activate()
            f.signed.now += 61000
            val before = f.codes.calls
            repeat(20) { f.manager.refresh() }
            assertEquals(before + 1, f.codes.calls)
            f.codes.finish(VerificationResult.Unavailable)
            repeat(20) { f.manager.refresh() }
            assertEquals(before + 1, f.codes.calls)
            f.manager.deactivateAccessCode(); f.manager.activateAccessCode(code); f.manager.close()
            f.codes.finish(VerificationResult.Verified(f.signed.envelope()))
            assertFalse(f.manager.hasCurrentEntitlement())
        }
    }

    @Test fun launchRequiresFreshCodeAndNeverFallsBackToRegularPrice() = onMain {
        ManagerFixture().use { f ->
            f.manager.purchase(Activity(), BillingPlan.LAUNCH_MONTHLY)
            assertEquals(0, f.play.launches)
            f.manager.refresh()
            f.manager.activateAccessCode(code)
            f.codes.finish(VerificationResult.Verified(f.signed.envelope("launch-offer")))
            f.manager.purchase(Activity(), BillingPlan.LAUNCH_MONTHLY)
            assertEquals(listOf(BillingPlan.LAUNCH_MONTHLY), f.play.plans)
        }
        ManagerFixture(offerAvailable = false).use { f ->
            f.manager.activateAccessCode(code)
            f.codes.finish(VerificationResult.Verified(f.signed.envelope("launch-offer")))
            f.manager.purchase(Activity(), BillingPlan.LAUNCH_MONTHLY)
            assertEquals(0, f.play.launches)
        }
    }

    @Test fun onlyExactApprovedLaunchPricesAreAccepted() {
        val intro = OfferPricePhase(2450000000L, "KRW", "₩2,450", "P1M", ProductDetails.RecurrenceMode.FINITE_RECURRING, 2)
        val renewal = OfferPricePhase(4900000000L, "KRW", "₩4,900", "P1M", ProductDetails.RecurrenceMode.INFINITE_RECURRING, 0)
        fun option(phases: List<OfferPricePhase>, id: String? = LAUNCH_OFFER_ID) = checkedBillingOption("jigeum_album", "monthly", id, phases)
        assertEquals(BillingPlan.LAUNCH_MONTHLY, option(listOf(intro, renewal))?.plan)
        assertEquals(2, option(listOf(intro, renewal))?.introductoryCycles)
        assertNull(option(listOf(intro, renewal), "unknown"))
        assertNull(option(listOf(intro, renewal), null))
        assertNull(option(listOf(renewal, intro)))
        for (bad in listOf(intro.copy(priceMicros = 2449000000L), intro.copy(currency = "USD"), intro.copy(cycles = 1),
            intro.copy(period = "P2M"), intro.copy(recurrence = ProductDetails.RecurrenceMode.NON_RECURRING))) {
            assertNull(option(listOf(bad, renewal)))
        }
        assertNull(option(listOf(intro, renewal.copy(priceMicros = 5900000000L))))
        assertEquals(BillingPlan.MONTHLY, option(listOf(renewal), null)?.plan)
    }

    private inner class SignedFixture {
        var now = 1790000000000L
        val install = UUID.randomUUID().toString()
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        fun claims(envelope: String, candidate: String = code) = verifiedAccessCodeClaims(envelope, keys.public, install, app, candidate, now)
        fun envelope(kind: String = "invitation", change: (JSONObject) -> Unit = {}): String {
            val claims = JSONObject().put("version", 1).put("grantSource", kind).put("packageName", app)
                .put("productId", "jigeum_album").put("installationId", install)
                .put("codeHash", MessageDigest.getInstance("SHA-256").digest(code.toByteArray()).joinToString("") { "%02x".format(it) })
                .put("basePlanId", if (kind == "invitation") "invitation" else "monthly")
                .put("accessId", "friends-permanent").put("issuedAtMs", now).put("expiresAtMs", now + 86400000)
                .put("subscriptionExpiryMs", now + 86400000).put("campaignId", "launch-20261027").put("offerId", LAUNCH_OFFER_ID)
                .put("currency", "KRW").put("introductoryPriceMicros", 2450000000L).put("introductoryCycles", 2)
                .put("billingPeriod", "P1M").put("renewalPriceMicros", 4900000000L)
            change(claims)
            val bytes = claims.toString().toByteArray()
            val sig = Signature.getInstance("SHA256withECDSA").apply { initSign(keys.private); update(bytes) }.sign()
            fun encode(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            return JSONObject().put("payload", encode(bytes)).put("signature", encode(sig)).toString()
        }
    }
    private inner class ManagerFixture(offerAvailable: Boolean = true) : AutoCloseable {
        val signed = SignedFixture()
        val purchase = PurchaseStub { signed.now }
        val codes = CodeStub(signed)
        val play = PlayStub(offerAvailable)
        lateinit var manager: BillingManager
        init { newManager() }
        fun newManager() { manager = BillingManager(purchase, { play }, clock = { signed.now }, recoveryClock = { signed.now }, accessCodes = codes) }
        fun activate() { manager.activateAccessCode(code); codes.finish(VerificationResult.Verified(signed.envelope())) }
        override fun close() { manager.close() }
    }
    private inner class CodeStub(val f: SignedFixture) : CodeEntitlements {
        override val isConfigured = true
        var saved: String? = null; var envelope: String? = null; var boot = 1; var anchor: EntitlementClockAnchor? = null; var calls = 0
        private var reply: Continuation<VerificationResult>? = null
        override fun storedCode() = saved
        override fun cachedExpiryMs() = cachedGrant()?.expiresAtMs
        override fun cachedGrant(): AccessCodeGrant? {
            val claims = f.claims(envelope ?: return null, saved ?: return null) ?: return null
            val remaining = remainingEntitlementMs(claims.getLong("issuedAtMs"), claims.getLong("expiresAtMs"), claims.getLong("subscriptionExpiryMs"), anchor, f.now, f.now, boot) ?: return null
            return AccessCodeGrant(if (claims.getString("grantSource") == "invitation") AccessCodeKind.INVITATION else AccessCodeKind.LAUNCH_OFFER, f.now + remaining)
        }
        override fun accept(envelope: String, code: String): Boolean {
            if (f.claims(envelope, code) == null) return false
            this.envelope = envelope; saved = code; anchor = EntitlementClockAnchor(ENTITLEMENT_CACHE_VERSION, f.now, f.now, boot)
            return true
        }
        override fun clear() { saved = null; envelope = null; anchor = null }
        override suspend fun verify(code: String): VerificationResult = suspendCoroutine { calls++; reply = it }
        fun finish(result: VerificationResult) { val pending = checkNotNull(reply); reply = null; pending.resume(result) }
    }
    private class PurchaseStub(val now: () -> Long) : BillingEntitlements {
        override val isConfigured = true
        var until: Long? = null
        override fun cachedExpiryMs() = until?.takeIf { it > now() }
        override fun cachedEntitlement() = cachedExpiryMs() != null
        override fun clear() { until = null }
        override fun accept(envelope: String) = false
        override suspend fun verify(productId: String, purchaseToken: String) = VerificationResult.Unavailable
    }
    private class PlayStub(val offerAvailable: Boolean) : BillingGateway {
        override val isReady = true
        val plans = mutableListOf<BillingPlan>()
        val launches get() = plans.size
        override fun startConnection(listener: BillingClientStateListener) = Unit
        override fun queryCatalog(callback: (BillingResult, BillingCatalog) -> Unit) {
            val regular = BillingOption(BillingPlan.MONTHLY, "jigeum_album", "monthly", "₩4,900", "P1M", "AUTO_RENEWING")
            val intro = BillingOption(BillingPlan.LAUNCH_MONTHLY, "jigeum_album", "monthly", "₩2,450", "P1M", "AUTO_RENEWING", "₩4,900", 2)
            callback(ok(), BillingCatalog(emptyList(), if (offerAvailable) listOf(regular, intro) else listOf(regular)))
        }
        override fun queryPurchases(callback: (BillingResult, List<Purchase>) -> Unit) = callback(ok(), emptyList())
        override fun purchase(activity: Activity, plan: BillingPlan): BillingResult { plans += plan; return ok() }
        override fun acknowledge(purchaseToken: String, callback: (BillingResult) -> Unit) = callback(ok())
        override fun endConnection() = Unit
        private fun ok() = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build()
    }
}
