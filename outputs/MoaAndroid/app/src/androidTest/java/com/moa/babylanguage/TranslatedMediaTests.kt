package com.moa.babylanguage

import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaCodec
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.math.sqrt

/** Real codecs, SQLite and file exports. All sources are synthetic, never infant data. */
class TranslatedMediaTests {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val token = "media-${UUID.randomUUID()}"
    private val files = mutableListOf<File>()
    private val ids = mutableListOf<Long>()
    private lateinit var store: ObservationStore
    @Before fun setup() { store = ObservationStore(context, "$token.db", token) }
    @After fun cleanup() = runBlocking {
        store.close(); context.deleteDatabase("$token.db")
        files.forEach { it.delete() }; ids.forEach { RecordMedia.delete(context, it) }
        TranslatedMedia.cleanup(context)
    }
    private fun record(phrase: String = "나도 같이 얘기할래. 내 소리 들었어?") = Observation(
        System.currentTimeMillis().also { ids += it }, "옹알이", "0:08–0:11 · 소리 관찰", "", phrase)
    private fun input(name: String = "range-silence-tone.mp4"): LocalClip {
        val file = File(context.cacheDir, "$token-$name").also { files += it }
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { src -> file.outputStream().use { src.copyTo(it) } }
        return CaptureFiles.inspect(context, Uri.fromFile(file), file, CaptureLimits.FULL)
    }
    private suspend fun verify(file: File, expectedMs: Long): android.graphics.Bitmap {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue("duration=$duration expected=$expectedMs", kotlin.math.abs(duration - expectedMs) < 150)
            assertEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            val frame = retriever.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST)!!
            assertEquals(720, frame.width); assertTrue(frame.height in 960..4096)
            val samples = VideoAudioDecoder.decode(context, Uri.fromFile(file), startMs = 0, endMs = expectedMs)!!
            assertTrue("original audio must remain audible", sqrt(samples.sumOf { it.toDouble()*it } / samples.size) > .025)
            samples.fill(0f)
            return frame
        } finally { retriever.release() }
    }

    @Test fun lateVideoRangeHasAudioCaptionAndSurvivesStoreReopen() = runBlocking {
        val clip = input(); val row = record()
        val rendered = TranslatedMedia.render(context, clip, ClipRange(8_123, 11_123), row).also { files += it }
        val frame = verify(rendered, 3_000)
        try {
            assertTrue(Color.red(frame.getPixel(15, 700)) > 225)
            var ink = 0
            for (y in 708..888) for (x in 48..672) if (Color.red(frame.getPixel(x,y)) < 140) ink++
            assertTrue("caption pixels present", ink > 400)
        } finally { frame.recycle() }
        TranslatedMedia.cleanup(context)
        assertTrue("activity startup cannot remove an output awaiting adoption", rendered.exists())
        store.save(row, rendered); assertFalse(rendered.exists()); store.close()
        store = ObservationStore(context, "$token.db", token)
        assertEquals(row, store.snapshot().observations.single())
        assertEquals(setOf(row.id), store.snapshot().mediaIds)
        val generation = MemoryArtifacts.currentGeneration()
        val exported = MemoryArtifacts.media(context, row.id, generation).also { files += it }
        assertTrue(RecordMedia.file(context,row.id).readBytes().contentEquals(exported.readBytes()))
        assertNotNull(FileProvider.getUriForFile(context, context.packageName+".files", exported))
        try { FileProvider.getUriForFile(context, context.packageName+".files", RecordMedia.file(context,row.id)); fail("private media must not be exposed") }
        catch (_: IllegalArgumentException) { }
        store.delete(row.id)
        assertFalse(RecordMedia.file(context,row.id).exists()); assertTrue(store.snapshot().observations.isEmpty())
        assertTrue("source remains unchanged", clip.ownedFile!!.exists())
    }

    @Test fun voiceOnlyRendersCardWithOriginalSound() = runBlocking {
        val clip = input()
        val audio = File(context.cacheDir, "$token.m4a").also { files += it }
        val extractor = MediaExtractor(); extractor.setDataSource(clip.ownedFile!!.path)
        val muxer = MediaMuxer(audio.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString("mime")!!.startsWith("audio/") }
            extractor.selectTrack(track)
            val out = muxer.addTrack(extractor.getTrackFormat(track)); muxer.start()
            val bytes = ByteBuffer.allocate(256*1024); val info = MediaCodec.BufferInfo()
            while (true) {
                bytes.clear(); val size = extractor.readSampleData(bytes,0); if(size < 0) break
                info.set(0,size,extractor.sampleTime,extractor.sampleFlags); muxer.writeSampleData(out,bytes,info); extractor.advance()
            }
            muxer.stop()
        } finally { muxer.release(); extractor.release() }
        val source = CaptureFiles.inspect(context,Uri.fromFile(audio),audio)
        assertFalse(source.hasVideo)
        val output=TranslatedMedia.render(context,source,ClipRange(8_000,11_000),record()).also { files += it }
        val frame=verify(output,3_000)
        try { assertTrue(Color.red(frame.getPixel(10,10)) > 225) } finally { frame.recycle() }
    }

    @Test fun videoWithoutAudioKeepsSceneCaptionAndSelectedDurationWithoutAddingAudio() = runBlocking {
        val original = input("media-quadrants.mp4")
        val silent = File(context.cacheDir, "$token-video-only.mp4").also { files += it }
        val extractor = MediaExtractor()
        val muxer = MediaMuxer(silent.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            extractor.setDataSource(original.ownedFile!!.path)
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString("mime")!!.startsWith("video/")
            }
            extractor.selectTrack(track)
            val out = muxer.addTrack(extractor.getTrackFormat(track))
            muxer.start()
            val bytes = ByteBuffer.allocate(1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                bytes.clear()
                val size = extractor.readSampleData(bytes, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
                muxer.writeSampleData(out, bytes, info)
                extractor.advance()
            }
            muxer.stop()
        } finally { muxer.release(); extractor.release() }

        fun assertVideoOnly(file: File) {
            val tracks = MediaExtractor()
            try {
                tracks.setDataSource(file.path)
                val mimes = (0 until tracks.trackCount).map { tracks.getTrackFormat(it).getString("mime")!! }
                assertEquals("one video track must be retained", 1, mimes.count { it.startsWith("video/") })
                assertFalse("audio must not be manufactured", mimes.any { it.startsWith("audio/") })
            } finally { tracks.release() }
        }
        assertVideoOnly(silent)
        val source = CaptureFiles.inspect(context, Uri.fromFile(silent), silent, CaptureLimits.FULL)
        assertTrue(source.hasVideo)
        val range = ClipRange(123, 2_123)
        val output = TranslatedMedia.render(context, source, range, record()).also { files += it }
        assertVideoOnly(output)
        val before = MediaMetadataRetriever()
        val after = MediaMetadataRetriever()
        try {
            before.setDataSource(silent.path)
            after.setDataSource(output.path)
            val duration = after.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue("duration=$duration expected=2000", kotlin.math.abs(duration - range.durationMs) < 150)
            val originalFrame = before.getFrameAtTime((range.startMs + 500) * 1000, MediaMetadataRetriever.OPTION_CLOSEST)!!
            val frame = after.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST)!!
            try {
                assertEquals(720, frame.width)
                assertTrue(frame.height in 960..4096)
                val scale = minOf(720f / originalFrame.width, 640f / originalFrame.height)
                val width = originalFrame.width * scale; val height = originalFrame.height * scale
                for (y in listOf(.25f, .75f)) for (x in listOf(.25f, .75f)) {
                    val wanted = originalFrame.getPixel((originalFrame.width * x).toInt(), (originalFrame.height * y).toInt())
                    val actual = frame.getPixel(((720 - width) / 2 + width * x).toInt(), ((640 - height) / 2 + height * y).toInt())
                    assertTrue("scene red", kotlin.math.abs(Color.red(wanted) - Color.red(actual)) < 35)
                    assertTrue("scene green", kotlin.math.abs(Color.green(wanted) - Color.green(actual)) < 35)
                    assertTrue("scene blue", kotlin.math.abs(Color.blue(wanted) - Color.blue(actual)) < 35)
                }
                var ink = 0
                for (y in 708..888) for (x in 48..672) if (Color.red(frame.getPixel(x, y)) < 140) ink++
                assertTrue("caption pixels present", ink > 400)
            } finally { originalFrame.recycle(); frame.recycle() }
        } finally { before.release(); after.release() }
    }

    @Test fun selectedVideoKeepsLateIntervalAudioAndDoesNotCreateCaptionOrRecord() = runBlocking {
        val source = input()
        val output = TranslatedMedia.selectedVideo(context, source, ClipRange(8_123, 11_123)).also { files += it }
        val reader = MediaMetadataRetriever()
        try {
            reader.setDataSource(output.path)
            assertTrue(kotlin.math.abs(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() - 3_000) < 150)
            val frame = reader.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST)!!
            try { assertEquals(source.aspectRatio, frame.width.toFloat() / frame.height, .02f) }
            finally { frame.recycle() }
        } finally { reader.release() }
        val samples = VideoAudioDecoder.decode(context, Uri.fromFile(output), 0, 3_000)!!
        try { assertTrue(sqrt(samples.sumOf { it.toDouble() * it } / samples.size) > .025) }
        finally { samples.fill(0f) }
        assertTrue(store.snapshot().observations.isEmpty())
        assertTrue(source.ownedFile!!.exists())
    }

    @Test fun selectedVideoWithoutAudioNeverAddsAudioTrack() = runBlocking {
        val source = input("no-audio.mp4")
        val output = TranslatedMedia.selectedVideo(context, source, ClipRange(123, 1_123)).also { files += it }
        val reader = MediaExtractor()
        try {
            reader.setDataSource(output.path)
            val types = (0 until reader.trackCount).map { reader.getTrackFormat(it).getString("mime")!! }
            assertTrue(types.any { it.startsWith("video/") })
            assertFalse(types.any { it.startsWith("audio/") })
        } finally { reader.release() }
    }

    @Test fun selectedVideoCancellationAndCorruptSourceLeaveNoPartialOutput() = runBlocking {
        val source = input("temporal-red-blue-device-20.mp4")
        val folder = File(context.cacheDir, "media-render")
        val before = folder.listFiles()?.map { it.name }?.toSet().orEmpty()
        val job = launch { TranslatedMedia.selectedVideo(context, source, ClipRange(0, 20_000)).also { files += it } }
        delay(30); job.cancelAndJoin()
        assertEquals(before, folder.listFiles()?.map { it.name }?.toSet().orEmpty())
        val broken = File(context.cacheDir, "$token-corrupt.mp4").also { it.writeText("synthetic corrupt input"); files += it }
        try {
            TranslatedMedia.selectedVideo(context, LocalClip(Uri.fromFile(broken), 2_000, true), ClipRange(0, 2_000))
            fail("corrupt source must fail")
        } catch (_: StoreException) { }
        assertEquals(before, folder.listFiles()?.map { it.name }?.toSet().orEmpty())
        assertEquals("synthetic corrupt input", broken.readText())
        assertTrue(source.ownedFile!!.exists())
    }

    @Test fun extremeCaptionFitsAndLeavesSceneTransparent() {
        for (text in listOf("가".repeat(160), "안녕😀\n".repeat(25), "W".repeat(160))) {
            for (video in listOf(true,false)) {
                val frame=TranslatedMedia.caption(record(text),video)
                try {
                    assertEquals(if(video) 0 else 255, Color.alpha(frame.getPixel(20,20)))
                    // The measured body must end before the metadata/footer margin.
                    for (x in 0 until 720) assertEquals(0xFFFFF9F0.toInt(),frame.getPixel(x,frame.height-60))
                } finally { frame.recycle() }
            }
        }
    }

    @Test fun duplicateAndInvalidSavePreserveExistingRecordAndMedia() = runBlocking {
        val row=record()
        val source=input()
        val output=TranslatedMedia.render(context,source,ClipRange(8_000,9_000),row).also { files += it }
        store.save(row,output)
        val bytes=RecordMedia.file(context,row.id).readBytes()
        val fake=File(context.cacheDir,"media-render/$token.mp4").also { it.parentFile!!.mkdirs(); it.writeBytes(bytes); files += it }
        try { store.save(row.copy(phrase="다른 문장"),fake); fail("duplicate must fail") } catch(_: StoreException) { }
        assertEquals(row,store.snapshot().observations.single()); assertTrue(bytes.contentEquals(RecordMedia.file(context,row.id).readBytes()))
        try { store.save(row.copy(id=row.id+1,phrase="가".repeat(161)),fake); fail("invalid record must fail") } catch(_: StoreException) { }
        assertFalse(RecordMedia.file(context,row.id+1).exists()); assertTrue(fake.exists())
        store.deleteAll(); assertFalse(RecordMedia.file(context,row.id).exists())
    }

    @Test fun cancelCorruptAndStaleExportsLeaveNoPartialFiles() = runBlocking {
        val source=input("capture-two-minute-tone.mp4")
        val job=launch(Dispatchers.Default) { TranslatedMedia.render(context,source,ClipRange(60_000,80_000),record()) }
        delay(400); job.cancelAndJoin()
        assertTrue(File(context.cacheDir,"media-render").listFiles().orEmpty().isEmpty())
        val broken=File(context.cacheDir,"$token-broken.mp4").also { it.writeText("invalid media"); files += it }
        try { TranslatedMedia.render(context,LocalClip(Uri.fromFile(broken),3_000,true),ClipRange(0,2_000),record()); fail("corrupt input") }
        catch(_: StoreException) { }
        assertTrue(broken.exists()); assertTrue(File(context.cacheDir,"media-render").listFiles().orEmpty().isEmpty())
        val row=record(); val output=TranslatedMedia.render(context,source,ClipRange(60_000,61_000),row).also { files += it }
        store.save(row,output)
        val stale=MemoryArtifacts.currentGeneration(); MemoryArtifacts.invalidatePendingExports()
        val before=File(context.cacheDir,"exports").listFiles().orEmpty().map { it.name }.toSet()
        try { MemoryArtifacts.media(context,row.id,stale); fail("stale generation") } catch(_: IllegalStateException) { }
        assertEquals(before,File(context.cacheDir,"exports").listFiles().orEmpty().map { it.name }.toSet())
    }

    @Test fun failedFileDeletionKeepsRetryIntentAndAccurateSnapshot() = runBlocking {
        val row=record(); store.save(row)
        // A non-empty directory at the owned path deterministically simulates unlink failure.
        val blocked=RecordMedia.file(context,row.id).apply { mkdirs() }
        val child=File(blocked,"owned-test").apply { writeText("QA") }
        try {
            store.delete(row.id)
            assertTrue(store.snapshot().observations.isEmpty())
            assertTrue(store.snapshot().warning!!.contains("영상 파일 정리"))
            store.close(); store=ObservationStore(context,"$token.db",token)
            assertNotNull(store.snapshot().warning)
            child.delete()
            assertNull(store.snapshot().warning)
            assertFalse(blocked.exists())
        } finally { child.delete(); blocked.delete() }
    }

    @Test fun completeSceneSurvivesLandscapeAndRotation() = runBlocking {
        for(name in listOf("media-quadrants.mp4","media-quadrants-rotated.mp4")) {
            val source=input(name)
            val retriever=MediaMetadataRetriever()
            retriever.setDataSource(source.ownedFile!!.path)
            val original=try { retriever.getFrameAtTime(500_000,MediaMetadataRetriever.OPTION_CLOSEST)!! } finally { retriever.release() }
            val output=TranslatedMedia.render(context,source,ClipRange(123,2_123),record()).also { files += it }
            val frame=verify(output,2_000)
            try {
                val scale=minOf(720f/original.width,640f/original.height)
                val w=original.width*scale; val h=original.height*scale
                for(y in listOf(.05f,.25f,.75f,.95f)) for(x in listOf(.05f,.25f,.75f,.95f)) {
                    val wanted=original.getPixel((original.width*x).toInt(),(original.height*y).toInt())
                    val actual=frame.getPixel(((720-w)/2+w*x).toInt(),((640-h)/2+h*y).toInt())
                    assertTrue("$name quadrant $x,$y red",kotlin.math.abs(Color.red(wanted)-Color.red(actual))<35)
                    assertTrue("$name quadrant $x,$y green",kotlin.math.abs(Color.green(wanted)-Color.green(actual))<35)
                    assertTrue("$name quadrant $x,$y blue",kotlin.math.abs(Color.blue(wanted)-Color.blue(actual))<35)
                }
            } finally { original.recycle(); frame.recycle() }
        }
    }
}
