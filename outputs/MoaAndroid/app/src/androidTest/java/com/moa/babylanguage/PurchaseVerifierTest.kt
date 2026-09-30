package com.moa.babylanguage

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

@RunWith(AndroidJUnit4::class)
class PurchaseVerifierTest {
    private val keys = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
    private val now = 1_780_000_000_000L
    private val installation = "94be8be6-04c9-4b85-9994-69113bf442d0"

    private fun envelope(
        issuedAt: Long = now,
        expiresAt: Long = now + 3_600_000,
        subscriptionExpiry: Long = now + 86_400_000,
        productId: String = "jigeum_album",
        basePlanId: String = "monthly"
    ): String {
        val claims = JSONObject()
            .put("version", 1)
            .put("installationId", installation)
            .put("packageName", "com.moa.babylanguage")
            .put("productId", productId)
            .put("basePlanId", basePlanId)
            .put("issuedAtMs", issuedAt)
            .put("expiresAtMs", expiresAt)
            .put("subscriptionExpiryMs", subscriptionExpiry)
            .toString().toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(keys.private)
            update(claims)
        }.sign()
        fun encode(value: ByteArray) = Base64.encodeToString(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return JSONObject().put("payload", encode(claims)).put("signature", encode(signature)).toString()
    }

    @Test fun acceptsValidSignatureForSameInstallation() {
        assertTrue(verifySignedEntitlement(envelope(), keys.public, installation, "com.moa.babylanguage", now))
    }

    @Test fun rejectsTamperingWrongInstallationAndExpiry() {
        val valid = envelope()
        assertFalse(verifySignedEntitlement(valid.replace("signature", "signaturE"), keys.public, installation, "com.moa.babylanguage", now))
        assertFalse(verifySignedEntitlement(valid, keys.public, "different", "com.moa.babylanguage", now))
        assertFalse(verifySignedEntitlement(envelope(expiresAt = now - 1), keys.public, installation, "com.moa.babylanguage", now))
        assertFalse(verifySignedEntitlement(envelope(expiresAt = now + 86_400_001), keys.public, installation, "com.moa.babylanguage", now))
        assertFalse(verifySignedEntitlement(envelope(subscriptionExpiry = now - 1), keys.public, installation, "com.moa.babylanguage", now))
        assertFalse(verifySignedEntitlement(envelope(productId = "other"), keys.public, installation, "com.moa.babylanguage", now))
        assertFalse(verifySignedEntitlement(envelope(basePlanId = "other"), keys.public, installation, "com.moa.babylanguage", now))
    }

    @Test fun enforcesBoundedOfflineGrantAndSuppliedClock() {
        val grant = envelope()
        assertTrue(verifySignedEntitlement(grant, keys.public, installation, "com.moa.babylanguage", now + 3_599_999))
        assertFalse(verifySignedEntitlement(grant, keys.public, installation, "com.moa.babylanguage", now + 3_600_000))
        assertFalse(verifySignedEntitlement(grant, keys.public, installation, "com.moa.babylanguage", now - 300_001))
        assertFalse(verifySignedEntitlement(envelope(issuedAt = now - 86_400_001), keys.public, installation, "com.moa.babylanguage", now))
        assertFalse(verifySignedEntitlement(envelope(issuedAt = now + 300_001), keys.public, installation, "com.moa.babylanguage", now))
        assertFalse(verifySignedEntitlement(envelope(issuedAt = now + 1000, expiresAt = now + 999), keys.public, installation, "com.moa.babylanguage", now))
        assertFalse(verifySignedEntitlement(envelope(subscriptionExpiry = now + 1000), keys.public, installation, "com.moa.babylanguage", now))
        assertTrue(verifySignedEntitlement(envelope(basePlanId = "three-month-prepaid"), keys.public, installation, "com.moa.babylanguage", now))
    }

    @Test fun rejectsCorruptOversizedAndWrongKeyData() {
        for (bad in listOf("", "null", "[]", "{}", "{broken", "x".repeat(16_385))) {
            assertFalse(verifySignedEntitlement(bad, keys.public, installation, "com.moa.babylanguage", now))
        }
        val wrongKey = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        assertFalse(verifySignedEntitlement(envelope(), wrongKey.public, installation, "com.moa.babylanguage", now))
        assertFalse(verifySignedEntitlement(envelope(), keys.public, installation, "other.package", now))
    }

    @Test fun missingConfigurationCannotUnlockPremium() {
        // This gate asserts only the deliberately unconfigured QA build.
        assumeTrue(BuildConfig.PLAY_VERIFY_URL.isBlank() && BuildConfig.PLAY_ENTITLEMENT_PUBLIC_KEY.isBlank())
        val verifier = PurchaseVerifier(InstrumentationRegistry.getInstrumentation().targetContext)
        assertFalse(verifier.isConfigured)
        assertFalse(verifier.cachedEntitlement())
        assertFalse(verifier.accept(envelope()))
    }

    @Test fun unconfiguredManagerSurvivesRefreshRestoreAndRepeatedClose() {
        assumeTrue(BuildConfig.PLAY_VERIFY_URL.isBlank() && BuildConfig.PLAY_ENTITLEMENT_PUBLIC_KEY.isBlank())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val manager = BillingManager(instrumentation.targetContext)
            try {
                repeat(10) { manager.refresh(); manager.restore() }
                assertEquals(BillingStatus.UNAVAILABLE, manager.state.value.status)
                assertTrue(manager.state.value.options.isEmpty())
                assertFalse(manager.state.value.isPremium)
            } finally {
                manager.close(); manager.close()
                manager.refresh(); manager.restore()
            }
            assertFalse(manager.state.value.isPremium)
        }
    }
}
