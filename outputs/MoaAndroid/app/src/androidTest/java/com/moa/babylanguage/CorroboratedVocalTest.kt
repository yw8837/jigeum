package com.moa.babylanguage
import org.junit.Assert.*
import org.junit.Test
/** Actual frozen Android scores; these are correlated public-source conditions, not independent babies. */
class CorroboratedVocalTest {
    @Test fun laugh_rms6KeepsCorroboratedVoiceWithoutInventingEmotion() {
        val audio = SoundAnalysis(emptyList(), frames = listOf(SoundFrame(0, listOf(SoundCue("웃음(화자 미확인)", 0.80078125f), SoundCue("아기 웃음 후보", 0.5859375f), SoundCue("울음(화자 미확인)", 0.04296875f), SoundCue("옹알이 후보", 0.03125f), SoundCue("울음", 0.03125f), SoundCue("말소리(화자 미확인)", 0.015625f), SoundCue("어린 말소리 후보", 0.01171875f), SoundCue("비명(화자 미확인)", 0.0078125f), SoundCue("배경 소리", 0.00390625f)), 0.006076145296699298),SoundFrame(975, listOf(SoundCue("옹알이 후보", 0.33203125f), SoundCue("울음(화자 미확인)", 0.26171875f), SoundCue("울음", 0.109375f), SoundCue("웃음(화자 미확인)", 0.08203125f), SoundCue("아기 웃음 후보", 0.05859375f), SoundCue("말소리(화자 미확인)", 0.04296875f), SoundCue("어린 말소리 후보", 0.04296875f), SoundCue("배경 소리", 0.01171875f), SoundCue("비명(화자 미확인)", 0.00390625f)), 0.001966290009213134),SoundFrame(1950, listOf(SoundCue("말소리(화자 미확인)", 0.05859375f), SoundCue("비명(화자 미확인)", 0.01171875f), SoundCue("어린 말소리 후보", 0.0078125f), SoundCue("배경 소리", 0.0078125f), SoundCue("웃음(화자 미확인)", 0.00390625f), SoundCue("울음(화자 미확인)", 0.00390625f), SoundCue("옹알이 후보", 0f), SoundCue("울음", 0f), SoundCue("아기 웃음 후보", 0f)), 0.00013576361857544692)))
        val ast = AstAudioAnalysis(listOf(AstAudioWindow(0, 2479, mapOf("Babbling" to 0.02606487274169922f, "Child speech, kid speaking" to 0.0025440582539886236f, "Baby cry, infant cry" to 0.06912228465080261f, "Baby laughter" to 0.1091311052441597f, "Crying, sobbing" to 0.024975866079330444f, "Laughter" to 0.057853519916534424f, "Screaming" to 0.00042326454422436655f, "Giggle" to 0.07901586592197418f, "Snicker" to 0.002494916319847107f, "Belly laugh" to 0.018771031871438026f, "Chuckle, chortle" to 0.009107179939746857f, "Whimper" to 0.02588478848338127f, "Wail, moan" to 0.007267775014042854f, "Television" to 0.0003365954617038369f),
            0.9241688251495361f, 0.005999999986465575, 0.9400785565376282f)))
        val combined = InfantVoiceSupportPolicy.attachCandidate(audio, ast, 2479)
        val result = EvidenceInterpreter.interpret(combined,
            AcousticShape(0f, false, 0, strongestSecondRms = 0.005999999986465575), ClipRange(0, 2480))
        assertNotNull("Corroborated infant voice still held: ${result.issue}", result.interpretation)
        assertEquals("아기 발성", result.interpretation!!.sound)
        for (word in listOf("으앙", "응애", "웃", "울", "배고", "졸", "슬퍼", "행복"))
            assertFalse(result.interpretation.phrase, result.interpretation.phrase.contains(word))
    }
    @Test fun laugh_rms9KeepsCorroboratedVoiceWithoutInventingEmotion() {
        val audio = SoundAnalysis(emptyList(), frames = listOf(SoundFrame(0, listOf(SoundCue("웃음(화자 미확인)", 0.890625f), SoundCue("아기 웃음 후보", 0.66796875f), SoundCue("울음(화자 미확인)", 0.01953125f), SoundCue("울음", 0.015625f), SoundCue("옹알이 후보", 0.01171875f), SoundCue("말소리(화자 미확인)", 0.0078125f), SoundCue("비명(화자 미확인)", 0.00390625f), SoundCue("어린 말소리 후보", 0.00390625f), SoundCue("배경 소리", 0f)), 0.009114218131469601),SoundFrame(975, listOf(SoundCue("옹알이 후보", 0.5f), SoundCue("울음(화자 미확인)", 0.33203125f), SoundCue("울음", 0.19921875f), SoundCue("아기 웃음 후보", 0.109375f), SoundCue("웃음(화자 미확인)", 0.109375f), SoundCue("어린 말소리 후보", 0.05859375f), SoundCue("말소리(화자 미확인)", 0.03125f), SoundCue("배경 소리", 0.01171875f), SoundCue("비명(화자 미확인)", 0.00390625f)), 0.002949435074576217),SoundFrame(1950, listOf(SoundCue("말소리(화자 미확인)", 0.08203125f), SoundCue("배경 소리", 0.0078125f), SoundCue("어린 말소리 후보", 0.0078125f), SoundCue("비명(화자 미확인)", 0.0078125f), SoundCue("울음", 0.00390625f), SoundCue("웃음(화자 미확인)", 0.00390625f), SoundCue("울음(화자 미확인)", 0.00390625f), SoundCue("옹알이 후보", 0.00390625f), SoundCue("아기 웃음 후보", 0f)), 0.00020364543269352692)))
        val ast = AstAudioAnalysis(listOf(AstAudioWindow(0, 2479, mapOf("Babbling" to 0.034333378076553345f, "Child speech, kid speaking" to 0.002686860738322139f, "Baby cry, infant cry" to 0.06771442294120789f, "Baby laughter" to 0.12941713631153107f, "Crying, sobbing" to 0.023631008341908455f, "Laughter" to 0.0700402483344078f, "Screaming" to 0.0003785861190408468f, "Giggle" to 0.0865965336561203f, "Snicker" to 0.0025904409121721983f, "Belly laugh" to 0.02355414442718029f, "Chuckle, chortle" to 0.009991212747991085f, "Whimper" to 0.02514824829995632f, "Wail, moan" to 0.0064873723313212395f, "Television" to 0.0003466117777861655f),
            0.935772180557251f, 0.009000000163781026, 0.9523242115974426f)))
        val combined = InfantVoiceSupportPolicy.attachCandidate(audio, ast, 2479)
        val result = EvidenceInterpreter.interpret(combined,
            AcousticShape(0f, false, 0, strongestSecondRms = 0.009000000163781026), ClipRange(0, 2480))
        assertNotNull("Corroborated infant voice still held: ${result.issue}", result.interpretation)
        assertEquals("아기 발성", result.interpretation!!.sound)
        for (word in listOf("으앙", "응애", "웃", "울", "배고", "졸", "슬퍼", "행복"))
            assertFalse(result.interpretation.phrase, result.interpretation.phrase.contains(word))
    }

