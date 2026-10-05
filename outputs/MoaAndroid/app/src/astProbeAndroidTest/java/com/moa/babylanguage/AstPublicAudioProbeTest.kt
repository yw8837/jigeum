package com.moa.babylanguage

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Explicit standalone evaluator. Source labels are metadata, never quality assertions. */
class AstPublicAudioProbeTest {
    @Test fun reportPairedPublicAudioProbes() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit standalone public AST probe only", args.getString("ast_public_probe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val phaseProbe = args.getString("sound_phase_probe") == "true"
        check(context.packageName == if (phaseProbe) "com.moa.babylanguage.astcandidate" else "com.moa.babylanguage.astprobe")
        check(!BabbleSupportPolicy.ENABLED) { "Production candidate must remain disabled" }
        val runId = args.getString("sound_probe_run_id") ?: error("Missing run ID")
        require(runId.matches(Regex("[a-f0-9]{32}"))) { "Invalid run ID" }
        val parent = File(context.filesDir, "qa-public-audio").canonicalFile
        val directory = File(parent, runId).canonicalFile
        require(parent.parentFile == context.filesDir.canonicalFile && parent.name == "qa-public-audio" &&
            directory.parentFile == parent && directory.name == runId && directory.isDirectory)
        val output = File(directory, "paired-results.json")
        check(output.createNewFile()) { "Existing paired result; use a fresh run ID" }
        val rows = JSONArray()
        val report = JSONObject().put("schema_version", 1).put("run_id", runId)
            .put("model_sha256", AstModelStore.SHA256).put("quality_validated", false)
            .put("evaluation_opt_in", true).put("production_babble_policy_enabled", false)
            .put("candidate_attached_explicitly_for_evaluation", true)
            .put("ast_evaluated_even_when_yamnet_not_eligible", true)
            .put("status", "running").put("completed", false)
            .put("case_count", 0).put("measured_count", 0).put("cases", rows)
        val started = SystemClock.elapsedRealtime()
        var measuredCount = 0
        try {
            val manifestBytes = boundedBytes(File(directory, "manifest.json"), directory, 131_072)
            val manifest = try {
                report.put("manifest_sha256", PublicAudioProbeContract.sha256(manifestBytes))
                val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(manifestBytes)).toString()
                val tokens = JSONTokener(text)
                JSONArray(tokens).also { require(tokens.nextClean() == '\u0000') }
            } finally { manifestBytes.fill(0) }
            require(manifest.length() in 1..100) { "Expected 1..100 local PCM cases" }
            val entries = (0 until manifest.length()).map { manifest.getJSONObject(it) }
            val ids = entries.map { it.get("id") as? String ?: error("Invalid case ID") }
            require(ids.distinct().size == ids.size && ids.all { it.matches(Regex("[a-z0-9_-]{1,100}")) })
            require(entries.all { it.get("run_id") == runId }) { "Stale manifest run ID" }
            report.put("case_count", entries.size)
            output.writeText(report.toString(2))
            for (entry in entries) {
                val id = entry.getString("id")
                val row = JSONObject().put("id", id).put("run_id", runId).put("status", "running")
                    .put("quality_validated", false)
                rows.put(row)
                var bytes = ByteArray(0)
                var pcm = FloatArray(0)
                var stage = "pcm_contract"
                val caseStarted = SystemClock.elapsedRealtime()
                try {
                    bytes = boundedBytes(File(directory, "$id.f32"), directory, 1_280_000)
                    row.put("received_bytes", bytes.size)
                        .put("received_sha256", PublicAudioProbeContract.sha256(bytes))
                    val decoded = PublicAudioProbeContract.decode(bytes, entry)
                    pcm = decoded.pcm
                    row.put("samples", pcm.size).put("duration_ms", decoded.range.durationMs)
                        .put("start_ms", decoded.range.startMs).put("end_ms", decoded.range.endMs)
                    stage = "yamnet"
                    val baseStarted = SystemClock.elapsedRealtime()
                    val baseline = SoundAnalyzer.analyzeDetailed(context, pcm)
                    PublicAudioProbeContract.requireCompletedAnalysis(baseline)
                    requireBaselineFrames(baseline, decoded.range.durationMs)
                    row.put("yamnet_ms", SystemClock.elapsedRealtime() - baseStarted)
                    val shape = AcousticShape.from(pcm)
                    row.put("base", observation(baseline,
                        EvidenceInterpreter.interpret(baseline, shape, decoded.range)))
                        .put("eligible", BabbleSupportPolicy.shouldAnalyze(baseline))
                    stage = "ast"
                    val astStarted = SystemClock.elapsedRealtime()
                    val ast = AstAudioAnalyzer.analyze(context, pcm)
                    row.put("ast_ms", SystemClock.elapsedRealtime() - astStarted).put("ast", astJson(ast))
                    requireCompleteAst(ast, pcm.size)
                    stage = "candidate_policy"
                    val candidate = BabbleSupportPolicy.attachCandidate(baseline, ast, decoded.range.durationMs)
                    row.put("candidate", observation(candidate,
                        EvidenceInterpreter.interpret(candidate, shape, decoded.range)))
                        .put("supported_windows", candidate.babbleWindows.size)
                        .put("status", "measured")
                    if (phaseProbe) {
                        stage = "phase_recovery"
                        val phaseStarted = SystemClock.elapsedRealtime()
                        val recovered = SoundAnalyzer.analyzeWithSupport(context, pcm) { fresh ->
                            check(fresh == baseline) { "Baseline changed for identical PCM" }
                            if (BabbleSupportPolicy.shouldAnalyze(fresh))
                                BabbleSupportPolicy.attachCandidate(fresh, ast, decoded.range.durationMs) else fresh
                        }
                        PublicAudioProbeContract.requireCompletedAnalysis(recovered)
                        requireBaselineFrames(recovered, decoded.range.durationMs)
                        row.put("phase", observation(recovered,
                            EvidenceInterpreter.interpret(recovered, shape, decoded.range)))
                            .put("phase_ms_excluding_ast", SystemClock.elapsedRealtime() - phaseStarted)
                        check(PublicAudioProbeContract.sha256(bytes) == entry.getString("pcm_sha256"))
                    }
                    measuredCount++
                    report.put("measured_count", measuredCount)
                } catch (error: Throwable) {
                    row.put("status", "error").put("failure_stage", stage)
                        .put("error_type", error.javaClass.simpleName)
                    throw error
                } finally {
                    pcm.fill(0f)
                    bytes.fill(0)
                    row.put("milliseconds", SystemClock.elapsedRealtime() - caseStarted)
                    output.writeText(report.toString(2))
                }
            }
            check(measuredCount == entries.size)
            report.put("status", "completed").put("completed", true)
        } catch (error: Throwable) {
            report.put("status", "failed").put("completed", false)
                .put("error_type", error.javaClass.simpleName)
            throw error
        } finally {
            report.put("milliseconds", SystemClock.elapsedRealtime() - started)
            output.writeText(report.toString(2))
        }
    }

    private fun boundedBytes(file: File, directory: File, maximum: Int): ByteArray {
        require(file.canonicalFile.parentFile == directory && file.canonicalFile.name == file.name &&
            file.isFile && file.canRead() && file.length() in 1L..maximum.toLong()) { "Invalid bounded input file" }
        val scratch = ByteArray(maximum + 1)
        try {
            val size = file.inputStream().use { input ->
                var count = 0
                while (count < scratch.size) {
                    val read = input.read(scratch, count, scratch.size - count)
                    if (read < 0) break
                    check(read > 0) { "Input read stalled" }
                    count += read
                }
                count
            }
            require(size in 1..maximum) { "Input size changed or exceeds bound" }
            return scratch.copyOf(size)
        } finally { scratch.fill(0) }
    }

    private fun requireBaselineFrames(audio: SoundAnalysis, durationMs: Long) {
        // Silence intentionally has no classifier frames; unresolved measured audio must have them.
        if (audio.problem == null) check(audio.frames.isNotEmpty()) { "Missing YAMNet measurements" }
        check(audio.frames.all { it.startMs >= 0 && it.startMs < durationMs &&
            it.cues.all { cue -> cue.score.isFinite() && cue.score in 0f..1f } })
        check(audio.frames.zipWithNext().all { (a, b) -> a.startMs < b.startMs })
    }

    private fun requireCompleteAst(ast: AstAudioAnalysis, samples: Int) {
        check(ast.problem == null) { "AST runtime failed" }
        val expected = AstAudioAnalyzer.windowRanges(samples).map { (start, end) ->
            start * 1000L / 16000 to end * 1000L / 16000
        }
        check(ast.windows.map { it.startMs to it.endMs } == expected) { "Incomplete AST selected interval" }
        check(ast.windows.all { it.scores.size == 527 && it.scores.keys.containsAll(RELATED) &&
            it.scores.values.all { value -> value.isFinite() && value in 0f..1f } }) { "Invalid AST scores" }
    }

    private fun cueJson(cues: List<SoundCue>) = JSONArray(cues.map {
        JSONObject().put("label", it.label).put("score", it.score.toDouble())
    })

    private fun observation(audio: SoundAnalysis, interpreted: ClipAnalysisResult): JSONObject {
        val decision = SoundTimeline.decide(audio)
        return JSONObject().put("issue", interpreted.issue?.name ?: JSONObject.NULL)
            .put("decision_issue", decision.issue?.name ?: JSONObject.NULL)
            .put("analysis_issue", audio.issue?.name ?: JSONObject.NULL)
            .put("events", JSONArray(decision.events.map { it.name }))
            .put("has_interpretation", interpreted.interpretation != null)
            .put("sound", interpreted.interpretation?.sound ?: JSONObject.NULL)
            .put("frame_starts_ms", JSONArray(audio.frames.map { it.startMs }))
            .put("cues", cueJson(audio.cues)).put("frames", JSONArray(audio.frames.map {
                JSONObject().put("start_ms", it.startMs).put("cues", cueJson(it.cues))
            })).put("broad_windows", JSONArray(decision.broadWindows.map {
                JSONObject().put("start_ms", it.startMs).put("end_ms", it.endMs)
            }))
    }

    private fun astJson(ast: AstAudioAnalysis) = JSONObject().put("model_sha256", AstModelStore.SHA256)
        .put("problem", ast.problem ?: JSONObject.NULL).put("returned_windows", ast.windows.size)
        .put("windows", JSONArray(ast.windows.map { window ->
            JSONObject().put("start_ms", window.startMs).put("end_ms", window.endMs)
                .put("scores", JSONObject().apply {
                    for (label in RELATED) window.scores[label]?.let { put(label, it.toDouble()) }
                }).put("top", JSONArray(window.scores.entries.sortedByDescending { it.value }.take(10).map {
                    JSONObject().put("label", it.key).put("score", it.value.toDouble())
                }))
        }))

    @Test fun missingAstCoverageAndRuntimeFailureAreRejected() {
        val scores = (RELATED + (1..527).map { "synthetic_$it" }).take(527).associateWith { 0f }
        val complete = AstAudioAnalysis(listOf(AstAudioWindow(0, 10000, scores),
            AstAudioWindow(5000, 15000, scores), AstAudioWindow(10000, 20000, scores)))
        requireCompleteAst(complete, 320000)
        for (invalid in listOf(complete.copy(windows = complete.windows.take(2)),
            complete.copy(problem = "synthetic failure"))) {
            var rejected = false
            try { requireCompleteAst(invalid, 320000) } catch (_: IllegalStateException) { rejected = true }
            assertTrue("Failed or partial AST is not measured abstention", rejected)
        }
    }

    companion object {
        private val RELATED = listOf("Babbling", "Child speech, kid speaking", "Baby cry, infant cry",
            "Baby laughter", "Crying, sobbing", "Laughter", "Screaming", "Giggle", "Snicker", "Belly laugh",
            "Chuckle, chortle", "Whimper", "Wail, moan", "Television")
    }
}
