package com.moa.babylanguage

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SoundTimelineTest {
    private fun frame(at: Long, vararg cues: Pair<String, Float>) = SoundFrame(at, cues.map { SoundCue(it.first, it.second) })
    private fun analyze(vararg frames: SoundFrame) = SoundAnalysis(emptyList(), frames = frames.toList())
    private val laugh = frame(0, "아기 웃음 후보" to .67f, "웃음(화자 미확인)" to .98f)
    private val cry = frame(975, "울음" to .85f, "울음(화자 미확인)" to .96f)

    @Test fun weakOffWindowCryDoesNotEraseClearLaughter() {
        val audio = analyze(laugh, frame(975, "아기 웃음 후보" to .26f, "울음(화자 미확인)" to .33f))
        assertEquals(listOf(VocalEvent.LAUGH), SoundTimeline.decide(audio).events)
    }
    @Test fun competingValenceIsSymmetricAndIndependentOfCueOrder() {
        val cues = listOf(SoundCue("울음", .85f), SoundCue("아기 웃음 후보", .7f), SoundCue("웃음(화자 미확인)", .95f))
        for (input in listOf(cues, cues.reversed())) assertNotNull(SoundTimeline.decide(SoundAnalysis(input)).problem)
    }
    @Test fun cryLaughterTransitionsKeepOrderAndNeverInvitePlay() {
        for (frames in listOf(arrayOf(cry.copy(startMs=0), laugh.copy(startMs=975)), arrayOf(laugh, cry))) {
            val audio = analyze(*frames)
            val decision = SoundTimeline.decide(audio)
            assertEquals("여러 아기 소리", decision.sound)
            assertEquals(frames.map { if (it.cues.first().label == "울음") VocalEvent.CRY else VocalEvent.LAUGH }, decision.events)
            val result = EvidenceInterpreter.interpret(audio, AcousticShape(.8f, false, 2), ClipRange(0,3000)).interpretation!!
            assertFalse(result.phrase.contains("응애")); assertFalse(result.phrase.contains("헤헤"))
            assertTrue(result.guide.contains("편안한지")); assertFalse(result.phrase.contains("놀"))
            assertTrue(result.evidence.contains("→"))
        }
    }
    @Test fun loneModerateSpikeNeedsPersistenceButStrongShortClipWorks() {
        val moderate = frame(975, "아기 웃음 후보" to .5f, "웃음(화자 미확인)" to .8f)
        assertNotNull(SoundTimeline.decide(analyze(frame(0), moderate, frame(1950))).problem)
        assertEquals(listOf(VocalEvent.LAUGH), SoundTimeline.decide(analyze(moderate, moderate.copy(startMs=1950))).events)
        assertEquals(listOf(VocalEvent.LAUGH), SoundTimeline.decide(analyze(laugh)).events)
    }
    @Test fun genericLaughterCannotAuthorizeBabyCryAndGenericSoundsCannotBecomeBaby() {
        assertNotNull(SoundTimeline.decide(analyze(frame(0,"울음" to .45f,"웃음(화자 미확인)" to .95f))).problem)
        for (label in listOf("웃음(화자 미확인)","울음(화자 미확인)","말소리(화자 미확인)","배경 소리"))
            assertNotNull(SoundTimeline.decide(analyze(frame(0,label to .99f))).problem)
    }
    @Test fun screamIsNotSadnessAndDoesNotAlwaysBlockSupportedLaughter() {
        val clear = laugh.copy(cues=laugh.cues+SoundCue("비명(화자 미확인)",.5f))
        assertEquals(listOf(VocalEvent.LAUGH), SoundTimeline.decide(analyze(clear)).events)
        val uncertain = frame(0,"아기 웃음 후보" to .5f,"비명(화자 미확인)" to .9f)
        assertNotNull(SoundTimeline.decide(analyze(uncertain, uncertain.copy(startMs=975))).problem)
    }
    @Test fun unresolvedOppositeInfantMomentCannotBeHiddenByDominantEvent() {
        val briefCry=frame(975,"울음" to .4f,"울음(화자 미확인)" to .5f)
        assertNotNull(SoundTimeline.decide(analyze(laugh, briefCry, laugh.copy(startMs=1950))).problem)
    }
    @Test fun repeatedConflictCannotBeErasedByOneStrongWindow() {
        val conflict=frame(975,"울음" to .85f,"아기 웃음 후보" to .8f)
        assertNotNull(SoundTimeline.decide(analyze(laugh, conflict, conflict.copy(startMs=1950))).problem)
    }
    @Test fun strongUnattributedOppositeSoundMustNotBeIgnoredAcrossTime() {
        val genericCry=frame(975,"울음(화자 미확인)" to .9f)
        assertNotNull(SoundTimeline.decide(analyze(laugh, genericCry, laugh.copy(startMs=1950))).problem)
        val genericLaugh=frame(975,"웃음(화자 미확인)" to .9f)
        assertNotNull(SoundTimeline.decide(analyze(cry.copy(startMs=0), genericLaugh, cry.copy(startMs=1950))).problem)
    }
    @Test fun malformedScoresAndPcmDoNotGeneratePhrases() = runBlocking {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 2f))
            assertNotNull(SoundTimeline.decide(analyze(frame(0,"울음" to value))).problem)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        for (pcm in listOf(FloatArray(16000){Float.NaN},FloatArray(16000){2f},FloatArray(480001){.1f}))
            assertNotNull(SoundAnalyzer.analyzeDetailed(context,pcm).problem)
    }
    @Test fun childVoiceRequiresThreeWindowsAndMakesOnlyNeutralSpeech() {
        val child=frame(0,"어린 말소리 후보" to .42f,"말소리(화자 미확인)" to .9f)
        assertNotNull(SoundTimeline.decide(analyze(child,child.copy(startMs=975))).problem)
        val audio=analyze(child,child.copy(startMs=1950),child.copy(startMs=4875))
        assertEquals(listOf(VocalEvent.CHILD_VOICE),SoundTimeline.decide(audio).events)
        val result=EvidenceInterpreter.interpret(audio,AcousticShape(.8f,false,2),ClipRange(0,6000)).interpretation!!
        for (word in listOf("웃", "울", "슬퍼", "배고", "졸려")) assertFalse(result.phrase.contains(word))
        val adult=frame(0,"말소리(화자 미확인)" to .99f)
        assertNotNull(SoundTimeline.decide(analyze(adult,adult.copy(startMs=975),adult.copy(startMs=1950))).problem)
    }
}
