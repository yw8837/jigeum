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

/** Opt-in measurement on attributed external public fixtures. No recordings in the release APK.
 * Source labels are not infant intent ground truth; this reports scores, including abstention. */
class PublicAudioProbeTest {
    @Test fun reportPublicSoundEventProbes() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Optional public-media probe requires an explicit argument", args.getString("public_audio_probe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "qa-public-audio").apply { mkdirs() }
        val results = JSONArray()
        val manifest = File(directory, "manifest.json").takeIf { it.isFile }?.let { JSONArray(it.readText()) }
        val entries = manifest?.let { array -> (0 until array.length()).map { array.getJSONObject(it) } }
            ?: listOf("cry", "laugh", "babble").map { JSONObject().put("id", it) }
        val mismatches = mutableListOf<String>()
        for (entry in entries) {
            val name = entry.getString("id")
            require(name.matches(Regex("[a-z0-9_-]+")))
            val source = File(directory, "$name.f32")
            if (!source.isFile) {
                results.put(JSONObject().put("source_label", name).put("status", "not_run_missing_source"))
                mismatches += "$name: missing fixture"
                continue
            }
            val bytes = source.readBytes()
            val pcm = FloatArray(bytes.size / 4)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(pcm)
            try {
                val started = android.os.SystemClock.elapsedRealtime()
                val audio = SoundAnalyzer.analyzeDetailed(context, pcm)
                val shape = AcousticShape.from(pcm)
                val range = ClipRange(0, (pcm.size * 1000L / 16_000).coerceAtMost(15_000))
                val result = EvidenceInterpreter.interpret(audio, shape, range)
                val observed = result.interpretation?.sound ?: "abstain"
                val (rawFrames, rawAudio) = rawFrames(context, pcm)
                val rawResult = EvidenceInterpreter.interpret(rawAudio, shape, range)
                if (entry.has("allowed") && (0 until entry.getJSONArray("allowed").length()).none {
                        entry.getJSONArray("allowed").getString(it) == observed
                    }) mismatches += "$name: $observed"
                results.put(JSONObject(entry.toString()).put("source_label", name).put("samples", pcm.size)
                    .put("milliseconds", android.os.SystemClock.elapsedRealtime() - started)
                    .put("observed", observed)
                    .put("frame_starts_ms", JSONArray(audio.frames.map { it.startMs }))
                    .put("raw_observed", rawResult.interpretation?.sound ?: "abstain")
                    .put("raw_problem", rawResult.problem ?: JSONObject.NULL)
                    .put("frames", rawFrames)
                    .put("cues", JSONArray(audio.cues.map { JSONObject().put("label", it.label).put("score", it.score.toDouble()) }))
                    .put("phrase", result.interpretation?.phrase ?: JSONObject.NULL)
                    .put("problem", result.problem ?: JSONObject.NULL)
                    .put("activeFraction", shape.activeFraction.toDouble()).put("bursts", shape.bursts))
            } finally { pcm.fill(0f); bytes.fill(0) }
        }
        File(directory, "probe-results.json").writeText(results.toString(2))
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
            val timeline = raw.map { frame -> SoundFrame(frame.timestampMs().orElseThrow(),
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
