package com.moa.babylanguage

import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.content.ContentResolver
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import org.json.JSONObject

/** Real MediaStore on an owned emulator. Only synthetic clips and this run's rows are deleted. */
class GalleryMediaTests {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val rows = mutableListOf<Uri>()
    private val files = mutableListOf<File>()
    private lateinit var before: Set<Long>
    @Before fun setup() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 29)
        assertTrue(runBlocking { GalleryMedia.cleanupInterrupted(context) })
        before = ownedIds()
    }
    @After fun cleanup() {
        assertTrue(runBlocking { GalleryMedia.cleanupInterrupted(context) })
        val remaining = ownedIds()
        rows.filter { it.lastPathSegment?.toLongOrNull() in remaining }.forEach { context.contentResolver.delete(it, null, null) }
        files.forEach { it.delete() }
    }
    @Suppress("DEPRECATION")
    private fun ownedIds(): Set<Long> = context.contentResolver.query(MediaStore.setIncludePending(MediaStore.Files.getContentUri("external")),
        arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
        arrayOf(context.packageName,"아가톡-%"), null)!!.use { cursor ->
        buildSet { while (cursor.moveToNext()) add(cursor.getLong(0)) }
    }
    private fun record() = Observation(System.currentTimeMillis(), "옹알이", "0:00–0:02", "", "우리 아기 목소리도 들어봐요.")

    @Test fun newVideoCopiesUseSaveDateInsteadOfOriginalRecordingDate() = runBlocking {
        val source = File(context.cacheDir, "gallery-old-${UUID.randomUUID()}.mp4").also { files += it }
        InstrumentationRegistry.getInstrumentation().context.assets.open("media-quadrants-old-date.mp4").use { input ->
            source.outputStream().use { input.copyTo(it) }
        }
        val original = source.readBytes()
        fun recordedAt(file: File): Long {
            val reader = android.media.MediaMetadataRetriever()
            return try {
                reader.setDataSource(file.path)
                val text = checkNotNull(reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DATE))
                java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss.SSSX", java.util.Locale.US).parse(text)!!.time
            } finally { reader.release() }
        }
        assertEquals(1_577_836_800_000L, recordedAt(source))
        val imported = CaptureFiles.inspect(context, Uri.fromFile(source), source, CaptureLimits.FULL)
        for (captioned in listOf(true, false)) {
            val began = System.currentTimeMillis() - 1_000
            val output = (if (captioned) TranslatedMedia.render(context, imported, ClipRange(0, 2_000), record())
                else TranslatedMedia.selectedVideo(context, imported, ClipRange(0, 2_000))).also { files += it }
            assertTrue("New MP4 must carry its save date", recordedAt(output) in began..System.currentTimeMillis())
            val saved = GalleryMedia.video(context, output, MemoryArtifacts.currentGeneration()).also { rows += it }
            context.contentResolver.query(saved, arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null)!!.use {
                assertTrue(it.moveToFirst())
                assertTrue("Gallery must list the new copy at its save date", it.getLong(0) in began..System.currentTimeMillis())
            }
        }
        assertArrayEquals("Import must remain untouched", original, source.readBytes())
    }
    private fun clip(): LocalClip {
        val file = File(context.cacheDir,"gallery-${UUID.randomUUID()}.mp4").also { files += it }
        InstrumentationRegistry.getInstrumentation().context.assets.open("media-quadrants.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        return CaptureFiles.inspect(context,Uri.fromFile(file),file,CaptureLimits.FULL)
    }
    @Test fun pngIsReadablePublishedAndVisibleInAgattokAlbumWithoutPhotoReadPermission() = runBlocking {
        val bitmap = MomentCardRenderer.frame(context,record(),clip(),ClipRange(0,2_000))
        val uri = try { GalleryMedia.image(context,bitmap,MemoryArtifacts.currentGeneration()).also { rows += it } } finally { bitmap.recycle() }
        context.contentResolver.query(uri,arrayOf(MediaStore.MediaColumns.IS_PENDING,MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.MIME_TYPE),null,null,null)!!.use {
            assertTrue(it.moveToFirst()); assertEquals(0,it.getInt(0)); assertEquals("Pictures/아가톡/",it.getString(1)); assertEquals("image/png",it.getString(2))
        }
        val saved = context.contentResolver.openInputStream(uri)!!.use(BitmapFactory::decodeStream)!!
        try { assertEquals(720,saved.width); assertTrue(saved.height in 960..4096) } finally { saved.recycle() }
    }
    @Test fun mp4PublishedWithOriginalAudioAndDurationAndRemainsAfterRecordInvalidation() = runBlocking {
        val movie = TranslatedMedia.render(context,clip(),ClipRange(123,2_123),record()).also { files += it }
        val uri = GalleryMedia.video(context,movie,MemoryArtifacts.currentGeneration()).also { rows += it }
        val copied = File(context.cacheDir,"gallery-check-${UUID.randomUUID()}.mp4").also { files += it }
        context.contentResolver.openInputStream(uri)!!.use { src -> copied.outputStream().use { src.copyTo(it) } }
        assertTrue(movie.readBytes().contentEquals(copied.readBytes()))
        val reader = android.media.MediaMetadataRetriever()
        try {
            reader.setDataSource(copied.path)
            assertEquals("yes",reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            assertTrue(kotlin.math.abs(reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()-2_000)<150)
        } finally { reader.release() }
        MemoryArtifacts.invalidatePendingExports()
        assertTrue(context.contentResolver.openInputStream(uri)!!.use { it.read() } >= 0)
    }
    @Test fun writerFailureRemovesPendingRowAndDoesNotTouchExistingRows() = runBlocking {
        try { GalleryMedia.publish(context,false,MemoryArtifacts.currentGeneration()) { it.write(byteArrayOf(1,2,3)); error("owned QA write failure") }; fail("must fail") }
        catch (_: IllegalStateException) { }
        assertEquals(before,ownedIds())
    }
    @Test fun cancellationAfterInsertRemovesPendingRow() = runBlocking {
        val writing = CompletableDeferred<Unit>(); val block = CompletableDeferred<Unit>()
        val task = launch(Dispatchers.IO) { GalleryMedia.publish(context,false,MemoryArtifacts.currentGeneration()) {
            it.write(byteArrayOf(1,2)); writing.complete(Unit); block.await()
        } }
        withTimeout(8_000) { writing.await() }
        assertEquals(before.size+1,ownedIds().size)
        task.cancelAndJoin(); assertEquals(before,ownedIds())
    }
    @Test fun staleGenerationBeforeAndDuringWritePublishesNothing() = runBlocking {
        val generation = MemoryArtifacts.currentGeneration(); MemoryArtifacts.invalidatePendingExports()
        try { GalleryMedia.publish(context,false,generation) { fail("must not write") }; fail("must fail") } catch (_: IllegalStateException) { }
        val current = MemoryArtifacts.currentGeneration()
        try { GalleryMedia.publish(context,false,current) { it.write(byteArrayOf(1)); MemoryArtifacts.invalidatePendingExports() }; fail("must fail") }
        catch (_: IllegalStateException) { }
        assertEquals(before,ownedIds())
    }
    @Test fun corruptPosterAndMissingMoviePreserveInputAndPublishNothing() = runBlocking {
        val broken=File(context.cacheDir,"gallery-broken-${UUID.randomUUID()}.mp4").also { it.writeText("owned invalid input");files += it }
        try { MomentCardRenderer.frame(context,record(),LocalClip(Uri.fromFile(broken),2_000,true),ClipRange(0,1_000)); fail("must fail") } catch (_: Exception) { }
        assertEquals("owned invalid input",broken.readText())
        try { GalleryMedia.video(context,File(context.cacheDir,"nonexistent-${UUID.randomUUID()}.mp4"),MemoryArtifacts.currentGeneration()); fail("must fail") }
        catch (_: IllegalStateException) { }
        assertEquals(before,ownedIds())
    }
    @Test fun captionPixelsInPosterUseTheSameRendererAsMovieAndEditedTextChangesCard() {
        val source=clip(); val row=record()
        val poster=MomentCardRenderer.frame(context,row,source,ClipRange(0,2_000))
        val caption=TranslatedMedia.caption(row,true)
        val edited=TranslatedMedia.caption(row.copy(phrase="엄마 아빠, 우리 같이 웃어요."),true)
        try {
            var changes=0
            for(y in 640 until minOf(caption.height,edited.height) step 3) for(x in 0 until 720 step 3) {
                assertEquals(caption.getPixel(x,y),poster.getPixel(x,y))
                if(caption.getPixel(x,y)!=edited.getPixel(x,y)) changes++
            }
            assertTrue(changes>30)
        } finally { poster.recycle();caption.recycle();edited.recycle() }
    }
    @Suppress("DEPRECATION")
    @Test fun interruptedPendingInsertIsRemovedButPublishedCopySurvivesStartupCleanup() = runBlocking {
        val orphan=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,"아가톡-${System.currentTimeMillis()}-${UUID.randomUUID()}.png")
            put(MediaStore.MediaColumns.RELATIVE_PATH,"Pictures/아가톡/");put(MediaStore.MediaColumns.MIME_TYPE,"image/png");put(MediaStore.MediaColumns.IS_PENDING,1)
        })!!
        rows += orphan
        val bitmap=TranslatedMedia.caption(record(),false)
        val complete=try { GalleryMedia.image(context,bitmap,MemoryArtifacts.currentGeneration()).also { rows += it } } finally { bitmap.recycle() }
        assertTrue(GalleryMedia.cleanupInterrupted(context))
        assertEquals(before.size+1,ownedIds().size)
        assertFalse(context.contentResolver.query(MediaStore.setIncludePending(orphan),arrayOf(MediaStore.MediaColumns._ID),null,null,null)!!.use { it.moveToFirst() })
        assertTrue(context.contentResolver.openInputStream(complete)!!.use { it.read() }>=0)
    }
    @Test fun largeFontProducesLargerReadableBodyInsteadOfShrinkingWholePhrase() {
        val row=record().copy(phrase="엄마 아빠, 내 얘기도 들어봐요. 우리 같이 웃어요.")
        val normal=TranslatedMedia.caption(row,true,1f);val large=TranslatedMedia.caption(row,true,2f)
        try { assertTrue(large.height>normal.height);assertTrue(large.height<=4096) } finally { normal.recycle();large.recycle() }
    }

    /** Android provider failures are injected only in androidTest; real MediaStore remains the backing store. */
    private fun failingProvider(failQuery: Boolean = false, onPublish: (() -> Unit)? = null): Context {
        val real = context.contentResolver
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = real.getType(uri)
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
                if (failQuery) error("owned QA provider unavailable")
                return real.query(uri,projection,selection,selectionArgs,sortOrder)
            }
            override fun insert(uri: Uri, values: ContentValues?): Uri? = real.insert(uri,values)
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("owned QA deletion unavailable")
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
                onPublish?.invoke()
                if (onPublish != null) throw CancellationException("owned QA cancelled publication")
                error("owned QA publication unavailable")
            }
            override fun openFile(uri: Uri, mode: String) = real.openFileDescriptor(uri,mode)
        }
        val wrapped = ContentResolver.wrap(provider)
        return object : ContextWrapper(context) { override fun getContentResolver() = wrapped }
    }

    @Test fun failedPublicationAndFailedRollbackRemainDeletionIntentAndRecover() = runBlocking {
        val target = failingProvider()
        try { GalleryMedia.publish(target,false,MemoryArtifacts.currentGeneration()) { it.write(byteArrayOf(1,2,3)) }; fail("must fail") }
        catch (_: IllegalStateException) { }
        val saved = context.getSharedPreferences("gallery-pending",Context.MODE_PRIVATE).all
        assertEquals(1,saved.size)
        val state = JSONObject(saved.values.single() as String)
        rows += Uri.parse(state.getString("uri"))
        assertFalse("aborted save must not be published on restart",state.getBoolean("written"))
        assertTrue(GalleryMedia.cleanupInterrupted(context)); assertEquals(before,ownedIds())
        assertTrue(context.getSharedPreferences("gallery-pending",Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test fun cancellationAfterWrittenJournalAndFailedDeletionNeverRecoversAsSuccess() = runBlocking {
        val task = launch(Dispatchers.IO) {
            val ownJob = currentCoroutineContext().job
            val target = failingProvider(onPublish = { ownJob.cancel() })
            GalleryMedia.publish(target,false,MemoryArtifacts.currentGeneration()) { it.write(byteArrayOf(1,2)) }
        }
        task.join()
        val saved = context.getSharedPreferences("gallery-pending",Context.MODE_PRIVATE).all
        assertEquals(1,saved.size)
        val state=JSONObject(saved.values.single() as String); rows += Uri.parse(state.getString("uri"))
        assertFalse(state.getBoolean("written")); assertTrue(GalleryMedia.cleanupInterrupted(context))
        assertEquals(before,ownedIds())
    }

    @Test fun providerOutageReturnsRetryableCleanupWithoutThrowingOrLosingJournal() = runBlocking {
        val token=UUID.randomUUID().toString()
        val row=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,"아가톡-${System.currentTimeMillis()}-$token.png")
            put(MediaStore.MediaColumns.RELATIVE_PATH,"Pictures/아가톡/");put(MediaStore.MediaColumns.MIME_TYPE,"image/png");put(MediaStore.MediaColumns.IS_PENDING,1)
        })!!.also { rows += it }
        val prefs=context.getSharedPreferences("gallery-pending",Context.MODE_PRIVATE)
        assertTrue(prefs.edit().putString(token,JSONObject().put("uri",row.toString()).put("legacy","").put("written",false).toString()).commit())
        assertFalse(GalleryMedia.cleanupInterrupted(failingProvider(failQuery=true)))
        assertTrue(prefs.contains(token)); assertTrue(GalleryMedia.cleanupInterrupted(context));assertFalse(prefs.contains(token))
    }

    @Suppress("DEPRECATION")
    @Test fun missingJournalUriRecoversOnlyExactLegacyPathAndPreservesFinishedCopy() = runBlocking {
        val token=UUID.randomUUID().toString()
        val bitmap=TranslatedMedia.caption(record(),false)
        val complete=try { GalleryMedia.image(context,bitmap,MemoryArtifacts.currentGeneration()).also { rows += it } } finally { bitmap.recycle() }
        val pending=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,"아가톡-${System.currentTimeMillis()}-$token.png")
            // Legacy MediaStore has no IS_PENDING and does not rename DATA to a hidden .pending file.
            put(MediaStore.MediaColumns.RELATIVE_PATH,"Pictures/아가톡/");put(MediaStore.MediaColumns.MIME_TYPE,"image/png")
        })!!.also { rows += it }
        val path=context.contentResolver.query(MediaStore.setIncludePending(pending),arrayOf(MediaStore.MediaColumns.DATA),null,null,null)!!.use { assertTrue(it.moveToFirst());it.getString(0) }
        context.contentResolver.openOutputStream(pending)!!.use { it.write(byteArrayOf(1,2)) }
        val prefs=context.getSharedPreferences("gallery-pending",Context.MODE_PRIVATE)
        assertTrue(prefs.edit().putString(token,JSONObject().put("uri","").put("legacy",path).put("written",false).toString()).commit())
        assertTrue(GalleryMedia.cleanupInterrupted(context));assertFalse(prefs.contains(token))
        assertFalse(File(path).exists());assertEquals(before.size+1,ownedIds().size)
        assertTrue(context.contentResolver.openInputStream(complete)!!.use { it.read() }>=0)
    }

    @Test fun malformedJournalCannotDeleteDifferentPublishedCopyOrPrivateInput() = runBlocking {
        val bitmap=TranslatedMedia.caption(record(),false)
        val complete=try { GalleryMedia.image(context,bitmap,MemoryArtifacts.currentGeneration()).also { rows += it } } finally { bitmap.recycle() }
        val privateInput=File(context.cacheDir,"gallery-protected-${UUID.randomUUID()}.png").also { it.writeText("owned private input");files += it }
        val token=UUID.randomUUID().toString();val prefs=context.getSharedPreferences("gallery-pending",Context.MODE_PRIVATE)
        try {
            assertTrue(prefs.edit().putString(token,JSONObject().put("uri",complete.toString()).put("legacy","").put("written",false).toString()).commit())
            assertFalse(GalleryMedia.cleanupInterrupted(context))
            assertTrue(context.contentResolver.openInputStream(complete)!!.use { it.read() }>=0)
            assertTrue(prefs.edit().putString(token,JSONObject().put("uri","").put("legacy",privateInput.path).put("written",false).toString()).commit())
            assertFalse(GalleryMedia.cleanupInterrupted(context)); assertEquals("owned private input",privateInput.readText())
        } finally { assertTrue(prefs.edit().remove(token).commit()) }
    }

    @Suppress("DEPRECATION")
    @Test fun completedPendingWriteIsRecoveredAndPublishedWithReadablePixels() = runBlocking {
        val token=UUID.randomUUID().toString()
        val row=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,"아가톡-${System.currentTimeMillis()}-$token.png")
            put(MediaStore.MediaColumns.RELATIVE_PATH,"Pictures/아가톡/");put(MediaStore.MediaColumns.MIME_TYPE,"image/png");put(MediaStore.MediaColumns.IS_PENDING,1)
        })!!.also { rows += it }
        val bitmap=TranslatedMedia.caption(record(),false)
        try { context.contentResolver.openOutputStream(row)!!.use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)) } }
        finally { bitmap.recycle() }
        val prefs=context.getSharedPreferences("gallery-pending",Context.MODE_PRIVATE)
        assertTrue(prefs.edit().putString(token,JSONObject().put("uri",row.toString()).put("legacy","").put("written",true).toString()).commit())
        assertTrue(GalleryMedia.cleanupInterrupted(context));assertFalse(prefs.contains(token))
        context.contentResolver.query(row,arrayOf(MediaStore.MediaColumns.IS_PENDING),null,null,null)!!.use { assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0)) }
        val saved=context.contentResolver.openInputStream(row)!!.use(BitmapFactory::decodeStream)!!
        try { assertEquals(720,saved.width) } finally { saved.recycle() }
    }
}
