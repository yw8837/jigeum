package com.moa.babylanguage

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
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
import java.security.KeyStore
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface ReviewEntitlements {
    val isConfigured: Boolean
    fun storedCode(): String?
    fun cachedExpiryMs(): Long?
    fun clear()
    fun accept(envelope: String, code: String): Boolean
    suspend fun verify(code: String): VerificationResult
}

internal fun validReviewCode(code: String): Boolean = code.matches(Regex("[A-Za-z0-9_-]{32,128}"))

/** Only the exact configured verification origin is allowed; never forward a code to a redirect. */
internal fun reviewEndpoint(verificationEndpoint: String): String? = runCatching {
    val uri = URI(verificationEndpoint.trim())
    if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
        uri.rawQuery != null || uri.rawFragment != null || uri.rawPath != "/verify" ||
        uri.port != -1 && uri.port !in 1..65_535) return null
    URI("https", null, uri.host, uri.port, "/review", null, null).toASCIIString()
}.getOrNull()

/** Review grants have an explicit purpose and cannot be accepted as a Play purchase. */
internal fun verifySignedReviewEntitlement(
    envelope: String, key: PublicKey, installation: String, packageName: String, now: Long
): Boolean = runCatching {
    if (envelope.length > 16_384) return false
    val json = JSONObject(envelope)
    val payload = Base64.decode(json.getString("payload"), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    val signature = Base64.decode(json.getString("signature"), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    val verifier = Signature.getInstance("SHA256withECDSA").apply { initVerify(key); update(payload) }
    if (!verifier.verify(signature)) return false
    val claims = JSONObject(String(payload, Charsets.UTF_8))
    val issued = claims.getLong("issuedAtMs")
    val expires = claims.getLong("expiresAtMs")
    val accessExpiry = claims.getLong("subscriptionExpiryMs")
    claims.getInt("version") == 1 && claims.getString("grantSource") == "play-review" &&
        claims.getString("basePlanId") == "play-review" &&
        claims.getString("reviewAccessId").matches(Regex("[A-Za-z0-9_-]{8,128}")) &&
        claims.getString("installationId") == installation && claims.getString("packageName") == packageName &&
        claims.getString("productId") == PurchaseVerifier.PRODUCT_ID &&
        issued >= now - 86_400_000 && issued <= now + 300_000 && expires > issued &&
        expires > now && expires <= issued + 86_400_000 && accessExpiry > now && expires <= accessExpiry
}.getOrDefault(false)

/**
 * A real server-signed review grant, separate from Play ownership. The reusable
 * credential is encrypted by a device-only Keystore key and never put in saved UI state.
 */
internal class ReviewAccessVerifier(private val context: Context) : ReviewEntitlements {
    private val preferences = context.getSharedPreferences("play_review_entitlement", Context.MODE_PRIVATE)
    private val publicKey: PublicKey? = runCatching {
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(
            Base64.decode(BuildConfig.PLAY_ENTITLEMENT_PUBLIC_KEY, Base64.DEFAULT)))
    }.getOrNull()
    private val endpoint = reviewEndpoint(BuildConfig.PLAY_VERIFY_URL)
    override val isConfigured: Boolean get() = endpoint != null && publicKey != null
    private val installationId: String? = preferences.getString("installation_id", null)
        ?.takeIf { it.matches(Regex("[0-9a-fA-F-]{36}")) } ?: run {
        val generated = UUID.randomUUID().toString()
        if (preferences.edit().putString("installation_id", generated).commit()) generated else null
    }
    private val credentials = installationId?.let { ReviewCredentialStore(context, it, preferences) }

    /** Reads only; asynchronous network work never changes grant or credential storage. */
    override fun storedCode(): String? = credentials?.read()

    override fun clear() {
        clearGrant(removeCredential = true)
        credentials?.deleteKey()
    }

    private fun clearGrant(removeCredential: Boolean = false) {
        preferences.edit().remove("entitlement").remove("cache_version")
            .remove("received_wall").remove("received_elapsed").remove("boot_count")
            .apply { if (removeCredential) remove("credential") }.commit()
    }

    override fun cachedExpiryMs(): Long? {
        if (!isConfigured) return null
        if (storedCode() == null) {
            // Copying preferences, losing the key, or corrupting ciphertext cannot grant access.
            if (preferences.contains("credential") || preferences.contains("entitlement")) clear()
            return null
        }
        val expiry = runCatching {
            val envelope = preferences.getString("entitlement", null) ?: return@runCatching null
            val now = snapshot() ?: return@runCatching null
            if (!isValid(envelope, now.wall)) return@runCatching null
            val anchor = EntitlementClockAnchor(preferences.getInt("cache_version", -1),
                preferences.getLong("received_wall", -1), preferences.getLong("received_elapsed", -1),
                preferences.getInt("boot_count", -1))
            val remaining = remaining(envelope, anchor, now) ?: return@runCatching null
            now.wall + remaining
        }.getOrNull()
        if (expiry == null && preferences.contains("entitlement")) clearGrant()
        return expiry
    }

    override fun accept(envelope: String, code: String): Boolean {
        if (!isConfigured || !validReviewCode(code)) return false
        val now = snapshot() ?: return false
        if (!isValid(envelope, now.wall)) return false
        val anchor = EntitlementClockAnchor(ENTITLEMENT_CACHE_VERSION, now.wall, now.elapsed, now.boot)
        if (remaining(envelope, anchor, now) == null) return false
        val encrypted = runCatching { credentials?.encrypt(code) }.getOrNull() ?: return false
        return preferences.edit().putString("entitlement", envelope).putString("credential", encrypted)
            .putInt("cache_version", anchor.version).putLong("received_wall", anchor.receivedAtWallMs)
            .putLong("received_elapsed", anchor.receivedAtElapsedMs).putInt("boot_count", anchor.bootCount).commit()
    }

    private data class Snapshot(val wall: Long, val elapsed: Long, val boot: Int)
    private fun snapshot(): Snapshot? = runCatching {
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (boot < 0) return null
        Snapshot(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot)
    }.getOrNull()
    private fun remaining(envelope: String, anchor: EntitlementClockAnchor, now: Snapshot): Long? = runCatching {
        val payload = Base64.decode(JSONObject(envelope).getString("payload"), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val claims = JSONObject(String(payload, Charsets.UTF_8))
        remainingEntitlementMs(claims.getLong("issuedAtMs"), claims.getLong("expiresAtMs"),
            claims.getLong("subscriptionExpiryMs"), anchor, now.wall, now.elapsed, now.boot)
    }.getOrNull()
    private fun isValid(envelope: String, now: Long): Boolean {
        val key = publicKey ?: return false
        val installation = installationId ?: return false
        return verifySignedReviewEntitlement(envelope, key, installation, context.packageName, now)
    }
    override suspend fun verify(code: String): VerificationResult = withContext(Dispatchers.IO) {
        if (!isConfigured || installationId == null || !validReviewCode(code)) return@withContext VerificationResult.Unavailable
        val connection = try {
            URI(checkNotNull(endpoint)).toURL().openConnection() as? HttpURLConnection
                ?: return@withContext VerificationResult.Unavailable
        } catch (_: Exception) { return@withContext VerificationResult.Unavailable }
        val deadline = HttpRequestDeadline(connection, currentCoroutineContext().job)
        try {
            deadline.checkActive()
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 7_000
            // Match the purchase path and exceed the origin's 23-second deadline.
            connection.readTimeout = 26_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            val request = JSONObject().put("packageName", context.packageName).put("installationId", installationId)
                .put("reviewCode", code).toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(request.size)
            deadline.checkActive()
            connection.outputStream.use { it.write(request) }
            deadline.checkActive()
            if (connection.responseCode == 403) return@withContext VerificationResult.Rejected
            if (connection.responseCode != 200) return@withContext VerificationResult.Unavailable
            val response = connection.inputStream.use { input ->
                val bytes = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(1_024)
                while (true) {
                    deadline.checkActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (bytes.size() + count > 16_384) return@withContext VerificationResult.Unavailable
                    bytes.write(buffer, 0, count)
                }
                bytes.toString(Charsets.UTF_8.name())
            }
            val envelope = JSONObject(response).getJSONObject("entitlement").toString()
            deadline.checkActive()
            if (!isValid(envelope, System.currentTimeMillis())) return@withContext VerificationResult.Unavailable
            deadline.checkActive()
            VerificationResult.Verified(envelope)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { VerificationResult.Unavailable }
        finally { deadline.close(); connection.disconnect() }
    }
}

/** Real device encryption; tests exercise this class with isolated prefs and device keys. */
internal class ReviewCredentialStore(
    context: Context,
    private val installationId: String,
    private val preferences: SharedPreferences
) {
    private val packageName = context.packageName
    private val keyAlias = "$packageName.play_review_code_v1.$installationId"
    private fun keystore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun aad() = "$packageName:$installationId:play-review-v1".toByteArray(Charsets.UTF_8)
    fun deleteKey() { runCatching { keystore().deleteEntry(keyAlias) } }
    fun read(): String? = runCatching {
        val stored = preferences.getString("credential", null) ?: return null
        if (stored.length > 1_024) return null
        val data = JSONObject(stored)
        if (data.getInt("version") != 1) return null
        val iv = Base64.decode(data.getString("iv"), Base64.NO_WRAP)
        val bytes = Base64.decode(data.getString("ciphertext"), Base64.NO_WRAP)
        if (iv.size != 12 || bytes.size !in 48..144) return null
        val key = keystore().getKey(keyAlias, null) as? SecretKey ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad())
        String(cipher.doFinal(bytes), Charsets.UTF_8).takeIf(::validReviewCode)
    }.getOrNull()
    fun encrypt(code: String): String {
        require(validReviewCode(code))
        val store = keystore()
        val key = (store.getKey(keyAlias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).setKeySize(256).build())
            generateKey()
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad())
        return JSONObject().put("version", 1).put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(cipher.doFinal(code.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)).toString()
    }
}
