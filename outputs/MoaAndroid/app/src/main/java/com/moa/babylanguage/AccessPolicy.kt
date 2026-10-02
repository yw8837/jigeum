package com.moa.babylanguage

import android.app.Activity
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

internal interface AccessBilling {
    val state: StateFlow<BillingUiState>
    fun hasCurrentEntitlement(): Boolean
    fun refresh()
    fun restore()
    fun purchase(activity: Activity, plan: BillingPlan)
    fun activateReviewAccess(code: String) = Unit
    fun deactivateReviewAccess() = Unit
}

internal class PlayAccessBilling(private val manager: BillingManager) : AccessBilling {
    override val state get() = manager.state
    override fun hasCurrentEntitlement() = manager.hasCurrentEntitlement()
    override fun refresh() = manager.refresh()
    override fun restore() = manager.restore()
    override fun purchase(activity: Activity, plan: BillingPlan) = manager.purchase(activity, plan)
    override fun activateReviewAccess(code: String) = manager.activateReviewAccess(code)
    override fun deactivateReviewAccess() = manager.deactivateReviewAccess()
}

/** All features use one policy. Free records and drafts never depend on their input source. */
internal enum class AppFeature(val requiresPass: Boolean) {
    SOUND_INPUT(false), CAPTURE(false), VIDEO_IMPORT(false), PHRASE_EDIT(false), RECORDS(false),
    PNG_SHARE(false), BACKUP(false), WEEKLY_SUMMARY(false), ALBUM_PREVIEW(false),
    VISUAL_ANALYSIS(true), PDF(true)
}

internal class MediaAccessSession internal constructor(val feature: AppFeature, val generation: Long)

/**
 * Called on Main. The checker is BillingManager.hasCurrentEntitlement, never a UI snapshot.
 * Explicit sideload preview / Play closed-test beta open features without claiming a purchase.
 * Release always supplies false; nothing is persisted when switching distributions.
 */
internal class AccessPolicy(
    private val currentEntitlement: () -> Boolean,
    private val testDistribution: Boolean
) {
    constructor(currentEntitlement: () -> Boolean) : this(currentEntitlement, false)
    private var generation = 0L

    fun canUse(feature: AppFeature): Boolean = !feature.requiresPass || testDistribution || currentEntitlement()

    fun beginMedia(feature: AppFeature): MediaAccessSession? {
        require(feature == AppFeature.VISUAL_ANALYSIS)
        invalidateMedia()
        return if (canUse(feature)) MediaAccessSession(feature, generation) else null
    }

    fun isCurrent(session: MediaAccessSession): Boolean {
        if (session.generation != generation) return false
        if (!canUse(session.feature)) {
            invalidateMedia()
            return false
        }
        return true
    }

    fun invalidateMedia() { generation++ }
}
