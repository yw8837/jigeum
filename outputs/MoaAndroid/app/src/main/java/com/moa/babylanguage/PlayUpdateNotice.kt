package com.moa.babylanguage

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.SystemClock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Play decides track/country/account eligibility. Never compare a website's advertised
 * version or a review-pending release with the locally installed version. */
internal class PlayUpdateNotice(
    private val manager: AppUpdateManager,
    private val preferences: SharedPreferences,
    private val installedVersion: Int,
    private val now: () -> Long = System::currentTimeMillis,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime
) {
    constructor(context: Context) : this(
        AppUpdateManagerFactory.create(context.applicationContext),
        context.getSharedPreferences("play-update-notice", Context.MODE_PRIVATE),
        BuildConfig.VERSION_CODE
    )

    private val mutableVersion = MutableStateFlow<Int?>(null)
    val version = mutableVersion.asStateFlow()
    private var closed = false
    private var checking = false
    private var lastCheck: Long? = null

    fun check() {
        if (closed || checking) return
        val tick = elapsed()
        if (lastCheck?.let { tick - it in 0 until 300_000 } == true) return
        lastCheck = tick
        checking = true
        // Failed/offline/non-Play installations remain fully usable.
        try {
            manager.appUpdateInfo.addOnSuccessListener { info ->
                if (!closed) mutableVersion.value = info.availableVersionCode().takeIf { candidate ->
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        shouldPrompt(installedVersion, candidate,
                            preferences.getInt("dismissed-version", -1),
                            preferences.getLong("dismissed-at", 0), now())
                }
            }.addOnFailureListener { if (!closed) mutableVersion.value = null }
                .addOnCompleteListener { checking = false }
        } catch (_: RuntimeException) {
            checking = false
            mutableVersion.value = null
        }
    }

    fun later() {
        val candidate = mutableVersion.value ?: return
        preferences.edit().putInt("dismissed-version", candidate)
            .putLong("dismissed-at", now()).apply()
        mutableVersion.value = null
    }

    fun close() { closed = true; mutableVersion.value = null }

    companion object {
        private const val REMIND_AFTER_MS = 24 * 60 * 60 * 1000L
        fun shouldPrompt(installed: Int, available: Int, dismissed: Int, dismissedAt: Long, now: Long): Boolean =
            available > installed && (available != dismissed || now < dismissedAt || now - dismissedAt >= REMIND_AFTER_MS)

        fun openStore(context: Context): Boolean {
            val intents = listOf(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.moa.babylanguage"))
                    .setPackage("com.android.vending"),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.moa.babylanguage"))
            )
            for (intent in intents) try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) { /* Try the HTTPS listing. */ }
              catch (_: SecurityException) { /* A restricted profile may disallow this intent. */ }
            return false
        }
    }
}

@Composable
internal fun StartupUpdatePrompt(notice: PlayUpdateNotice?, eligible: Boolean, onUpdate: () -> Boolean,
                                 onFailure: () -> Unit) {
    if (notice == null) return
    val version by notice.version.collectAsState()
    // Wait for the home screen; never interrupt capture, an unsaved result or checkout.
    if (eligible && version != null) AlertDialog(
        modifier = Modifier.testTag("play-update-dialog"),
        onDismissRequest = notice::later,
        title = { Text("새로운 아가톡이 있어요") },
        text = { Text("Google Play에서 새 버전을 받을 수 있어요. 업데이트 내용은 스토어에서 확인해 주세요. 지금은 나중에 하고 계속 사용할 수도 있어요.") },
        confirmButton = { TextButton(onClick = {
            if (onUpdate()) notice.later() else onFailure()
        }) { Text("업데이트") } },
        dismissButton = { TextButton(onClick = notice::later) { Text("나중에") } }
    )
}
