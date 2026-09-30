package com.moa.babylanguage

/** Explicit policy: billing is consulted at execution time, never inferred from a UI flag. */
internal enum class AccessFeature(val requiresPass: Boolean) {
    AUDIO(false), MANUAL_INPUT(false), EDIT_SAVE_DRAFT(false), RECORDS(false),
    PNG_SHARE(false), JSON_BACKUP_RESTORE(false), CSV_EXPORT(false),
    WEEKLY_SUMMARY(false), ALBUM_PREVIEW(false),
    CAMERA(true), STORED_VIDEO(true), PDF(true)
}

internal class AccessPolicy(private val hasCurrentEntitlement: () -> Boolean) {
    fun canStart(feature: AccessFeature): Boolean =
        !feature.requiresPass || hasCurrentEntitlement()
}

/** Main-thread owned generation. Once disposed or denied, late callbacks cannot revive it. */
internal class MediaAccessSession(
    private val policy: AccessPolicy,
    private val feature: AccessFeature
) {
    private var closed = false
    val isClosed: Boolean get() = closed
    fun isCurrent(): Boolean {
        if (closed) return false
        if (!policy.canStart(feature)) { closed = true; return false }
        return true
    }
    fun check(onDenied: () -> Unit): Boolean {
        // A late callback from a disposed generation must not redirect a newer screen.
        if (closed) return false
        if (isCurrent()) return true
        onDenied()
        return false
    }
    fun close() { closed = true }
}
