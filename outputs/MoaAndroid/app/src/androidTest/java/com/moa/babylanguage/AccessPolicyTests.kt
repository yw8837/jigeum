package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test

class AccessPolicyTests {
    @Test fun everyFreeFeatureWorksWithoutConsultingBilling() {
        val policy = AccessPolicy { error("Free features must not consult billing") }
        AppFeature.entries.filterNot { it.requiresPass }.forEach { assertTrue(policy.canUse(it)) }
    }

    @Test fun oneVerifiedPassUnlocksVisualAnalysisAndPdf() {
        var verified = false
        val policy = AccessPolicy { verified }
        val paid = AppFeature.entries.filter { it.requiresPass }
        assertEquals(setOf(AppFeature.VISUAL_ANALYSIS, AppFeature.PDF), paid.toSet())
        paid.forEach { assertFalse(policy.canUse(it)) }
        verified = true
        paid.forEach { assertTrue(policy.canUse(it)) }
    }

    @Test fun expiredMediaCannotDeliverLateResultsOrReviveAfterRestore() {
        var verified = true
        val policy = AccessPolicy { verified }
        val old = policy.beginMedia(AppFeature.VISUAL_ANALYSIS)!!
        assertTrue(policy.isCurrent(old))
        verified = false
        assertFalse(policy.isCurrent(old))
        assertNull(policy.beginMedia(AppFeature.VISUAL_ANALYSIS))
        assertTrue(policy.canUse(AppFeature.PHRASE_EDIT))
        assertTrue(policy.canUse(AppFeature.RECORDS))
        verified = true
        assertFalse(policy.isCurrent(old))
        assertTrue(policy.isCurrent(policy.beginMedia(AppFeature.VISUAL_ANALYSIS)!!))
    }

    @Test fun leavingOrStartingAnotherMediaSessionInvalidatesOldCallbacks() {
        val policy = AccessPolicy { true }
        val camera = policy.beginMedia(AppFeature.VISUAL_ANALYSIS)!!
        val video = policy.beginMedia(AppFeature.VISUAL_ANALYSIS)!!
        assertFalse(policy.isCurrent(camera))
        assertTrue(policy.isCurrent(video))
        policy.invalidateMedia()
        assertFalse(policy.isCurrent(video))
    }

    @Test fun newPaidOperationChecksLiveEntitlementEveryTime() {
        var verified = true
        var checks = 0
        val policy = AccessPolicy { checks++; verified }
        assertTrue(policy.canUse(AppFeature.PDF))
        verified = false
        assertFalse(policy.canUse(AppFeature.PDF))
        assertNull(policy.beginMedia(AppFeature.VISUAL_ANALYSIS))
        assertEquals(3, checks)
        assertTrue(policy.canUse(AppFeature.PNG_SHARE))
        assertTrue(policy.canUse(AppFeature.BACKUP))
    }
}
