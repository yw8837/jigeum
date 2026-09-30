package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test

class AccessPolicyTest {
    @Test fun freeFeaturesNeverConsultPaidEntitlement() {
        val policy = AccessPolicy { error("Free work must not check billing") }
        AccessFeature.entries.filterNot { it.requiresPass }.forEach { assertTrue(policy.canStart(it)) }
    }

    @Test fun allPaidFeaturesRecheckCurrentEntitlement() {
        var current = true
        val policy = AccessPolicy { current }
        listOf(AccessFeature.CAMERA, AccessFeature.STORED_VIDEO, AccessFeature.PDF).forEach {
            assertTrue(policy.canStart(it))
        }
        current = false
        listOf(AccessFeature.CAMERA, AccessFeature.STORED_VIDEO, AccessFeature.PDF).forEach {
            assertFalse(policy.canStart(it))
        }
        assertTrue(policy.canStart(AccessFeature.EDIT_SAVE_DRAFT))
    }

    @Test fun expiredAndDisposedMediaSessionsRejectLateCallbacks() {
        var current = true
        val session = MediaAccessSession(AccessPolicy { current }, AccessFeature.STORED_VIDEO)
        assertTrue(session.isCurrent())
        current = false
        assertFalse(session.isCurrent())
        current = true
        session.close()
        assertTrue(session.isClosed)
        assertFalse(session.isCurrent())
        assertTrue(MediaAccessSession(AccessPolicy { current }, AccessFeature.STORED_VIDEO).isCurrent())
    }

    @Test fun oldCallbacksCannotRedirectNewScreen() {
        var redirects = 0
        val disposed = MediaAccessSession(AccessPolicy { true }, AccessFeature.CAMERA)
        disposed.close()
        assertFalse(disposed.check { redirects++ })
        assertEquals(0, redirects)
        val expired = MediaAccessSession(AccessPolicy { false }, AccessFeature.STORED_VIDEO)
        assertFalse(expired.check { redirects++ })
        assertFalse(expired.check { redirects++ })
        assertEquals(1, redirects)
    }

    @Test fun denialPermanentlyInvalidatesThisMediaGeneration() {
        var current = false
        val session = MediaAccessSession(AccessPolicy { current }, AccessFeature.CAMERA)
        assertFalse(session.isCurrent())
        current = true
        assertFalse(session.isCurrent())
    }
}