    private fun audio(at: Long = 0, rms: Double? = .01, label: String = "아기 웃음 후보") =
        SoundAnalysis(emptyList(), frames = listOf(SoundFrame(at,
            listOf(SoundCue(label, .5f), SoundCue("웃음(화자 미확인)", .8f)), rms)))
    private fun ast(start: Long = 0, end: Long = 10_000, head: Float? = .9f,
                    caption: Float? = .9f, level: Double? = .01) = AstAudioWindow(start, end,
        listOf("Baby cry, infant cry", "Baby laughter", "Crying, sobbing", "Laughter", "Screaming",
            "Giggle", "Snicker", "Belly laugh", "Chuckle, chortle", "Whimper", "Wail, moan", "Television")
            .associateWith { 0f }, head, level, caption)
    private fun apply(input: SoundAnalysis = audio(), vararg windows: AstAudioWindow = arrayOf(ast()),
                      duration: Long = 10_000) =
        InfantVoiceSupportPolicy.attachCandidate(input, AstAudioAnalysis(windows.toList()), duration)

    @Test fun bothHeadsAndTheInfantWindowMustBeAudible() {
        assertTrue(InfantVoiceSupportPolicy.shouldCorroborate(audio(), 10_000))
        assertTrue(apply().infantVoiceWindows.single().kindUncertain)
        for (low in listOf(null, .79999f, Float.NaN, Float.POSITIVE_INFINITY, -1f, 1.1f)) {
            assertEquals(audio(), apply(windows = arrayOf(ast(head = low))))
            assertEquals(audio(), apply(windows = arrayOf(ast(caption = low))))
        }
        for (level in listOf(null, .00299, Double.NaN, Double.POSITIVE_INFINITY, -1.0, 1.1)) {
            assertEquals(audio(), apply(windows = arrayOf(ast(level = level))))
            val input = audio(rms = level)
            assertFalse(InfantVoiceSupportPolicy.shouldCorroborate(input, 10_000))
            assertEquals(input, apply(input))
        }
    }

    @Test fun unrelatedLouderFramesAndHighHeadsElsewhereCannotLendSupport() {
        val low = audio(rms = .001).copy(frames = audio(rms = .001).frames +
            SoundFrame(975, listOf(SoundCue("말소리(화자 미확인)", .99f)), .2))
        assertEquals(low, apply(low))
        val later = audio(at = 10_725)
        assertEquals(later, apply(later, ast(), ast(5_000,15_000,.1f,.1f),
            ast(10_000,20_000,.1f,.1f), duration=20_000))
        // Existing approvals are preserved even when candidate AST ranges differ.
        val separated = audio().copy(frames = audio().frames + later.frames)
        assertEquals(separated, apply(separated, ast(), ast(5_000,15_000,.1f,.1f),
            ast(10_000,20_000,.1f,.1f), duration=20_000))
        val unresolved = audio().copy(frames=audio().frames + SoundFrame(10_725,
            listOf(SoundCue("울음",.5f),SoundCue("울음(화자 미확인)",.8f)),.01))
        val partial = apply(unresolved, ast(),ast(5_000,15_000,.1f,.1f),
            ast(10_000,20_000,.1f,.1f),duration=20_000)
        assertTrue(partial.infantVoicePartial)
        assertEquals(listOf(InfantVoiceSupportWindow(0,10_000,true)),partial.infantVoiceWindows)
    }

