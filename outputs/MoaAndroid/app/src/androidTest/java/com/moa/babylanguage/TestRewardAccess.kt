package com.moa.babylanguage

import kotlinx.coroutines.flow.MutableStateFlow

/** Only the ad completion is synthetic; operation accounting uses the production ledger. */
internal class TestRewardAccess : RewardAccess {
    private val storage = object : RewardStorage {
        var value = RewardSnapshot()
        override fun read() = value
        override fun write(value: RewardSnapshot): Boolean { this.value = value; return true }
    }
    private val ledger = RewardLedger(storage)
    override val state = MutableStateFlow(RewardUiState(enabled = true))
    private fun refresh() { state.value = state.value.copy(ready = RewardKind.entries.filter(::available).toSet()) }
    fun earn(kind: RewardKind) { check(ledger.grant(kind, checkNotNull(ledger.beginAd(kind)))); refresh() }
    override fun available(kind: RewardKind) = ledger.available(kind)
    override fun active(lease: RewardLease) = ledger.active(lease)
    override fun reserve(kind: RewardKind) = ledger.reserve(kind).also { refresh() }
    override fun finish(lease: RewardLease, succeeded: Boolean) = ledger.finish(lease, succeeded).also { refresh() }
    override fun watch(kind: RewardKind) = Unit
    override fun privacyOptions() = Unit
    override fun cancelAd() = Unit
}
