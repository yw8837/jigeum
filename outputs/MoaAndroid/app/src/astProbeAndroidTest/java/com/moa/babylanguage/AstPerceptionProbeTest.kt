package com.moa.babylanguage

import android.app.ActivityManager
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Local evaluation only. No media/manifest is bundled in either APK.
 *
 * adb instrumentation argument: -e ast_perception_probe true
 * target filesDir/qa-ast-perception/manifest.json:
 * {"schema_version":1,"run_id":"anonymous-run-01","cases":[
 *   {"id":"owner1_10","file":"owner1.mp4","start_ms":0,"end_ms":10000,"visual":true}
 * ]}
 * All files must be flat, anonymous ASCII .mp4 names inside that directory.
 * Results stay there as results-RUN_ID.json. A successful JUnit invocation only
 * means the evaluator completed; quality_validated remains false in the output.
 */
class AstPerceptionProbeTest {
    private data class ProbeCase(val id: String, val file: File, val range: ClipRange,
                                 val visual: Boolean, val sourceDurationMs: Long, val sha256: String)

    @Test fun evaluateLocalPerceptionManifest() {
        assumeTrue("Requires explicit local AST perception probe opt-in",
            InstrumentationRegistry.getArguments().getString("ast_perception_probe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { withContext(Dispatchers.Default) {
            val directory = File(context.filesDir, "qa-ast-perception").canonicalFile
            require(directory.isDirectory && directory.parentFile == context.filesDir.canonicalFile &&
                directory.name == "qa-ast-perception") { "AST probe runtime directory invalid" }
            // Reject malformed input with a fixed message; never print manifest contents or paths.
            val (runId, cases) = try { readManifest(context, directory) }
                catch (_: Exception) { throw IllegalArgumentException("AST probe manifest invalid") }
            val output = File(directory, "results-$runId.json")
            require(!output.exists()) { "AST probe run ID already has results" }
            val results = JSONArray()
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val report = JSONObject()
                .put("schema_version", 1).put("run_id", runId)
                .put("evaluation_opt_in", true)
                .put("production_babble_policy_enabled", BabbleSupportPolicy.ENABLED)
                .put("candidate_attached_explicitly_for_evaluation", true)
                .put("ast_evaluated_even_when_yamnet_not_eligible", true)
                .put("quality_validated", false)
                .put("execution_success_is_not_quality_pass", true)
                .put("model_sha256", AstModelStore.SHA256)
                .put("android_ort_version", "1.24.3")
                .put("package_version_name", packageInfo.versionName ?: JSONObject.NULL)
                .put("package_version_code", packageInfo.versionCode)
                .put("android_sdk", Build.VERSION.SDK_INT)
                .put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
                .put("case_count", cases.size).put("cases", results)
                .put("status", "running")
                .put("memory_measurement", "Stage snapshots; VmHWM is process lifetime peak, not this case peak")
            val runStarted = SystemClock.elapsedRealtimeNanos()
            writeReport(output, report)
            try {
                for (case in cases) {
                    coroutineContext.ensureActive()
                    results.put(evaluateCase(context, case))
                    report.put("elapsed_ms", elapsedMs(runStarted))
                    writeReport(output, report)
                }
                report.put("status", "completed")
            } catch (cancelled: CancellationException) {
                report.put("status", "cancelled")
                throw cancelled
            } finally {
                report.put("elapsed_ms", elapsedMs(runStarted))
                writeReport(output, report)
            }
            // No assertion about expected sound, phrase, score, or quality is made here.
        } }
    }

