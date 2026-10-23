package com.moa.babylanguage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal data class RewardUiState(
    val enabled: Boolean = false,
    val busy: Boolean = false,
    val ready: Set<RewardKind> = emptySet(),
    val privacyRequired: Boolean = false,
    val message: String? = null
)

internal interface RewardAccess {
    val state: StateFlow<RewardUiState>
    fun available(kind: RewardKind): Boolean
    fun reserve(kind: RewardKind): RewardLease?
    fun active(lease: RewardLease): Boolean
    fun finish(lease: RewardLease, succeeded: Boolean): Boolean
    fun watch(kind: RewardKind)
    fun privacyOptions()
    fun cancelAd()
}

/** Default for isolated UI tests and full-access test distributions: no SDK or network. */
internal object NoRewardAccess : RewardAccess {
    override val state = MutableStateFlow(RewardUiState())
    override fun available(kind: RewardKind) = false
    override fun reserve(kind: RewardKind): RewardLease? = null
    override fun active(lease: RewardLease) = false
    override fun finish(lease: RewardLease, succeeded: Boolean) = false
    override fun watch(kind: RewardKind) = Unit
    override fun privacyOptions() = Unit
    override fun cancelAd() = Unit
}
