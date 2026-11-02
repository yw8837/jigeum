package com.moa.babylanguage

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

internal class PreferenceRewardStorage(context: Context) : RewardStorage {
    private val preferences = context.getSharedPreferences("rewarded-operations-v1", Context.MODE_PRIVATE)
    override fun read(): RewardSnapshot {
        val raw = preferences.getString("state", null) ?: return RewardSnapshot()
        return runCatching {
            val json = JSONObject(raw)
            check(json.getInt("version") == 1)
            val credits = RewardKind.entries.filter { json.optBoolean("credit-${it.name}") }.toSet()
            fun ids(prefix: String) = RewardKind.entries.mapNotNull { kind ->
                json.optString("$prefix-${kind.name}").takeIf { it.isNotEmpty() }?.let { kind to it }
            }.toMap()
            RewardSnapshot(credits, ids("pending"), ids("reserved"))
        }.getOrElse { throw IllegalStateException("광고 이용 기회를 읽지 못했어요. 앱을 다시 열어 주세요.") }
    }
    @SuppressLint("ApplySharedPref") // A grant must be durable before it is displayed or consumed.
    override fun write(value: RewardSnapshot): Boolean {
        val json = JSONObject().put("version", 1)
        RewardKind.entries.forEach { kind ->
            json.put("credit-${kind.name}", kind in value.credits)
            value.pending[kind]?.let { json.put("pending-${kind.name}", it) }
            value.reserved[kind]?.let { json.put("reserved-${kind.name}", it) }
        }
        return preferences.edit().putString("state", json.toString()).commit()
    }
}

/** Opt-in rewarded ads. No recording, image, caption, or record is passed to the SDK. */
internal class RewardedAds(private val activity: Activity) : RewardAccess {
    private val mutable = MutableStateFlow(RewardUiState(enabled = BuildConfig.REWARDED_ADS_ENABLED))
    override val state: StateFlow<RewardUiState> = mutable
    private val consent = UserMessagingPlatform.getConsentInformation(activity)
    private var ledger: RewardLedger? = null
    private var closed = false
    private var initialized = false
    private var attempt = 0L
    private var request: Pair<RewardKind, String>? = null