    private suspend fun evaluateCase(context: Context, case: ProbeCase): JSONObject {
        val row = JSONObject().put("id", case.id).put("file", case.file.name)
            .put("source_sha256", case.sha256).put("source_bytes", case.file.length())
            .put("source_duration_ms", case.sourceDurationMs)
            .put("start_ms", case.range.startMs).put("end_ms", case.range.endMs)
            .put("visual_requested", case.visual).put("quality_validated", false)
        val stages = JSONArray()
        row.put("stages", stages)
        val started = SystemClock.elapsedRealtimeNanos()
        var stage = "decode"
        var pcm: FloatArray? = null
        suspend fun <T> measured(name: String, action: suspend () -> T): T {
            stage = name
            val at = SystemClock.elapsedRealtimeNanos()
            val snapshot = JSONObject().put("stage", name).put("before", memory(context))
            try { return action() } finally {
                snapshot.put("elapsed_ms", elapsedMs(at)).put("after", memory(context))
                stages.put(snapshot)
            }
        }
        try {
            val uri = Uri.fromFile(case.file)
            measured("decode") {
                VideoAudioDecoder.decode(context, uri, case.range.startMs, case.range.endMs).also { pcm = it }
            }
            val samples = pcm
            row.put("decoded_samples", samples?.size ?: 0)
                .put("decoded_duration_ms", samples?.let { it.size / 16.0 } ?: JSONObject.NULL)
                .put("decoded_matches_selected_length", samples?.size?.toLong() == case.range.durationMs * 16L)
            val base = measured("yamnet") {
                if (samples == null) SoundAnalysis(emptyList(), "No decoded audio track", issue = AnalysisIssue.NO_AUDIO)
                else SoundAnalyzer.analyzeDetailed(context, samples)
            }
            row.put("base_audio", audio(base))
                .put("candidate_eligible_by_yamnet", BabbleSupportPolicy.shouldAnalyze(base))
            val ast = measured("ast") {
                if (samples == null) AstAudioAnalysis(emptyList(), "AST not run: no decoded PCM")
                else AstAudioAnalyzer.analyze(context, samples)
            }
            row.put("ast", JSONObject().put("problem", ast.problem ?: JSONObject.NULL)
                .put("analyzer_invoked", samples != null).put("returned_windows", ast.windows.size)
                .put("windows", JSONArray().apply {
                    for (window in ast.windows) put(JSONObject().put("start_ms", window.startMs)
                        .put("end_ms", window.endMs).put("scores", JSONObject().apply {
                            for ((label, score) in window.scores) put(label, finite(score))
                        }))
                }))
            val candidate = measured("candidate_policy") {
                BabbleSupportPolicy.attachCandidate(base, ast, samples?.size?.div(16L) ?: case.range.durationMs)
            }
            row.put("candidate_audio", audio(candidate))
            val shape = measured("acoustic_shape") {
                if (samples != null && base.problem == null) AcousticShape.from(samples) else AcousticShape(0f, false, 0)
            }
            row.put("acoustic_shape", JSONObject().put("active_fraction", finite(shape.activeFraction))
                .put("rising", shape.rising).put("bursts", shape.bursts).put("pauses", shape.pauses))
            val visual = if (case.visual) measured("visual") {
                inspectVideoEvidence(context, uri, case.range.startMs, case.range.endMs)
            } else null
            row.put("visual", visual?.let { visualJson(it) } ?: JSONObject.NULL)
            row.put("base_interpretation", interpretation(measured("base_interpreter") {
                EvidenceInterpreter.interpret(base, shape, case.range, visual)
            }))
            row.put("candidate_interpretation", interpretation(measured("candidate_interpreter") {
                EvidenceInterpreter.interpret(candidate, shape, case.range, visual)
            }))
            row.put("operational_status", "completed")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Exception messages can contain media paths or contents; keep only its type.
            row.put("operational_status", "failed").put("failure_stage", stage)
                .put("exception_type", failure.javaClass.name)
        } catch (failure: LinkageError) {
            row.put("operational_status", "native_unavailable").put("failure_stage", stage)
                .put("exception_type", failure.javaClass.name)
        } catch (_: OutOfMemoryError) {
            row.put("operational_status", "memory_exhausted").put("failure_stage", stage)
        } finally {
            pcm?.fill(0f)
            row.put("pcm_zeroed", pcm != null).put("elapsed_ms", elapsedMs(started))
        }
        return row
    }

