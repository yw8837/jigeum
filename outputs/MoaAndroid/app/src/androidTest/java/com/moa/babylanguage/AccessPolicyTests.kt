package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test

class AccessPolicyTests {
    @Test fun everyFreeFeatureWorksWithoutConsultingBilling() {
        val policy = AccessPolicy { error("Free features must not consult billing") }
        AppFeature.entries.filterNot { it.requiresPass }.forEach { assertTrue(policy.canUse(it)) }
    }

    @Test fun oneVerifiedPassUnlocksExactlyThreePaidFeatures() {
        var verified = false
        val policy = AccessPolicy { verified }
        val paid = AppFeature.entries.filter { it.requiresPass }
        assertEquals(setOf(AppFeature.CAMERA, AppFeature.VIDEO_IMPORT, AppFeature.PDF), paid.toSet())
        paid.forEach { assertFalse(policy.canUse(it)) }
        verified = true
        paid.forEach { assertTrue(policy.canUse(it)) }
    }

    @Test fun expiredMediaCannotDeliverLateResultsOrReviveAfterRestore() {
        var verified = true
        val policy = AccessPolicy { verified }
        val old = policy.beginMedia(AppFeature.CAMERA)!!
        assertTrue(policy.isCurrent(old))
        verified = false
        assertFalse(policy.isCurrent(old))
        assertNull(policy.beginMedia(AppFeature.VIDEO_IMPORT))
        assertTrue(policy.canUse(AppFeature.PHRASE_EDIT))
        assertTrue(policy.canUse(AppFeature.RECORDS))
        verified = true
        assertFalse(policy.isCurrent(old))
        assertTrue(policy.isCurrent(policy.beginMedia(AppFeature.CAMERA)!!))
    }

    @Test fun leavingOrStartingAnotherMediaSessionInvalidatesOldCallbacks() {
        val policy = AccessPolicy { true }
        val camera = policy.beginMedia(AppFeature.CAMERA)!!
        val video = policy.beginMedia(AppFeature.VIDEO_IMPORT)!!
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
        assertNull(policy.beginMedia(AppFeature.CAMERA))
        assertEquals(3, checks)
        assertTrue(policy.canUse(AppFeature.PNG_SHARE))
        assertTrue(policy.canUse(AppFeature.BACKUP))
    }
}
