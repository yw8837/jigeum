package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test

class RewardLedgerTest {
    private class Memory : RewardStorage {
        var value = RewardSnapshot()
        var writable = true
        override fun read() = value
        override fun write(value: RewardSnapshot): Boolean {
            if (!writable) return false
            this.value = value
            return true
        }
    }

    @Test fun oneCompletionCanBeSpentOnlyOnceAndReplayCannotGrantAgain() {
        val ledger = RewardLedger(Memory())
        val id = ledger.beginAd(RewardKind.ANALYSIS)!!
        assertFalse(ledger.grant(RewardKind.ANALYSIS, "wrong"))
        assertTrue(ledger.grant(RewardKind.ANALYSIS, id))
        assertNull(ledger.beginAd(RewardKind.ANALYSIS))
        val lease = ledger.reserve(RewardKind.ANALYSIS)!!
        assertNull(ledger.reserve(RewardKind.ANALYSIS))
        assertTrue(ledger.finish(lease, true))
        assertFalse(ledger.finish(lease, true))
        assertFalse(ledger.grant(RewardKind.ANALYSIS, id))
        assertFalse(ledger.available(RewardKind.ANALYSIS))
    }

    @Test fun cancellationAndFailureReturnTheSameOpportunity() {
        val ledger = RewardLedger(Memory())
        val id = ledger.beginAd(RewardKind.ANALYSIS)!!
        ledger.grant(RewardKind.ANALYSIS, id)
        val first = ledger.reserve(RewardKind.ANALYSIS)!!
        assertTrue(ledger.finish(first, false))
        val next = ledger.reserve(RewardKind.ANALYSIS)!!
        assertFalse(ledger.active(first))
        assertFalse(ledger.finish(first, true))
        assertTrue(ledger.active(next))
        assertTrue(ledger.finish(next, true))
    }

    @Test fun interruptedWorkIsRecoveredButUncompletedAdsAreNotRewarded() {
        val storage = Memory()
        val old = RewardLedger(storage)
        val id = old.beginAd(RewardKind.ANALYSIS)!!
        old.grant(RewardKind.ANALYSIS, id)
        old.reserve(RewardKind.ANALYSIS)
        val incompleteAd = old.beginAd(RewardKind.PDF)!!
        val restored = RewardLedger(storage)
        assertTrue(restored.available(RewardKind.ANALYSIS))
        assertFalse(restored.available(RewardKind.PDF))
        assertFalse(restored.grant(RewardKind.PDF, incompleteAd))
    }

    @Test fun operationKindsAreIndependentAndCancelledAdCannotRewardLater() {
        val ledger = RewardLedger(Memory())
        val id = ledger.beginAd(RewardKind.ANALYSIS)!!
        ledger.cancelAd(RewardKind.ANALYSIS, id)
        assertFalse(ledger.grant(RewardKind.ANALYSIS, id))
        val pdf = ledger.beginAd(RewardKind.PDF)!!
        assertTrue(ledger.grant(RewardKind.PDF, pdf))
        assertNull(ledger.reserve(RewardKind.ANALYSIS))
        assertTrue(ledger.available(RewardKind.PDF))
    }

    @Test fun persistenceFailureCannotSilentlySpendOrCreateAccess() {
        val storage = Memory()
        val ledger = RewardLedger(storage)
        val id = ledger.beginAd(RewardKind.ANALYSIS)!!
        storage.writable = false
        assertThrows(IllegalStateException::class.java) { ledger.grant(RewardKind.ANALYSIS, id) }
        assertFalse(ledger.available(RewardKind.ANALYSIS))
        storage.writable = true
        assertTrue(ledger.grant(RewardKind.ANALYSIS, id))
        val lease = ledger.reserve(RewardKind.ANALYSIS)!!
        storage.writable = false
        assertThrows(IllegalStateException::class.java) { ledger.finish(lease, true) }
        assertTrue(ledger.active(lease))
    }
}
