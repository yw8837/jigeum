package com.moa.babylanguage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExpressionBasisTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun row(basis: String = ExpressionBasis.LEGACY) =
        Observation(1_750_000_000_000, "옹알이", "0:00–0:10", "", "아아, 내 소리 들려요?", "VIDEO_FILE", "옹알이", basis)
    private fun json(record: Observation): JSONObject = JSONObject(ByteArrayOutputStream().also {
        RecordTransfer.writeJson(it, StoreSnapshot(listOf(record), ""))
    }.toString("UTF-8"))
    private fun parse(document: JSONObject) = RecordTransfer.parseImport(document.toString().byteInputStream(), StoreSnapshot(emptyList(), ""))

    @Test fun missingAndUnknownBasisNeverInferFromMediaType() {
        assertEquals(ExpressionBasis.LEGACY, row().basis)
        listOf(null, "", "VIDEO_FILE", "CAMERA", "audio_visual", "future-value").forEach {
            assertEquals(ExpressionBasis.LEGACY, ExpressionBasis.normalize(it))
            assertTrue(ExpressionBasis.label(it).contains("근거 미기록"))
        }
        assertEquals("소리를 바탕으로 예상한 아기말", ExpressionBasis.label(ExpressionBasis.AUDIO))
        assertEquals("소리와 표정을 바탕으로 예상한 아기말", ExpressionBasis.label(ExpressionBasis.AUDIO_VISUAL))
    }

    @Test fun onlyChangedSentenceIsMarkedCaregiver() {
        assertEquals(ExpressionBasis.AUDIO_VISUAL,
            ExpressionBasis.forPhrase(ExpressionBasis.AUDIO_VISUAL, "아아!", " 아아! "))
        assertEquals(ExpressionBasis.CAREGIVER,
            ExpressionBasis.forPhrase(ExpressionBasis.AUDIO_VISUAL, "아아!", "엄마 안아줘!"))
        assertEquals(ExpressionBasis.LEGACY, ExpressionBasis.forPhrase("unknown", "아아!", "아아!"))
        assertEquals("보호자가 수정한 아기말", ExpressionBasis.label(ExpressionBasis.CAREGIVER))
    }

    @Test fun versionTwoPreservesEveryBasisAndVersionOneDefaultsToLegacy() {
        for (basis in listOf(ExpressionBasis.AUDIO, ExpressionBasis.AUDIO_VISUAL, ExpressionBasis.CAREGIVER, ExpressionBasis.LEGACY)) {
            val document = json(row(basis))
            assertEquals(2, document.getInt("version"))
            assertEquals(row(basis), parse(document).records.single())
        }
        val old = json(row(ExpressionBasis.AUDIO_VISUAL)).put("version", 1)
        old.getJSONArray("records").getJSONObject(0).remove("basis")
        assertEquals(ExpressionBasis.LEGACY, parse(old).records.single().basis)
        val unknown = json(row(ExpressionBasis.AUDIO)).also {
            it.getJSONArray("records").getJSONObject(0).put("basis", "future-basis")
        }
        assertEquals(ExpressionBasis.LEGACY, parse(unknown).records.single().basis)
    }

    @Test fun savedResultRestoresBasisButOldOrUnknownStateCannotClaimVideoContribution() {
        val result = ClipInterpretation("히히!", "웃음", "관찰", "안내", "0:00–0:10", true,
            "관찰 안내", ExpressionBasis.AUDIO_VISUAL)
        assertEquals(result, InterpretationState.decode(InterpretationState.encode(result)))
        val raw = JSONObject(InterpretationState.encode(result))
        raw.remove("basis")
        assertEquals(ExpressionBasis.LEGACY, InterpretationState.decode(raw.toString())!!.basis)
        raw.put("basis", "future")
        assertEquals(ExpressionBasis.LEGACY, InterpretationState.decode(raw.toString())!!.basis)
        assertNull(InterpretationState.decode("broken state"))
    }

    @Test fun databaseOneMigratesWithoutInventingBasisAndPersistsNewBasis() = runBlocking {
        val suffix = UUID.randomUUID().toString()
        val database = "basis-$suffix.db"
        val prefs = "basis-legacy-$suffix"
        var store: ObservationStore? = null
        try {
            context.openOrCreateDatabase(database, Context.MODE_PRIVATE, null).use { db ->
                db.execSQL("CREATE TABLE observations (id INTEGER PRIMARY KEY NOT NULL, sound TEXT NOT NULL, context TEXT NOT NULL, note TEXT NOT NULL, phrase TEXT NOT NULL, source TEXT NOT NULL, model_cue TEXT NOT NULL)")
                db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
                val old = row()
                db.execSQL("INSERT INTO observations VALUES(?,?,?,?,?,?,?)", arrayOf(old.id, old.sound, old.context, old.note, old.phrase, old.source, old.modelCue))
                db.execSQL("INSERT INTO meta VALUES('migration_v1','done')")
                db.version = 1
            }
            store = ObservationStore(context, database, prefs)
            assertEquals(row(), store.snapshot().observations.single())
            store.save(row(ExpressionBasis.AUDIO_VISUAL).copy(id = row().id + 1))
            store.save(row("unrecognized").copy(id = row().id + 2))
            store.close()
            store = ObservationStore(context, database, prefs)
            val rows = store.snapshot().observations.associateBy { it.id }
            assertEquals(ExpressionBasis.AUDIO_VISUAL, rows.getValue(row().id + 1).basis)
            assertEquals(ExpressionBasis.LEGACY, rows.getValue(row().id + 2).basis)
        } finally {
            store?.close()
            context.deleteDatabase(database)
            context.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun legacyRelocatedImportOriginIsStableButDifferentKnownBasisCannotMergeSilently() {
        val original = row()
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { stream ->
            stream.writeLong(original.id)
            listOf(original.sound, original.context, original.note, original.phrase, original.source, original.modelCue).forEach {
                val encoded = it.toByteArray(Charsets.UTF_8)
                stream.writeInt(encoded.size); stream.write(encoded)
            }
        }
        val legacyOrigin = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals(legacyOrigin, RecordTransfer.importOrigin(original))
        val existing = listOf(original.copy(note = "원래 기록"), original.copy(id = original.id + 1))
        val plan = RecordTransfer.planMerge(existing, listOf(original), mapOf(legacyOrigin to original.id + 1))
        assertEquals(1, plan.duplicates)
        assertTrue(plan.additions.isEmpty())
        val known = RecordTransfer.planMerge(listOf(row(ExpressionBasis.AUDIO)), listOf(row(ExpressionBasis.AUDIO_VISUAL)))
        assertEquals(1, known.conflicts)
        assertEquals(ExpressionBasis.AUDIO_VISUAL, known.additions.single().basis)
    }

    @Test fun csvAndRenderedCardUseBasisRatherThanVideoFilePresence() {
        val audio = row(ExpressionBasis.AUDIO)
        val csv = ByteArrayOutputStream().also { RecordTransfer.writeCsv(it, StoreSnapshot(listOf(audio), "")) }.toString("UTF-8")
        assertTrue(csv.contains("표현근거"))
        assertTrue(csv.contains("소리를 바탕으로 예상한 아기말"))
        val a = TranslatedMedia.caption(audio, false)
        val same = TranslatedMedia.caption(audio.copy(source = "AUDIO"), false)
        val visual = TranslatedMedia.caption(audio.copy(basis = ExpressionBasis.AUDIO_VISUAL), false)
        val edited = TranslatedMedia.caption(audio.copy(basis = ExpressionBasis.CAREGIVER), false)
        try {
            assertTrue(a.sameAs(same))
            assertFalse(a.sameAs(visual))
            assertFalse(a.sameAs(edited))
        } finally { a.recycle(); same.recycle(); visual.recycle(); edited.recycle() }
    }
}
