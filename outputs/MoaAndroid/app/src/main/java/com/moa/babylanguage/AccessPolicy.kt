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

/** An internal composition seam, not a persisted setting or exported runtime switch. */
internal interface MediaScreens {
    @Composable fun Camera(authorize: () -> Boolean, onBack: () -> Unit, onTranslate: (CameraObservation) -> Unit)
    @Composable fun Video(authorize: () -> Boolean, onBack: () -> Unit, onTranslate: (ImportedVideoObservation) -> Unit)
}

internal object DeviceMediaScreens : MediaScreens {
    @Composable override fun Camera(authorize: () -> Boolean, onBack: () -> Unit, onTranslate: (CameraObservation) -> Unit) {
        CameraScanScreen(onBack = onBack, onTranslate = onTranslate, authorize = authorize)
    }
    @Composable override fun Video(authorize: () -> Boolean, onBack: () -> Unit, onTranslate: (ImportedVideoObservation) -> Unit) {
        VideoImportScreen(onBack = onBack, onTranslate = onTranslate, authorize = authorize)
    }
}

/** All features use one policy. Free records and drafts never depend on their input source. */
internal enum class AppFeature(val requiresPass: Boolean) {
    SOUND_INPUT(false), MANUAL_INPUT(false), PHRASE_EDIT(false), RECORDS(false),
    PNG_SHARE(false), BACKUP(false), WEEKLY_SUMMARY(false), ALBUM_PREVIEW(false),
    CAMERA(true), VIDEO_IMPORT(true), PDF(true)
}

internal class MediaAccessSession internal constructor(val feature: AppFeature, val generation: Long)

/**
 * Called on Main. The checker is BillingManager.hasCurrentEntitlement, never a UI snapshot.
 * It is injected as a function so androidTest can exercise expiry without a release bypass.
 */
internal class AccessPolicy(private val currentEntitlement: () -> Boolean) {
    private var generation = 0L

    fun canUse(feature: AppFeature): Boolean = !feature.requiresPass || currentEntitlement()

    fun beginMedia(feature: AppFeature): MediaAccessSession? {
        require(feature == AppFeature.CAMERA || feature == AppFeature.VIDEO_IMPORT)
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
