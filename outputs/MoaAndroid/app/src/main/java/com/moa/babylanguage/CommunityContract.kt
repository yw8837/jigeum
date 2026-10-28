package com.moa.babylanguage

import java.net.URI

/** The board receives only files explicitly attached by the caregiver. */
internal object CommunityContract {
    const val MAX_BYTES = 25L * 1024 * 1024
    fun origin(value: String): String? = runCatching {
        val uri = URI(value)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null)
        require(uri.port == -1 || uri.port == 443)
        require(uri.rawQuery == null && uri.rawFragment == null && uri.rawPath in listOf("", "/"))
        "https://${uri.host.lowercase()}"
    }.getOrNull()

    fun allows(base: String, destination: String): Boolean = runCatching {
        val allowed = origin(base) ?: return false
        val uri = URI(destination)
        uri.scheme == "https" && uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443) &&
            "https://${uri.host?.lowercase()}" == allowed
    }.getOrDefault(false)
}
