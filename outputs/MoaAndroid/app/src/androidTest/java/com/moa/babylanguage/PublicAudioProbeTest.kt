package com.moa.babylanguage

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import org.junit.Assert.assertTrue
import java.security.MessageDigest
import kotlin.math.abs

internal data class PublicProbePcm(val pcm: FloatArray, val sha256: String, val range: ClipRange)

/** Matches the QA host PCM contract; this helper is included only in androidTest. */
internal object PublicAudioProbeContract {
    fun requireCompletedAnalysis(audio: SoundAnalysis) {
        check(audio.issue != AnalysisIssue.MODEL_FAILURE) {
            "Production sound pipeline failed; this is not a measured abstention"
        }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    fun decode(bytes: ByteArray, entry: JSONObject): PublicProbePcm {
        val name = entry.getString("id")
        require(bytes.size % 4 == 0 && bytes.size in 64000..1280000) {
            "$name: expected aligned 1..20 second PCM (64000..1280000 bytes), got ${bytes.size}"
        }
        require(entry.getInt("sample_rate_hz") == 16000 && entry.getInt("channels") == 1 &&
            entry.getString("encoding") == "float32le") { "$name: expected mono 16kHz float32le" }
        require(entry.getInt("pcm_bytes") == bytes.size && entry.getInt("pcm_samples") == bytes.size / 4) {
            "$name: received byte/sample count differs from host manifest"
        }
        val digest = sha256(bytes)
        require(entry.getString("pcm_sha256").matches(Regex("[a-f0-9]{64}")) &&
            digest == entry.getString("pcm_sha256")) { "$name: received PCM hash mismatch" }
        val duration = (bytes.size / 4 * 1000L + 15999) / 16000
        require(entry.getLong("duration_ms") == duration) { "$name: duration differs from received PCM" }
        val pcm = FloatArray(bytes.size / 4)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(pcm)
        try {
            pcm.forEachIndexed { index, value ->
                require(value.isFinite() && abs(value) <= 1.001f) {
                    "$name: invalid PCM sample at $index: require finite amplitude <=1.001f"
                }
            }
            return PublicProbePcm(pcm, digest, ClipRange(0, duration))
        } catch (error: Exception) { pcm.fill(0f); throw error }
    }
}

/** Opt-in measurement on attributed external public fixtures. No recordings in the release APK.
 * Source labels are not infant intent ground truth; this reports scores, including abstention. */
class PublicAudioProbeTest {
    @Test fun reportPublicSoundEventProbes() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Optional public-media probe requires an explicit argument", args.getString("public_audio_probe") == "true")
        val runId = args.getString("sound_probe_run_id") ?: error("Missing sound_probe_run_id")
        require(runId.matches(Regex("[a-f0-9]{32}"))) { "Invalid sound probe run ID" }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "qa-public-audio/$runId")
        val output = File(directory, "probe-results.json")
        require(!output.exists()) { "Existing result for $runId; never count a repeated run as new evidence" }
        val results = JSONArray()
        val manifestFile = File(directory, "manifest.json")
        require(manifestFile.isFile) { "Missing manifest for sound probe run $runId" }
        val manifest = JSONArray(manifestFile.readText())
        require(manifest.length() in 1..100) { "Expected 1..100 local fixtures" }
        val entries = (0 until manifest.length()).map { manifest.getJSONObject(it) }
        val names = entries.map { it.getString("id") }
        require(names.distinct().size == names.size && names.all { it.matches(Regex("[a-z0-9_-]+")) }) {
            "Unique safe fixture IDs required"
        }
        val mismatches = mutableListOf<String>()
        val failures = mutableListOf<String>()
        for (entry in entries) {
            val name = entry.getString("id")
            var bytes = ByteArray(0)
            var pcm = FloatArray(0)
            try {
                require(entry.getString("run_id") == runId) { "$name: stale manifest run ID" }
                val source = File(directory, "$name.f32")
                require(source.isFile) { "$name: missing fixture" }
                bytes = source.readBytes()
                val decoded = PublicAudioProbeContract.decode(bytes, entry)
                pcm = decoded.pcm
                val started = android.os.SystemClock.elapsedRealtime()
                val audio = SoundAnalyzer.analyzeDetailed(context, pcm)
                PublicAudioProbeContract.requireCompletedAnalysis(audio)
                val shape = AcousticShape.from(pcm)
                val range = decoded.range
                val result = EvidenceInterpreter.interpret(audio, shape, range)
                val elapsedMs = android.os.SystemClock.elapsedRealtime() - started
                val observed = result.interpretation?.sound ?: "abstain"
                val (rawFrames, rawAudio) = rawFrames(context, pcm)
                val rawResult = EvidenceInterpreter.interpret(rawAudio, shape, range)
                if (entry.has("allowed") && (0 until entry.getJSONArray("allowed").length()).none {
                        entry.getJSONArray("allowed").getString(it) == observed
                    }) mismatches += "$name: $observed"
                results.put(JSONObject(entry.toString()).put("source_label", name).put("samples", pcm.size)
                    .put("run_id", runId).put("status", "measured").put("received_bytes", bytes.size)
                    .put("received_sha256", decoded.sha256).put("range_end_ms", range.endMs)
                    .put("milliseconds", elapsedMs)
                    .put("observed", observed)
                    .put("frame_starts_ms", JSONArray(audio.frames.map { it.startMs }))
                    .put("raw_observed", rawResult.interpretation?.sound ?: "abstain")
                    .put("raw_problem", rawResult.problem ?: JSONObject.NULL)
                    .put("frames", rawFrames)
                    .put("cues", JSONArray(audio.cues.map { JSONObject().put("label", it.label).put("score", it.score.toDouble()) }))
                    .put("phrase", result.interpretation?.phrase ?: JSONObject.NULL)
                    .put("problem", result.problem ?: JSONObject.NULL)
                    .put("activeFraction", shape.activeFraction.toDouble()).put("bursts", shape.bursts))
            } catch (error: Exception) {
                val message = "$name: ${error.javaClass.simpleName}: ${error.message}"
                failures += message
                results.put(JSONObject().put("id", name).put("run_id", runId).put("status", "error")
                    .put("received_bytes", bytes.size).put("error", message))
            } finally { pcm.fill(0f); bytes.fill(0) }
        }
        output.writeText(results.toString(2))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        if (args.getString("enforce_sound_expectations") == "true") assertTrue(mismatches.joinToString(), mismatches.isEmpty())
    }

    private fun rawFrames(context: android.content.Context, pcm: FloatArray): Pair<JSONArray, SoundAnalysis> {
        val options = AudioClassifier.AudioClassifierOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("yamnet.tflite").build())
            .setMaxResults(521).build()
        return AudioClassifier.createFromOptions(context, options).use { classifier ->
            val format = AudioData.AudioDataFormat.builder().setNumOfChannels(1).setSampleRate(16_000f).build()
            val audio = AudioData.create(format, pcm.size)
            audio.load(pcm)
            val raw = classifier.classify(audio).classificationResults()
            val timeline = raw.map { frame -> SoundFrame(frame.timestampMs().orElseThrow { IllegalStateException("Missing audio timestamp") },
                frame.classifications().flatMap { it.categories() }.mapNotNull { category ->
                    SoundAnalyzer.cueLabel(category.categoryName())?.let { SoundCue(it,category.score()) }
                }) }
            val json = JSONArray(raw.map { frame ->
                JSONArray(frame.classifications().flatMap { it.categories() }
                    .filter { it.score() >= .04f || SoundAnalyzer.cueLabel(it.categoryName()) != null }
                    .map { JSONObject().put("label", it.categoryName()).put("score", it.score().toDouble()) })
            })
            json to SoundAnalysis(emptyList(), frames=timeline)
        }
    }
}