    init {
        runCatching { ledger = RewardLedger(PreferenceRewardStorage(activity.applicationContext)) }
            .onFailure { mutable.value = mutable.value.copy(enabled = false, message = it.message) }
        refresh()
        if (mutable.value.enabled) {
            mutable.value = mutable.value.copy(busy = true)
            consent.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
                if (!closed) refresh(busy = false)
            }, { if (!closed) refresh(busy = false) })
        }
    }

    private fun refresh(busy: Boolean = mutable.value.busy, message: String? = mutable.value.message) {
        mutable.value = mutable.value.copy(busy = busy, message = message,
            ready = RewardKind.entries.filter { ledger?.available(it) == true }.toSet(),
            privacyRequired = consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED)
    }

    override fun available(kind: RewardKind) = ledger?.available(kind) == true
    override fun active(lease: RewardLease) = ledger?.active(lease) == true
    override fun reserve(kind: RewardKind): RewardLease? = try {
        ledger?.reserve(kind).also { refresh() }
    } catch (error: Exception) { refresh(message = error.message); null }
    override fun finish(lease: RewardLease, succeeded: Boolean): Boolean = try {
        (ledger?.finish(lease, succeeded) == true).also { refresh() }
    } catch (error: Exception) { refresh(message = error.message); false }

    override fun watch(kind: RewardKind) {
        if (closed || !mutable.value.enabled || mutable.value.busy || available(kind) || activity.isFinishing) return
        val run = ++attempt
        refresh(busy = true, message = "광고 이용 안내를 확인하고 있어요.")
        consent.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            if (!closed && run == attempt) UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                if (!closed && run == attempt) {
                    if (consent.canRequestAds()) initializeAndLoad(kind, run)
                    else refresh(busy = false, message = if (error != null) "광고 안내를 불러오지 못했어요. 잠시 후 다시 시도해 주세요." else "현재 광고를 요청할 수 없어요. 무료 기능은 계속 이용할 수 있어요.")
                }
            }
        }, {
            if (!closed && run == attempt) {
                if (consent.canRequestAds()) initializeAndLoad(kind, run)
                else refresh(busy = false, message = "광고 연결을 확인하지 못했어요. 무료 기능은 계속 이용할 수 있어요.")
            }
        })
    }

    private fun initializeAndLoad(kind: RewardKind, run: Long) {
        if (run != attempt) return
        if (closed || !consent.canRequestAds()) { refresh(busy = false); return }
        if (initialized) { load(kind, run); return }
        MobileAds.setRequestConfiguration(RequestConfiguration.Builder()
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G).build())
        MobileAds.initialize(activity.applicationContext) {
            activity.runOnUiThread { if (!closed && run == attempt) { initialized = true; load(kind, run) } }
        }
    }

    private fun load(kind: RewardKind, run: Long) {
        if (run != attempt) return
        if (closed || activity.isFinishing || !consent.canRequestAds()) { refresh(busy = false); return }
        val id = try { ledger?.beginAd(kind) } catch (error: Exception) {
            refresh(busy = false, message = error.message); return
        }
        if (id == null) { refresh(busy = false); return }
        request = kind to id
        refresh(message = "광고를 불러오고 있어요.")
        try { RewardedAd.load(activity, BuildConfig.REWARDED_AD_UNIT_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(error: LoadAdError) {
                end(kind, id, "지금은 준비된 광고가 없어요. 잠시 후 다시 시도하거나 무료 기능을 이용해 주세요.")
            }
            override fun onAdLoaded(ad: RewardedAd) {
                if (closed || request != (kind to id) || activity.isFinishing || activity.isDestroyed) {
                    end(kind, id, null); return
                }
                var earned = false
                var grantFailure: String? = null
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        end(kind, id, grantFailure ?: if (earned) "${if (kind == RewardKind.ANALYSIS) "분석" else "PDF"} 1회가 준비됐어요. 원래 화면에서 시작해 주세요."
                            else "광고를 완료하지 않아 이용 기회가 추가되지 않았어요.")
                    }
                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        end(kind, id, "광고를 표시하지 못했어요. 무료 기능은 계속 이용할 수 있어요.")
                    }
                }
                try {
                    ad.show(activity) {
                        if (!closed && request == (kind to id) && !earned) {
                            earned = try { ledger?.grant(kind, id) == true }
                            catch (error: Exception) { grantFailure = error.message ?: "이용 기회를 보관하지 못했어요."; refresh(message = grantFailure); false }
                            refresh()
                        }
                    }
                } catch (_: Exception) { end(kind, id, "광고를 표시하지 못했어요. 다시 시도해 주세요.") }
            }
        }) } catch (_: Exception) { end(kind, id, "광고를 불러오지 못했어요. 무료 기능은 계속 이용할 수 있어요.") }
    }

    private fun end(kind: RewardKind, id: String, message: String?) {
        if (request != (kind to id)) return
        request = null
        try { ledger?.cancelAd(kind, id); if (!closed) refresh(busy = false, message = message) }
        catch (error: Exception) { if (!closed) refresh(busy = false, message = error.message) }
    }

    override fun privacyOptions() {
        if (closed || mutable.value.busy || !mutable.value.privacyRequired) return
        refresh(busy = true)
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (!closed) refresh(busy = false, message = if (error == null) "광고 개인정보 설정을 확인했어요." else "광고 개인정보 설정을 열지 못했어요. 다시 시도해 주세요.")
        }
    }

    override fun cancelAd() {
        attempt++
        request?.let { (kind, id) -> end(kind, id, null) }
        if (!closed) refresh(busy = false)
    }

    fun close() { closed = true; cancelAd() }
}
