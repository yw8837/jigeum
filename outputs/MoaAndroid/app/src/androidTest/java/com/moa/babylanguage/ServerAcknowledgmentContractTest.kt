package com.moa.babylanguage

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec

@RunWith(AndroidJUnit4::class)
class ServerAcknowledgmentContractTest {
    @Test fun nodeSignedUtf8TokenClaimIsVerifiedAndBoundToExactPurchaseOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val fixture = JSONObject(context.assets.open("server-ack-fixture.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(fixture.getString("publicKey"), Base64.DEFAULT)))
        val envelope = fixture.getJSONObject("envelope").toString()
        assertTrue(verifySignedEntitlement(envelope, key, fixture.getString("installationId"), "com.moa.babylanguage", fixture.getLong("now")))
        assertTrue(acknowledgesPurchase(envelope, fixture.getString("purchaseToken")))
        assertFalse(acknowledgesPurchase(envelope, fixture.getString("purchaseToken") + "different"))
        val tampered = JSONObject(envelope)
        val payload = JSONObject(String(Base64.decode(tampered.getString("payload"), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8))
        payload.put("acknowledgedTokenHash", "0".repeat(64))
        tampered.put("payload", Base64.encodeToString(payload.toString().toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
        assertFalse(verifySignedEntitlement(tampered.toString(), key, fixture.getString("installationId"), "com.moa.babylanguage", fixture.getLong("now")))
    }
}
