package com.moa.babylanguage

import android.content.Context
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReviewAccessVerifierTest {
    private val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val now = 1_790_000_000_000L
    private val installation = UUID.randomUUID().toString()
    private val packageName = "com.moa.babylanguage"
    private fun envelope(change: (JSONObject) -> Unit = {}): String {
        val claims = JSONObject().put("version", 1).put("grantSource", "play-review")
            .put("basePlanId", "play-review").put("reviewAccessId", "play-review-v1")
            .put("productId", "jigeum_album").put("packageName", packageName).put("installationId", installation)
            .put("issuedAtMs", now).put("expiresAtMs", now + 3_600_000).put("subscriptionExpiryMs", now + 86_400_000)
        change(claims)
        val payload = claims.toString().toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(keys.private); update(payload) }.sign()
        fun encode(data: ByteArray) = Base64.encodeToString(data, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
        return JSONObject().put("payload", encode(payload)).put("signature", encode(signature)).toString()
    }
    private fun valid(data: String, time: Long = now) = verifySignedReviewEntitlement(data, keys.public, installation, packageName, time)

    @Test fun signedReviewGrantIsBoundToPurposeInstallationAndExpiry() {
        val data = envelope()
        assertTrue(valid(data))
        assertFalse(verifySignedEntitlement(data, keys.public, installation, packageName, now))
        assertFalse(verifySignedReviewEntitlement(data, keys.public, UUID.randomUUID().toString(), packageName, now))
        assertFalse(verifySignedReviewEntitlement(data, keys.public, installation, "another.package", now))
        assertTrue(valid(data, now + 3_599_999))
        assertFalse(valid(data, now + 3_600_000))
    }

    @Test fun purchaseAndReviewGrantsCannotImpersonateEachOther() {
        val purchase = envelope { it.remove("grantSource"); it.remove("reviewAccessId"); it.put("basePlanId", "monthly") }
        assertTrue(verifySignedEntitlement(purchase, keys.public, installation, packageName, now))
        assertFalse(valid(purchase))
        val mislabeled = envelope { it.put("basePlanId", "monthly") }
        assertFalse(valid(mislabeled))
        assertFalse(verifySignedEntitlement(mislabeled, keys.public, installation, packageName, now))
        assertFalse(valid(envelope { it.put("grantSource", "play-purchase") }))
    }

    @Test fun rejectsInvalidSignatureClaimsClockAndOversizedData() {
        val broken = JSONObject(envelope()).put("signature", Base64.encodeToString(ByteArray(70), Base64.NO_WRAP)).toString()
        assertFalse(valid(broken))
        assertFalse(valid(envelope { it.put("reviewAccessId", "") }))
        assertFalse(valid(envelope { it.put("expiresAtMs", now + 86_400_001) }))
        assertFalse(valid(envelope { it.put("subscriptionExpiryMs", now + 10) }))
        assertFalse(valid(envelope { it.put("issuedAtMs", now + 300_001) }))
        assertFalse(valid(envelope { it.put("version", 2) }))
        assertFalse(valid(envelope { it.put("productId", "another") }))
        for (bad in listOf("", "null", "[]", "{broken", "x".repeat(16_385))) assertFalse(valid(bad))
        val other = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        assertFalse(verifySignedReviewEntitlement(envelope(), other.public, installation, packageName, now))
    }

    @Test fun onlyStrictHttpsOriginVerifyPathCanDeriveReviewEndpoint() {
        assertEquals("https://example.com/review", reviewEndpoint("https://example.com/verify"))
        assertEquals("https://example.com:8443/review", reviewEndpoint("https://example.com:8443/verify"))
        for (bad in listOf("", "http://example.com/verify", "https://user:pass@example.com/verify", "https://example.com/verify?to=evil",
            "https://example.com/verify#fragment", "https://example.com/another", "https://example.com/a/../verify",
            "https://example.com/%76erify", "https://example.com:0/verify", "https://example.com:65536/verify")) {
            assertNull(reviewEndpoint(bad))
        }
    }

    @Test fun credentialFormatRejectsWeakMalformedAndUnboundedInput() {
        assertTrue(validReviewCode("A_z-19".repeat(8)))
        assertTrue(validReviewCode("a".repeat(128)))
        for (bad in listOf("", "short", "a".repeat(31), "a".repeat(129), "a".repeat(32) + " ", "가".repeat(32))) assertFalse(validReviewCode(bad))
    }

    @Test fun realKeystoreEncryptsCredentialAndFailsClosedOnCorruptionCopyOrKeyLoss() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefsName = "review-crypto-test-${UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val store = ReviewCredentialStore(context, installation, prefs)
        val different = ReviewCredentialStore(context, UUID.randomUUID().toString(), prefs)
        val code = "synthetic-review-code-for-tests-only-0123456789"
        try {
            assertNull(store.read())
            val encrypted = store.encrypt(code)
            assertFalse(encrypted.contains(code))
            assertTrue(prefs.edit().putString("credential", encrypted).commit())
            assertEquals(code, store.read())
            assertEquals(code, ReviewCredentialStore(context, installation, prefs).read())
            assertNull(different.read())
            val corrupt = JSONObject(encrypted).put("ciphertext", Base64.encodeToString(ByteArray(64), Base64.NO_WRAP)).toString()
            assertTrue(prefs.edit().putString("credential", corrupt).commit())
            assertNull(store.read())
            assertTrue(prefs.edit().putString("credential", encrypted).commit())
            store.deleteKey()
            assertNull(store.read())
            assertTrue(prefs.edit().putString("credential", "x".repeat(1_025)).commit())
            assertNull(store.read())
        } finally {
            store.deleteKey(); different.deleteKey(); context.deleteSharedPreferences(prefsName)
        }
    }
}
