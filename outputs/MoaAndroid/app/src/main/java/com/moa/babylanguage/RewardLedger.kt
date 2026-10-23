package com.moa.babylanguage

import java.util.UUID

internal enum class RewardKind { ANALYSIS, PDF }
internal data class RewardLease(val kind: RewardKind, val id: String)
internal data class RewardSnapshot(
    val credits: Set<RewardKind> = emptySet(),
    val pending: Map<RewardKind, String> = emptyMap(),
    val reserved: Map<RewardKind, String> = emptyMap()
)
internal interface RewardStorage {
    fun read(): RewardSnapshot
    fun write(value: RewardSnapshot): Boolean
}

/** One durable opportunity per operation kind. Never represents a Play purchase. */
internal class RewardLedger(private val storage: RewardStorage) {
    private var value = storage.read()

    init {
        // An interrupted operation produced no durable result acknowledgment. Return its opportunity.
        if (value.reserved.isNotEmpty() || value.pending.isNotEmpty())
            save(RewardSnapshot(credits = value.credits + value.reserved.keys))
    }

    private fun save(next: RewardSnapshot) {
        check(storage.write(next)) { "광고 이용 기회를 보관하지 못했어요. 저장 공간을 확인해 주세요." }
        value = next
    }

    @Synchronized fun available(kind: RewardKind) = kind in value.credits
    @Synchronized fun active(lease: RewardLease) = value.reserved[lease.kind] == lease.id

    @Synchronized fun beginAd(kind: RewardKind): String? {
        if (kind in value.credits || kind in value.reserved || kind in value.pending) return null
        val id = UUID.randomUUID().toString()
        save(value.copy(pending = value.pending + (kind to id)))
        return id
    }

    @Synchronized fun grant(kind: RewardKind, id: String): Boolean {
        if (value.pending[kind] != id) return false
        save(value.copy(credits = value.credits + kind, pending = value.pending - kind))
        return true
    }

    @Synchronized fun cancelAd(kind: RewardKind, id: String) {
        if (value.pending[kind] == id) save(value.copy(pending = value.pending - kind))
    }

    @Synchronized fun reserve(kind: RewardKind): RewardLease? {
        if (kind !in value.credits || kind in value.reserved) return null
        val lease = RewardLease(kind, UUID.randomUUID().toString())
        save(value.copy(credits = value.credits - kind, reserved = value.reserved + (kind to lease.id)))
        return lease
    }

    @Synchronized fun finish(lease: RewardLease, succeeded: Boolean): Boolean {
        if (!active(lease)) return false
        save(value.copy(reserved = value.reserved - lease.kind,
            credits = if (succeeded) value.credits else value.credits + lease.kind))
        return true
    }
}