    @Test fun genericAdultSoundsAndOriginalApprovalsAreNotReclassified() {
        for (label in listOf("웃음(화자 미확인)", "말소리(화자 미확인)", "울음(화자 미확인)", "배경 소리")) {
            val input = audio(label = label)
            assertFalse(InfantVoiceSupportPolicy.shouldCorroborate(input, 10_000))
            assertEquals(input, apply(input))
        }
        val accepted = audio().copy(frames = listOf(SoundFrame(0, listOf(SoundCue("울음", .9f)), .01)))
        assertEquals(accepted, apply(accepted))
        val failed = audio().copy(problem = "input error", issue = AnalysisIssue.INVALID_AUDIO)
        assertEquals(failed, apply(failed))
    }

    @Test fun existingPrecisePartialObservationWinsOverBroadPartial() {
        val input = SoundAnalysis(emptyList(), frames=listOf(0L,975L).map {
            SoundFrame(it,listOf(SoundCue("울음",.9f)),.01)
        } + SoundFrame(6_000,listOf(SoundCue("웃음(화자 미확인)",.9f)),.01))
        assertNotNull(SoundTimeline.partialObservation(input,20_000))
        val later=ast(10_000,20_000).copy(scores=ast().scores+("Laughter" to .4f))
        assertEquals(input,apply(input,ast(),ast(5_000,15_000),later,duration=20_000))
    }

    @Test fun aFailedNewCorroborationIsNotReportedAsMissingInfantVoice() {
        val failed = InfantVoiceSupportPolicy.attachCandidate(audio(),
            AstAudioAnalysis(emptyList(), "synthetic native failure"), 10_000)
        val result = EvidenceInterpreter.interpret(failed,
            AcousticShape(.5f, false, 0, strongestSecondRms=.02), ClipRange(0,10_000))
        assertEquals(AnalysisIssue.MODEL_FAILURE, result.issue)
        assertNull(result.interpretation)
        assertFalse(result.problem!!.contains("아기 소리인지"))
        val accepted = SoundAnalysis(listOf(SoundCue("울음", .9f)))
        assertEquals(accepted, InfantVoiceSupportPolicy.attachCandidate(accepted,
            AstAudioAnalysis(emptyList(), "synthetic native failure"), 10_000))
    }

    @Test fun fullCoverageAndLocalEmotionVetoRemainMandatory() {
        for (label in ast().scores.keys) {
            val conflict = ast(10_000,20_000).copy(scores = ast().scores + (label to .30f))
            val result = apply(audio(), ast(), ast(5_000,15_000), conflict, duration=20_000)
            if (label in setOf("Television","Screaming")) assertEquals(label,audio(),result)
            else {
                assertTrue(label,result.infantVoicePartial)
                assertEquals(listOf(InfantVoiceSupportWindow(0,10_000,true)),result.infantVoiceWindows)
            }
            // An emotion conflict in the only window with an anchor still blocks it.
            assertEquals(audio(),apply(windows=arrayOf(ast().copy(scores=ast().scores+(label to .30f)))))
        }
        for (windows in listOf(arrayOf(ast()), arrayOf(ast(),ast()),
                arrayOf(ast(10_000,20_000),ast(),ast(5_000,15_000))))
            assertEquals(audio(), apply(audio(), *windows, duration=20_000))
        assertEquals(audio(), apply(windows = arrayOf(ast().copy(scores = emptyMap()))))
        for (start in listOf(-1L, 10_000L)) assertEquals(audio(at=start), apply(audio(at=start)))
        val duplicate = audio().copy(frames=audio().frames+audio().frames)
        assertEquals(duplicate, apply(duplicate))
    }

    @Test fun uncertainKindCannotCreateEmotionOrSynchronizedVisualEvidence() {
        val combined = apply()
        val decision = SoundTimeline.decide(combined, 10_000)
        assertTrue(decision.events.isEmpty() && decision.moments.isEmpty())
        assertTrue(VisualSoundAlignment.find(decision, emptyList(), 10_000).isEmpty())
        for (key in 0..30) {
            val result = EvidenceInterpreter.interpret(combined,
                AcousticShape(.9f, true, 9, key, strongestSecondRms=.02), ClipRange(0,10_000)).interpretation!!
            assertTrue(result.evidence.contains("어느 소리인지는 구분하지 않았어요"))
            for (word in listOf("으앙", "응애", "배고", "졸려", "슬퍼", "행복", "쉬었다", "커졌"))
                assertFalse(result.phrase, result.phrase.contains(word))
        }
    }
}
