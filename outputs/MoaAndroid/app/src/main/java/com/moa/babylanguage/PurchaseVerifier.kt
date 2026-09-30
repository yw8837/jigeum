package com.moa.babylanguage

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

internal sealed interface VerificationResult {
    data class Verified(val envelope: String) : VerificationResult
    data object Rejected : VerificationResult
    data object Unavailable : VerificationResult
}

/** Internal seam for lifecycle tests; the public manager always uses PurchaseVerifier. */
internal interface BillingEntitlements {
    val isConfigured: Boolean
    fun cachedEntitlement(): Boolean
    fun cachedExpiryMs(): Long?
    fun clear()
    fun accept(envelope: String): Boolean
    suspend fun verify(productId: String, purchaseToken: String): VerificationResult
}

/** Holds only a short signed entitlement; raw purchase tokens are never persisted. */
internal class PurchaseVerifier(private val context: Context) : BillingEntitlements {
    private val preferences = context.getSharedPreferences("play_entitlement", Context.MODE_PRIVATE)
    private val publicKey: java.security.PublicKey? = runCatching {
        val encoded = Base64.decode(BuildConfig.PLAY_ENTITLEMENT_PUBLIC_KEY, Base64.DEFAULT)
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
    }.getOrNull()
    private val endpoint = BuildConfig.PLAY_VERIFY_URL.trim()
    override val isConfigured: Boolean = runCatching {
        val url = URL(endpoint)
        url.protocol == "https" && url.host.isNotBlank() && url.userInfo == null && url.ref == null && publicKey != null
    }.getOrDefault(false)

    private val installationId: String? = run {
        preferences.getString("installation_id", null)?.takeIf { it.isNotBlank() } ?: run {
            val generated = UUID.randomUUID().toString()
            if (preferences.edit().putString("installation_id", generated).commit()) generated else null
        }
    }

    override fun cachedEntitlement(): Boolean {
        if (!isConfigured) return false
        val envelope = preferences.getString("entitlement", null) ?: return false
        if (!isValid(envelope)) {
            preferences.edit().remove("entitlement").commit()
            return false
        }
        return true
    }

    override fun clear() {
        preferences.edit().remove("entitlement").commit()
    }

    /** Called only by the current Main-thread verification generation. */
    override fun accept(envelope: String): Boolean = isConfigured && isValid(envelope) &&
        preferences.edit().putString("entitlement", envelope).commit()

    override fun cachedExpiryMs(): Long? {
        if (!cachedEntitlement()) return null
        return runCatching {
            val envelope = JSONObject(preferences.getString("entitlement", null)!!)
            val payload = Base64.decode(envelope.getString("payload"), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            JSONObject(String(payload, Charsets.UTF_8)).getLong("expiresAtMs")
        }.getOrNull()
    }

    /** Network work cannot mutate the cache: late/cancelled replies cannot unlock an old session. */
    override suspend fun verify(productId: String, purchaseToken: String): VerificationResult = withContext(Dispatchers.IO) {
        if (!isConfigured || installationId == null || productId != PRODUCT_ID || purchaseToken.length !in 8..4096 || purchaseToken.isBlank()) {
            return@withContext VerificationResult.Unavailable
        }
        val connection = try {
            URL(endpoint).openConnection() as? HttpURLConnection ?: return@withContext VerificationResult.Unavailable
        } catch (_: Exception) {
            return@withContext VerificationResult.Unavailable
        }
        try {
            connection.requestMethod = "POST"
            // A misconfigured redirect must not forward a Play purchase token elsewhere.
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 7_000
            connection.readTimeout = 18_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            val request = JSONObject()
                .put("packageName", context.packageName)
                .put("productId", productId)
                .put("purchaseToken", purchaseToken)
                .put("installationId", installationId)
                .toString().toByteArray(Charsets.UTF_8)
            if (request.size > 16_384) return@withContext VerificationResult.Unavailable
            connection.setFixedLengthStreamingMode(request.size)
            connection.outputStream.use { it.write(request) }
            if (connection.responseCode == 403) return@withContext VerificationResult.Rejected
            if (connection.responseCode != 200) return@withContext VerificationResult.Unavailable
            val response = connection.inputStream.use { input ->
                val bytes = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (bytes.size() + count > 16_384) return@withContext VerificationResult.Unavailable
                    bytes.write(buffer, 0, count)
                }
                bytes.toString(Charsets.UTF_8.name())
            }
            val envelope = JSONObject(response).getJSONObject("entitlement").toString()
            if (!isValid(envelope)) return@withContext VerificationResult.Unavailable
            VerificationResult.Verified(envelope)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            VerificationResult.Unavailable
        } finally {
            connection.disconnect()
        }
    }

    private fun isValid(envelope: String): Boolean {
        val key = publicKey ?: return false
        val installation = installationId ?: return false
        return verifySignedEntitlement(envelope, key, installation, context.packageName, System.currentTimeMillis())
    }

    companion object { const val PRODUCT_ID = "jigeum_album" }
}

/** Pure signature and claim check, also exercised in androidTest without a release bypass. */
internal fun verifySignedEntitlement(
    envelope: String,
    key: java.security.PublicKey,
    installation: String,
    packageName: String,
    now: Long
): Boolean = runCatching {
        if (envelope.length > 16_384) return false
        val json = JSONObject(envelope)
        val payloadBytes = Base64.decode(json.getString("payload"), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val signatureBytes = Base64.decode(json.getString("signature"), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(key)
        verifier.update(payloadBytes)
        if (!verifier.verify(signatureBytes)) return false
        val claims = JSONObject(String(payloadBytes, Charsets.UTF_8))
        val issuedAt = claims.getLong("issuedAtMs")
        val expiresAt = claims.getLong("expiresAtMs")
        val subscriptionExpiry = claims.getLong("subscriptionExpiryMs")
        claims.getInt("version") == 1 &&
            claims.getString("installationId") == installation &&
            claims.getString("packageName") == packageName &&
            claims.getString("productId") == PurchaseVerifier.PRODUCT_ID &&
            BillingPlan.entries.any { it.basePlanId == claims.getString("basePlanId") } &&
            issuedAt >= now - 86_400_000 &&
            issuedAt <= now + 300_000 &&
            expiresAt > issuedAt &&
            expiresAt > now &&
            expiresAt <= issuedAt + 86_400_000 &&
            subscriptionExpiry > now &&
            expiresAt <= subscriptionExpiry
    }.getOrDefault(false)
