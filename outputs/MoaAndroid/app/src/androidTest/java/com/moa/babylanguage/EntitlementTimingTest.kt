package com.moa.babylanguage

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** No fake Play entitlement is enabled in production; this tests only the pure time policy. */
@RunWith(AndroidJUnit4::class)
class EntitlementTimingTest {
    private val wall = 1_800_000_000_000L
    private val elapsed = 20_000_000L
    private val hour = 3_600_000L
    private val anchor = EntitlementClockAnchor(ENTITLEMENT_CACHE_VERSION, wall, elapsed, 7)

    private fun remaining(
        issued: Long = wall - hour,
        expiry: Long = wall + hour,
        subscriptionExpiry: Long = expiry,
        evidence: EntitlementClockAnchor? = anchor,
        nowWall: Long = wall,
        nowElapsed: Long = elapsed,
        boot: Int = 7
    ) = remainingEntitlementMs(issued, expiry, subscriptionExpiry, evidence, nowWall, nowElapsed, boot)

    @Test fun normalSameBootReceiptAndElapsedConsumption() {
        assertEquals(hour, remaining())
        assertEquals(hour - 600_000, remaining(nowWall = wall + 600_000, nowElapsed = elapsed + 600_000))
        // The persisted evidence remains usable by a new process on the same boot.
        assertEquals(hour - 600_000, remaining(evidence = anchor.copy(), nowWall = wall + 600_000, nowElapsed = elapsed + 600_000))
    }

    @Test fun wallRollbackWithinSignedWindowCannotExtendElapsedBudget() {
        assertEquals(hour / 2, remaining(nowWall = wall - hour / 2, nowElapsed = elapsed + hour / 2))
        assertNull(remaining(nowWall = wall - hour / 2, nowElapsed = elapsed + hour))
        assertNull(remaining(nowWall = wall - hour / 2, nowElapsed = elapsed + hour * 25))
    }

    @Test fun forwardWallClockOnlyShortensTheAvailableWindow() {
        assertEquals(hour - 600_000, remaining(nowWall = wall + 600_000, nowElapsed = elapsed + 1_000))
        assertNull(remaining(nowWall = wall + hour))
        assertNull(remaining(nowWall = wall + hour * 25))
        assertNull(remaining(nowWall = wall - hour - 300_001))
    }

    @Test fun rebootAndUnknownBootRequireOnlineVerification() {
        assertNull(remaining(boot = 8))
        assertNull(remaining(boot = 6))
        assertNull(remaining(boot = -1))
        assertNull(remaining(evidence = anchor.copy(bootCount = -1)))
        assertNull(remaining(nowElapsed = 1_000, boot = 8))
    }

    @Test fun legacyMissingOrFutureVersionEvidenceFailsClosed() {
        assertNull(remaining(evidence = null))
        assertNull(remaining(evidence = anchor.copy(version = -1)))
        assertNull(remaining(evidence = anchor.copy(version = 0)))
        assertNull(remaining(evidence = anchor.copy(version = ENTITLEMENT_CACHE_VERSION + 1)))
        assertNull(remaining(evidence = anchor.copy(receivedAtWallMs = -1)))
        assertNull(remaining(evidence = anchor.copy(receivedAtElapsedMs = -1)))
    }

    @Test fun negativeElapsedAndEvidenceFromTheFutureFailClosed() {
        assertNull(remaining(nowElapsed = -1))
        assertNull(remaining(nowElapsed = elapsed - 1))
        assertNull(remaining(evidence = anchor.copy(receivedAtElapsedMs = elapsed + 1)))
        assertNull(remaining(evidence = anchor.copy(receivedAtWallMs = wall + hour)))
        assertNull(remaining(evidence = anchor.copy(receivedAtWallMs = wall - hour - 300_001)))
    }

    @Test fun expiryBoundaryIsExclusiveForBothClocks() {
        assertEquals(1L, remaining(nowWall = wall - 1_000, nowElapsed = elapsed + hour - 1))
        assertNull(remaining(nowWall = wall - 1_000, nowElapsed = elapsed + hour))
        assertEquals(1L, remaining(nowWall = wall + hour - 1, nowElapsed = elapsed))
        assertNull(remaining(nowWall = wall + hour, nowElapsed = elapsed))
    }

    @Test fun corruptSignedTimeBoundsAreRejected() {
        assertNull(remaining(issued = -1))
        assertNull(remaining(expiry = wall - hour))
        assertNull(remaining(subscriptionExpiry = wall + hour - 1))
        assertNull(remaining(issued = wall, expiry = wall + 86_400_001))
        assertNull(remaining(nowWall = -1))
        assertNull(remaining(nowElapsed = Long.MAX_VALUE))
    }

    @Test fun acceptedServerClockSkewStillHasAtMost24HoursOfElapsedTime() {
        val issued = wall + 300_000
        val expiry = issued + 86_400_000
        assertEquals(86_400_000L, remaining(issued = issued, expiry = expiry))
        assertEquals(1L, remaining(issued = issued, expiry = expiry, nowWall = wall, nowElapsed = elapsed + 86_400_000 - 1))
        assertNull(remaining(issued = issued, expiry = expiry, nowWall = wall, nowElapsed = elapsed + 86_400_000))
    }

    @Test fun effectiveUiDeadlineUsesElapsedRemainingAfterWallRollback() {
        val rolledBackWall = wall - 600_000
        val remaining = remaining(nowWall = rolledBackWall, nowElapsed = elapsed + 1_800_000)!!
        val uiDeadline = rolledBackWall + remaining
        assertEquals(1_800_000L, uiDeadline - rolledBackWall)
        assertEquals(wall + 1_200_000, uiDeadline)
    }
}
