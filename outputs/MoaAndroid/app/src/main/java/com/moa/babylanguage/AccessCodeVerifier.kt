package com.moa.babylanguage

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

internal const val LAUNCH_OFFER_ID = "launch-2m-half"
enum class AccessCodeKind { NONE, INVITATION, LAUNCH_OFFER }
data class AccessCodeGrant(val kind: AccessCodeKind, val expiresAtMs: Long)
internal interface CodeEntitlements : ReviewEntitlements {
    fun cachedGrant(): AccessCodeGrant?
}

internal fun verifiedAccessCodeClaims(envelope: String, key: PublicKey, installation: String,
    packageName: String, code: String, now: Long): JSONObject? = runCatching {
    if (!validReviewCode(code) || envelope.length > 16_384) return null
    val json = JSONObject(envelope)
    val payload = Base64.decode(json.getString("payload"), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    val signature = Base64.decode(json.getString("signature"), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    if (!Signature.getInstance("SHA256withECDSA").run { initVerify(key); update(payload); verify(signature) }) return null
    val claims = JSONObject(String(payload, Charsets.UTF_8))
    val issued = claims.getLong("issuedAtMs")
    val expires = claims.getLong("expiresAtMs")
    val codeHash = MessageDigest.getInstance("SHA-256").digest(code.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    if (claims.getInt("version") != 1 || claims.getString("installationId") != installation ||
        claims.getString("packageName") != packageName || claims.getString("productId") != PurchaseVerifier.PRODUCT_ID ||
        claims.getString("codeHash") != codeHash || issued < now - 86_400_000 || issued > now + 300_000 ||
        expires <= issued || expires <= now || expires > issued + 86_400_000 ||
        claims.getLong("subscriptionExpiryMs") != expires) return null
    when (claims.getString("grantSource")) {
        "invitation" -> if (claims.getString("basePlanId") != "invitation" || claims.getString("accessId") != "friends-permanent") return null
        "launch-offer" -> if (claims.getString("basePlanId") != "monthly" ||
            claims.getString("campaignId") != "launch-20261027" || claims.getString("offerId") != LAUNCH_OFFER_ID ||
            claims.getString("currency") != "KRW" || claims.getLong("introductoryPriceMicros") != 2_450_000_000L ||
            claims.getInt("introductoryCycles") != 2 || claims.getString("billingPeriod") != "P1M" ||
            claims.getLong("renewalPriceMicros") != 4_900_000_000L) return null
        else -> return null
    }
    claims
}.getOrNull()

/** Server-signed invitations and discount eligibility. Discount eligibility is never full access. */
internal class AccessCodeVerifier(private val context: Context) : CodeEntitlements {
    private val preferences = context.getSharedPreferences("access_code_entitlement", Context.MODE_PRIVATE)
    private val publicKey = runCatching { KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(
        Base64.decode(BuildConfig.PLAY_ENTITLEMENT_PUBLIC_KEY, Base64.DEFAULT))) }.getOrNull()
    private val endpoint = reviewEndpoint(BuildConfig.PLAY_VERIFY_URL)?.let {
        val uri = URI(it); URI("https", null, uri.host, uri.port, "/access-code", null, null).toASCIIString()
    }
    override val isConfigured: Boolean get() = endpoint != null && publicKey != null
    private val installationId = preferences.getString("installation_id", null)
        ?.takeIf { it.matches(Regex("[0-9a-fA-F-]{36}")) } ?: run {
        val id = UUID.randomUUID().toString()
        if (preferences.edit().putString("installation_id", id).commit()) id else null
    }
    private val credentials = installationId?.let { ReviewCredentialStore(context, it, preferences) }
    override fun storedCode(): String? = credentials?.read()
    override fun clear() {
        preferences.edit().remove("credential").remove("entitlement").remove("cache_version")
            .remove("received_wall").remove("received_elapsed").remove("boot_count").commit()
        credentials?.deleteKey()
    }
    override fun cachedExpiryMs(): Long? = cachedGrant()?.expiresAtMs
    override fun cachedGrant(): AccessCodeGrant? {
        val code = storedCode() ?: return null
        val envelope = preferences.getString("entitlement", null) ?: return null
        val now = System.currentTimeMillis()
        val claims = claims(envelope, code, now) ?: return null
        val boot = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1)
        val anchor = EntitlementClockAnchor(preferences.getInt("cache_version", -1),
            preferences.getLong("received_wall", -1), preferences.getLong("received_elapsed", -1),
            preferences.getInt("boot_count", -1))
        val remaining = remainingEntitlementMs(claims.getLong("issuedAtMs"), claims.getLong("expiresAtMs"),
            claims.getLong("subscriptionExpiryMs"), anchor, now, SystemClock.elapsedRealtime(), boot) ?: return null
        return AccessCodeGrant(if (claims.getString("grantSource") == "invitation") AccessCodeKind.INVITATION
            else AccessCodeKind.LAUNCH_OFFER, now + remaining)
    }
    override fun accept(envelope: String, code: String): Boolean {
        val now = System.currentTimeMillis()
        if (claims(envelope, code, now) == null) return false
        val boot = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1)
        if (boot < 0) return false
        val encrypted = runCatching { credentials?.encrypt(code) }.getOrNull() ?: return false
        return preferences.edit().putString("entitlement", envelope).putString("credential", encrypted)
            .putInt("cache_version", ENTITLEMENT_CACHE_VERSION).putLong("received_wall", now)
            .putLong("received_elapsed", SystemClock.elapsedRealtime()).putInt("boot_count", boot).commit()
    }
    private fun claims(envelope: String, code: String, now: Long): JSONObject? {
        val key = publicKey ?: return null
        val installation = installationId ?: return null
        return verifiedAccessCodeClaims(envelope, key, installation, context.packageName, code, now)
    }
    override suspend fun verify(code: String): VerificationResult = withContext(Dispatchers.IO) {
        if (!isConfigured || installationId == null || !validReviewCode(code)) return@withContext VerificationResult.Unavailable
        val connection = try { URI(checkNotNull(endpoint)).toURL().openConnection() as HttpURLConnection }
            catch (_: Exception) { return@withContext VerificationResult.Unavailable }
        val deadline = HttpRequestDeadline(connection, currentCoroutineContext().job)
        try {
            connection.requestMethod = "POST"; connection.instanceFollowRedirects = false
            connection.connectTimeout = 7_000; connection.readTimeout = 26_000; connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            val bytes = JSONObject().put("packageName", context.packageName).put("installationId", installationId)
                .put("code", code).toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            deadline.checkActive(); connection.outputStream.use { it.write(bytes) }; deadline.checkActive()
            if (connection.responseCode == 403) return@withContext VerificationResult.Rejected
            if (connection.responseCode != 200) return@withContext VerificationResult.Unavailable
            val response = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(1_024)
                while (true) {
                    deadline.checkActive(); val count = input.read(buffer); if (count < 0) break
                    if (out.size() + count > 16_384) return@withContext VerificationResult.Unavailable
                    out.write(buffer, 0, count)
                }
                out.toString(Charsets.UTF_8.name())
            }
            val envelope = JSONObject(response).getJSONObject("entitlement").toString()
            deadline.checkActive()
            if (claims(envelope, code, System.currentTimeMillis()) == null) return@withContext VerificationResult.Unavailable
            VerificationResult.Verified(envelope)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { VerificationResult.Unavailable }
        finally { deadline.close(); connection.disconnect() }
    }
}
