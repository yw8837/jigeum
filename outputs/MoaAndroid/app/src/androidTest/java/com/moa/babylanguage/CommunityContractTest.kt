package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test

class CommunityContractTest {
    @Test fun originRejectsUnsafeConfiguration() {
        listOf("http://example.com", "https://user@example.com", "https://example.com:8443", "https://example.com/path", "https://example.com?x=1", "file:///tmp/a").forEach {
            assertNull(it, CommunityContract.origin(it))
        }
        assertEquals("https://example.com", CommunityContract.origin("https://example.com/"))
    }
    @Test fun draftCleanupPreservesActiveFreshAndUnrelatedFiles() {
        val base = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = java.io.File(base.cacheDir, "community-cleanup-test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val context = object : android.content.ContextWrapper(base) { override fun getCacheDir() = root }
        val exports = java.io.File(root, "exports").apply { mkdirs() }
        val now = System.currentTimeMillis()
        fun draft(old: Boolean) = java.io.File(exports, "community-${java.util.UUID.randomUUID()}.jpg").apply {
            writeText("synthetic only")
            if (old) setLastModified(now - 48 * 60 * 60 * 1000L)
        }
        try {
            val expired = draft(true); val active = draft(true); val fresh = draft(false)
            val unrelated = java.io.File(exports, "personal.jpg").apply { writeText("not owned"); setLastModified(1) }
            CommunitySharing.cleanup(context, active, now)
            assertFalse(expired.exists()); assertTrue(active.exists()); assertTrue(fresh.exists()); assertTrue(unrelated.exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun navigationStaysOnExactHttpsOrigin() {
        assertTrue(CommunityContract.allows("https://example.com", "https://example.com/#compose"))
        listOf("https://example.com.evil.test/", "https://evil.test@example.com/", "http://example.com/", "javascript:alert(1)", "file:///data/private").forEach {
            assertFalse(it, CommunityContract.allows("https://example.com", it))
        }
    }
}
