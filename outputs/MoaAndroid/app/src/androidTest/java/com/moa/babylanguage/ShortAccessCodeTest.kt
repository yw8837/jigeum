package com.moa.babylanguage

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.util.UUID

class ShortAccessCodeTest {
    @Test fun humanInputNormalizesWithoutChangingLegacyOrReviewCredentials() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("TESTHALF", normalizeAccessCode(" test-half "))
            assertEquals("ABC234DEF5", normalizeAccessCode("abc23 4-def5"))
            val legacy = "CaseSensitive-Legacy-credential-0123456789"
            assertEquals(legacy, normalizeAccessCode(" $legacy "))
            assertTrue(validAccessCode(normalizeAccessCode("test-half")))
            assertFalse(validReviewCode("TESTHALF"))
            for (invalid in listOf("", "SHORT77", "A".repeat(13), "TEST\nHALF", "테스트코드1234", "a".repeat(31))) {
                assertFalse(invalid, validAccessCode(invalid))
            }
        } finally { Locale.setDefault(previous) }
    }

    @Test fun shortCredentialSurvivesReopenWhileReviewModeRemainsStrictAndLegacyStillDecrypts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "short-access-test-${UUID.randomUUID()}"
        val id = UUID.randomUUID().toString()
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val access = ReviewCredentialStore(context, id, prefs, accessCodeMode = true)
        val review = ReviewCredentialStore(context, id, prefs)
        val otherDevice = ReviewCredentialStore(context, UUID.randomUUID().toString(), prefs, accessCodeMode = true)
        try {
            val legacy = "CaseSensitive-Legacy-credential-0123456789"
            prefs.edit().putString("credential", review.encrypt(legacy)).commit()
            assertEquals(legacy, access.read())
            for (code in listOf("TESTHALF", "ABC234DEF5")) {
                val encrypted = access.encrypt(code)
                assertFalse(encrypted.contains(code))
                assertTrue(prefs.edit().putString("credential", encrypted).commit())
                assertEquals(code, ReviewCredentialStore(context, id, prefs, accessCodeMode = true).read())
                assertNull(review.read())
                assertNull(otherDevice.read())
                try { review.encrypt(code); fail("Review mode accepted a short credential") }
                catch (_: IllegalArgumentException) { }
            }
            access.deleteKey()
            assertNull(access.read())
        } finally { access.deleteKey(); otherDevice.deleteKey(); context.deleteSharedPreferences(name) }
    }
}
