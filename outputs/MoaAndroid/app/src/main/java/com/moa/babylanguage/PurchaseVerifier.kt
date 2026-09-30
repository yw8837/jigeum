package com.moa.babylanguage

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

internal sealed interface VerificationResult {
    data class Verified(val envelope: String) : VerificationResult
    data object Rejected : VerificationResult
    data object Unavailable : VerificationResult
}

/** Call only after accept(envelope) verifies its signature and current grant. */
internal fun acknowledgesPurchase(envelope: String, purchaseToken: String): Boolean = runCatching {
    val payload = Base64.decode(JSONObject(envelope).getString("payload"), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    val claimed = JSONObject(String(payload, Charsets.UTF_8)).opt("acknowledgedTokenHash") as? String ?: return false
    if (!claimed.matches(Regex("[0-9a-f]{64}"))) return false
    val actual = MessageDigest.getInstance("SHA-256").digest(purchaseToken.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    MessageDigest.isEqual(claimed.toByteArray(Charsets.US_ASCII), actual.toByteArray(Charsets.US_ASCII))
}.getOrDefault(false)

/** Internal seam for lifecycle tests; the public manager always uses PurchaseVerifier. */
internal interface BillingEntitlements {
    val isConfigured: Boolean
    fun cachedEntitlement(): Boolean
    fun cachedExpiryMs(): Long?
    fun clear()
    fun accept(envelope: String): Boolean
    suspend fun verify(productId: String, purchaseToken: String): VerificationResult
}

internal const val ENTITLEMENT_CACHE_VERSION = 1
private const val MAX_OFFLINE_ENTITLEMENT_MS = 86_400_000L
private const val ENTITLEMENT_CLOCK_SKEW_MS = 300_000L

internal data class EntitlementClockAnchor(
    val version: Int,
    val receivedAtWallMs: Long,
    val receivedAtElapsedMs: Long,
    val bootCount: Int
)

/**
 * Pure time policy after signature verification. Wall-clock rollback cannot add
 * time to the budget fixed when the grant was received on this boot.
 */
internal fun remainingEntitlementMs(
    issuedAtMs: Long,
    expiresAtMs: Long,
    subscriptionExpiryMs: Long,
    anchor: EntitlementClockAnchor?,
    nowWallMs: Long,
    nowElapsedMs: Long,
    bootCount: Int
): Long? {
    if (anchor == null || anchor.version != ENTITLEMENT_CACHE_VERSION) return null
    if (bootCount < 0 || anchor.bootCount < 0 || bootCount != anchor.bootCount) return null
    if (anchor.receivedAtWallMs <= 0 || nowWallMs <= 0 || anchor.receivedAtElapsedMs < 0 || nowElapsedMs < anchor.receivedAtElapsedMs) return null
    if (issuedAtMs <= 0 || expiresAtMs <= issuedAtMs || subscriptionExpiryMs < expiresAtMs) return null
    if (expiresAtMs - issuedAtMs > MAX_OFFLINE_ENTITLEMENT_MS) return null
    if (anchor.receivedAtWallMs < issuedAtMs - ENTITLEMENT_CLOCK_SKEW_MS || anchor.receivedAtWallMs >= expiresAtMs) return null
    if (nowWallMs < issuedAtMs - ENTITLEMENT_CLOCK_SKEW_MS || nowWallMs >= expiresAtMs) return null
    val originalBudget = minOf(expiresAtMs - anchor.receivedAtWallMs, MAX_OFFLINE_ENTITLEMENT_MS)
    val elapsed = nowElapsedMs - anchor.receivedAtElapsedMs
    if (elapsed >= originalBudget) return null
    return minOf(originalBudget - elapsed, expiresAtMs - nowWallMs).takeIf { it > 0 }
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

    override fun cachedEntitlement(): Boolean = cachedGrant() != null

    override fun clear() {
        // Keep revocation synchronous; async apply could restore an old grant after a crash.
        preferences.edit(commit = true) {
            remove("entitlement")
            remove("entitlement_cache_version")
            remove("entitlement_received_wall_ms")
            remove("entitlement_received_elapsed_ms")
            remove("entitlement_boot_count")
        }
    }

    /** Called only by the current Main-thread verification generation. */
    override fun accept(envelope: String): Boolean {
        if (!isConfigured) return false
        val now = clockSnapshot() ?: return false
        if (!isValid(envelope, now.wallMs)) return false
        val anchor = EntitlementClockAnchor(ENTITLEMENT_CACHE_VERSION, now.wallMs, now.elapsedMs, now.bootCount)
        if (remaining(envelope, anchor, now) == null) return false
        // Envelope and evidence form one atomic preference transaction. A legacy
        // envelope without these fields must be reverified online before reuse.
        return preferences.edit()
            .putString("entitlement", envelope)
            .putInt("entitlement_cache_version", anchor.version)
            .putLong("entitlement_received_wall_ms", anchor.receivedAtWallMs)
            .putLong("entitlement_received_elapsed_ms", anchor.receivedAtElapsedMs)
            .putInt("entitlement_boot_count", anchor.bootCount)
            .commit()
    }

    /** Effective UI deadline, bounded by both wall time and same-boot elapsed time. */
    override fun cachedExpiryMs(): Long? {
        val cached = cachedGrant() ?: return null
        return cached.wallMs + cached.remainingMs
    }

    private data class ClockSnapshot(val wallMs: Long, val elapsedMs: Long, val bootCount: Int)
    private data class CachedGrant(val wallMs: Long, val remainingMs: Long)

    private fun clockSnapshot(): ClockSnapshot? = runCatching {
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (boot < 0) return null
        ClockSnapshot(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot)
    }.getOrNull()

    private fun cachedGrant(): CachedGrant? {
        if (!isConfigured) return null
        val cached = runCatching {
            val envelope = preferences.getString("entitlement", null) ?: return@runCatching null
            val now = clockSnapshot() ?: return@runCatching null
            if (!isValid(envelope, now.wallMs)) return@runCatching null
            val anchor = EntitlementClockAnchor(
                preferences.getInt("entitlement_cache_version", -1),
                preferences.getLong("entitlement_received_wall_ms", -1),
                preferences.getLong("entitlement_received_elapsed_ms", -1),
                preferences.getInt("entitlement_boot_count", -1)
            )
            remaining(envelope, anchor, now)?.let { CachedGrant(now.wallMs, it) }
        }.getOrNull()
        if (cached == null && preferences.contains("entitlement")) clear()
        return cached
    }

    private fun remaining(envelope: String, anchor: EntitlementClockAnchor, now: ClockSnapshot): Long? = runCatching {
        val payload = Base64.decode(JSONObject(envelope).getString("payload"), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val claims = JSONObject(String(payload, Charsets.UTF_8))
        remainingEntitlementMs(claims.getLong("issuedAtMs"), claims.getLong("expiresAtMs"), claims.getLong("subscriptionExpiryMs"),
            anchor, now.wallMs, now.elapsedMs, now.bootCount)
    }.getOrNull()

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

    private fun isValid(envelope: String, now: Long = System.currentTimeMillis()): Boolean {
        val key = publicKey ?: return false
        val installation = installationId ?: return false
        return verifySignedEntitlement(envelope, key, installation, context.packageName, now)
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