    private fun readManifest(context: Context, directory: File): Pair<String, List<ProbeCase>> {
        val file = File(directory, "manifest.json")
        require(file.canonicalFile.parentFile == directory && file.canonicalFile.name == "manifest.json" &&
            file.isFile && file.canRead() && file.length() in 1L..131_072L)
        val source = ByteArray(131_073)
        val count = file.inputStream().use { input ->
            var offset = 0
            while (offset < source.size) {
                val read = input.read(source, offset, source.size - offset)
                if (read < 0) break
                if (read == 0) error("Manifest read stalled")
                offset += read
            }
            offset
        }
        require(count in 1..131_072)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(source, 0, count)).toString()
        val tokens = JSONTokener(text)
        val manifest = JSONObject(tokens)
        require(tokens.nextClean() == '\u0000')
        require(keys(manifest) == setOf("schema_version", "run_id", "cases") && integer(manifest, "schema_version") == 1L)
        val runId = manifest.get("run_id") as? String ?: error("Invalid run ID")
        require(ID.matches(runId))
        val input = manifest.get("cases") as? JSONArray ?: error("Invalid cases")
        require(input.length() in 1..64)
        val identifiers = mutableSetOf<String>()
        val hashes = mutableMapOf<File, String>()
        val cases = (0 until input.length()).map { index ->
            val item = input.get(index) as? JSONObject ?: error("Invalid case")
            require(keys(item).all { it in setOf("id", "file", "start_ms", "end_ms", "visual") } &&
                keys(item).containsAll(setOf("id", "file", "start_ms", "end_ms")))
            val id = item.get("id") as? String ?: error("Invalid ID")
            require(ID.matches(id) && identifiers.add(id))
            val name = item.get("file") as? String ?: error("Invalid filename")
            require(MP4.matches(name))
            val media = File(directory, name).canonicalFile
            require(media.parentFile == directory && media.name == name && media.isFile && media.canRead() &&
                media.length() in 1L..200L * 1024 * 1024)
            val start = integer(item, "start_ms")
            val end = integer(item, "end_ms")
            require(start in 0L..110_500L && end in 10_000L..120_500L && end - start in setOf(10_000L, 20_000L))
            val visual = if (item.has("visual")) item.get("visual") as? Boolean ?: error("Invalid visual flag") else false
            val retriever = MediaMetadataRetriever()
            val duration = try {
                retriever.setDataSource(context, Uri.fromFile(media))
                require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes")
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    ?: error("Missing duration")
            } finally { retriever.release() }
            require(duration >= end)
            ProbeCase(id, media, ClipRange(start, end), visual, duration,
                hashes.getOrPut(media) { sha256(media) })
        }
        return runId to cases
    }

    private fun audio(analysis: SoundAnalysis): JSONObject {
        val decision = SoundTimeline.decide(analysis)
        return JSONObject().put("problem", analysis.problem ?: JSONObject.NULL)
            .put("issue", analysis.issue?.name ?: JSONObject.NULL)
            .put("decision_issue", decision.issue?.name ?: JSONObject.NULL)
            .put("decision_problem", decision.problem ?: JSONObject.NULL)
            .put("events", JSONArray(decision.events.map { it.name }))
            .put("cues", cues(analysis.cues))
            .put("frames", JSONArray().apply {
                analysis.frames.forEach { put(JSONObject().put("start_ms", it.startMs).put("cues", cues(it.cues))) }
            }).put("broad_windows", JSONArray().apply {
                analysis.babbleWindows.forEach { put(JSONObject().put("start_ms", it.startMs).put("end_ms", it.endMs)) }
            })
    }

    private fun cues(values: List<SoundCue>) = JSONArray().apply {
        values.forEach { put(JSONObject().put("label", it.label).put("score", finite(it.score))) }
    }

    private fun interpretation(result: ClipAnalysisResult): JSONObject = JSONObject()
        .put("issue", result.issue?.name ?: JSONObject.NULL).put("problem", result.problem ?: JSONObject.NULL)
        .put("has_interpretation", result.interpretation != null)
        .put("sound", result.interpretation?.sound ?: JSONObject.NULL)
        .put("phrase", result.interpretation?.phrase ?: JSONObject.NULL)
        .put("basis", result.interpretation?.basis ?: JSONObject.NULL)
        .put("visual_observation_present", result.interpretation?.visual ?: JSONObject.NULL)
        .put("used_visual_for_phrase", result.interpretation?.basis == "AUDIO_VISUAL")

    private fun visualJson(value: VisualEvidence): JSONObject = JSONObject()
        .put("status", value.status.name).put("notice", value.notice ?: JSONObject.NULL)
        .put("frames", JSONArray().apply {
            value.frames.forEach { frame -> put(JSONObject()
                .put("requested_offset_ms", frame.requestedOffsetMs)
                .put("presentation_offset_ms", frame.presentationOffsetMs ?: JSONObject.NULL)
                .put("face_count", frame.faceCount).put("valid", frame.valid)
                .put("mouth_opening", finite(frame.mouthOpening)).put("eye_closure", finite(frame.eyeClosure))
                .put("mouth_corner_lift", finite(frame.mouthCornerLift))) }
        }).put("timeline", value.timeline?.let { timeline -> JSONObject()
            .put("ordered", timeline.ordered).put("covers_selection", timeline.coversSelection)
            .put("observed_start_ms", timeline.observedStartMs ?: JSONObject.NULL)
            .put("observed_end_ms", timeline.observedEndMs ?: JSONObject.NULL)
            .put("longest_gap_ms", timeline.longestGapMs).put("mouth_transitions", timeline.mouthTransitions)
            .put("phases", JSONArray().apply { timeline.phases.forEach { phase -> put(JSONObject()
                .put("start_ms", phase.startMs).put("end_ms", phase.endMs).put("samples", phase.samples)
                .put("single_face_samples", phase.singleFaceSamples).put("open_mouth_samples", phase.openMouthSamples)
                .put("ambiguous_samples", phase.ambiguousSamples)) } })
            .put("mouth_changes", JSONArray().apply { timeline.mouthChanges.forEach { change -> put(JSONObject()
                .put("start_ms", change.startMs).put("end_ms", change.endMs).put("opening", change.opening)) } })
        } ?: JSONObject.NULL)

    private fun memory(context: Context): JSONObject {
        val runtime = Runtime.getRuntime()
        val process = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val system = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(system)
        val result = JSONObject().put("elapsed_realtime_ms", SystemClock.elapsedRealtime())
            .put("java_used_bytes", runtime.totalMemory() - runtime.freeMemory())
            .put("java_max_bytes", runtime.maxMemory()).put("native_allocated_bytes", Debug.getNativeHeapAllocatedSize())
            .put("pss_bytes", process.totalPss * 1024L).put("system_available_bytes", system.availMem)
            .put("system_low_memory", system.lowMemory)
        try {
            File("/proc/self/status").bufferedReader().use { input ->
                repeat(128) {
                    val line = input.readLine() ?: return@use
                    if (line.startsWith("VmRSS:") || line.startsWith("VmHWM:")) {
                        val number = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull()
                        if (number != null) result.put(if (line.startsWith("VmRSS:")) "rss_bytes" else "process_lifetime_peak_rss_bytes", number * 1024)
                    }
                }
            }
        } catch (_: Exception) { /* optional process accounting unavailable */ }
        return result
    }

    private fun writeReport(target: File, report: JSONObject) {
        val partial = File(target.parentFile, "${target.name}.partial")
        require(partial.canonicalFile.parentFile == target.parentFile && partial.canonicalFile.name == partial.name)
        try {
            FileOutputStream(partial).use { output ->
                output.write(report.toString(2).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { if (partial.exists()) partial.delete() }
    }

    private fun sha256(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var count = input.read(buffer)
            while (count >= 0) { hash.update(buffer, 0, count); count = input.read(buffer) }
        }
        return hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun integer(value: JSONObject, key: String): Long = when (val number = value.get(key)) {
        is Int -> number.toLong()
        is Long -> number
        else -> error("Expected integer")
    }
    private fun keys(value: JSONObject): Set<String> = value.keys().asSequence().toSet()
    private fun finite(value: Float?): Any = value?.takeIf { it.isFinite() } ?: JSONObject.NULL
    private fun elapsedMs(start: Long): Double = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0

    companion object {
        private val ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
        private val MP4 = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}\\.mp4")
    }
}
