package com.moa.babylanguage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhraseEngineTests {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferencesName = "phrase_engine_test"
    private val engine: PhraseEngine get() = PhraseEngine(context, preferencesName)

    @Before fun setUp() { engine.reset() }
    @After fun tearDown() { engine.reset() }

    @Test fun everyBucketReturnsThreeDistinctFirstPersonLines() {
        val sounds = listOf("울음", "웃음", "옹알이", "기타 소리", "영상 장면")
        val contexts = listOf("수유 전", "수유 후", "잠들기 전", "잠에서 깬 후", "놀이 중", "기타 상황")
        for (sound in sounds) for (situation in contexts) {
            val seen = mutableSetOf<String>()
            repeat(4) { turn ->
                val result = engine.candidates(sound, situation, "$sound-$situation-$turn")
                assertEquals("$sound / $situation", 3, result.size)
                assertEquals("$sound / $situation", 3, result.distinct().size)
                assertTrue("$sound / $situation", result.all { it.isNotBlank() && it.length < 160 })
                seen.addAll(result)
            }
            assertTrue("$sound / $situation has too little variety", seen.size >= 9)
        }
    }

    @Test fun sameSessionAndBucketSurviveNewInstance() {
        val first = engine.candidates("옹알이", "놀이 중", "saved-session")
        assertEquals(first, engine.candidates("옹알이", "놀이 중", "saved-session"))
        assertEquals(first, PhraseEngine(context, preferencesName)
            .candidates("옹알이", "놀이 중", "saved-session"))
    }

    @Test fun newSessionAvoidsNineMostRecentLines() {
        val exposed = (0..2).flatMap { turn ->
            engine.candidates("울음", "수유 전", "session-$turn")
        }
        assertEquals(9, exposed.toSet().size)
        val next = PhraseEngine(context, preferencesName)
            .candidates("울음", "수유 전", "session-3")
        assertTrue(next.none { it in exposed })
    }

    @Test fun firstLineChangesAcrossNewSessions() {
        val leading = (0..5).map { turn ->
            engine.candidates("웃음", "잠에서 깬 후", "session-$turn").first()
        }
        assertTrue("Lead line should rotate", leading.distinct().size >= 3)
    }

    @Test fun candidatesVaryBothSentenceHalvesAcrossSessions() {
        val sounds = listOf("울음", "웃음", "옹알이", "기타 소리", "영상 장면")
        for (sound in sounds) {
            var recent = emptyList<String>()
            repeat(24) { turn ->
                val lines = engine.candidates(sound, "놀이 중", "varied-$sound-$turn")
                val halves = lines.map { it.split(Regex("(?<=[.!?])\\s+"), limit = 2) }
                assertTrue(halves.all { it.size == 2 })
                assertEquals("$sound session $turn repeats an opening", 3,
                    halves.map { it[0] }.distinct().size)
                assertEquals("$sound session $turn repeats a closing", 3,
                    halves.map { it[1] }.distinct().size)
                assertTrue("$sound session $turn repeats a recent full line", lines.none { it in recent })
                recent = (recent + lines).takeLast(9)
            }
        }
    }

    @Test fun bucketsKeepIndependentRecentHistories() {
        val original = engine.candidates("옹알이", "수유 전", "one")
        repeat(8) { turn -> engine.candidates("웃음", "놀이 중", "other-$turn") }
        assertEquals(original, engine.candidates("옹알이", "수유 전", "one"))
        val second = engine.candidates("옹알이", "수유 전", "two")
        assertTrue(second.none { it in original })
    }

    @Test fun malformedAndOverlongInputsFallBackSafely() {
        val longValue = "아".repeat(2000)
        val fallback = engine.candidates("기타 소리", "기타 상황", "fallback")
        assertEquals(fallback, engine.candidates(longValue, longValue, "fallback"))
        assertEquals(3, engine.candidates("", "", "").distinct().size)
        assertEquals(3, engine.candidates("울음", "수유 전", longValue).distinct().size)
    }

    @Test fun cacheIsBoundedAndResetClearsIt() {
        repeat(140) { turn -> engine.candidates("영상 장면", "기타 상황", "session-$turn") }
        val stored = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getString("phrase_state", "{}") ?: "{}"
        val json = JSONObject(stored)
        assertTrue(json.getJSONArray("sessions").length() <= 128)
        assertTrue(json.getJSONObject("recent").getJSONArray("영상 장면|기타 상황").length() <= 9)
        engine.reset()
        assertFalse(context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .contains("phrase_state"))
    }
}
