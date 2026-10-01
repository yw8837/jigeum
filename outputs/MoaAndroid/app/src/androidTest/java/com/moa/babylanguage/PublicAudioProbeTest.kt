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

/** Opt-in measurement on attributed external public fixtures. No recordings in the release APK.
 * Source labels are not infant intent ground truth; this reports scores, including abstention. */
class PublicAudioProbeTest {
    @Test fun reportPublicSoundEventProbes() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Optional public-media probe requires an explicit argument", args.getString("public_audio_probe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "qa-public-audio")
        val results = JSONArray()
        for (name in listOf("cry", "laugh", "babble")) {
            val source = File(directory, "$name.f32")
            if (!source.isFile) {
                results.put(JSONObject().put("source_label", name).put("status", "not_run_missing_source"))
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
                results.put(JSONObject().put("source_label", name).put("samples", pcm.size)
                    .put("milliseconds", android.os.SystemClock.elapsedRealtime() - started)
                    .put("cues", JSONArray(audio.cues.map { JSONObject().put("label", it.label).put("score", it.score.toDouble()) }))
                    .put("phrase", result.interpretation?.phrase ?: JSONObject.NULL)
                    .put("problem", result.problem ?: JSONObject.NULL)
                    .put("activeFraction", shape.activeFraction.toDouble()).put("bursts", shape.bursts))
            } finally { pcm.fill(0f); bytes.fill(0) }
        }
        File(directory, "probe-results.json").writeText(results.toString(2))
    }
}
