package com.moa.babylanguage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class RecordTransferTests {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "record_transfer_test.db"
    private val prefsName = "record_transfer_test_legacy"
    private lateinit var store: ObservationStore
    private val base = 1_750_000_000_000L
    private fun row(id: Long = base, note: String = "보호자 메모 🌱\n\"아기\"") =
        Observation(id, "옹알이", "놀이 중", note, "엄마 아빠, 나랑 같이 놀아요!", "AUDIO", "Babbling")
    private fun backup(rows: List<Observation>, age: String = "9~12개월"): ByteArray =
        ByteArrayOutputStream().also { RecordTransfer.writeJson(it, StoreSnapshot(rows, age)) }.toByteArray()
    private fun parse(bytes: ByteArray, existing: StoreSnapshot = StoreSnapshot(emptyList(), "")) =
        RecordTransfer.parseImport(ByteArrayInputStream(bytes), existing)
    private fun preview(rows: List<Observation>, age: String = "") = ImportPreview(rows, age, rows.size, 0, 0)
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: StoreException) { }
    }

    @Before fun setUp() {
        context.deleteDatabase(dbName)
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
        store = ObservationStore(context, dbName, prefsName)
    }

    @After fun tearDown() = runBlocking {
        store.close()
        context.deleteDatabase(dbName)
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
        Unit
    }

    @Test fun unicodeJsonRoundTripAndRepeatedImportsAreLossless() = runBlocking {
        val rows = listOf(row(), row(base + 1, ""))
        val preview = parse(backup(rows))
        assertEquals(rows, preview.records)
        assertEquals(2, preview.addedCount)
        store.mergeImport(preview)
        val saved = store.snapshot()
        assertEquals("9~12개월", saved.ageBand)
        val second = parse(backup(rows), saved)
        assertEquals(2, second.duplicateCount)
        assertEquals(0, second.addedCount)
        store.mergeImport(second)
        assertEquals(rows.toSet(), store.snapshot().observations.toSet())
    }

    @Test fun sameTimestampConflictStaysInOriginalDayAndDoesNotDuplicateOnRetry() = runBlocking {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.of(2024, 1, 31)
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val original = row(endOfDay, "기존")
        val incoming = row(endOfDay, "다른 기기의 기록")
        store.save(original)
        val first = parse(backup(listOf(incoming)), store.snapshot())
        assertEquals(1, first.conflictCount)
        store.mergeImport(first)
        val saved = store.snapshot()
        assertEquals(2, saved.observations.size)
        assertTrue(saved.observations.all { java.time.Instant.ofEpochMilli(it.id).atZone(zone).toLocalDate() == date })
        val again = parse(backup(listOf(incoming)), saved)
        assertEquals(1, again.duplicateCount)
        store.mergeImport(again)
        assertEquals(saved.observations, store.snapshot().observations)
        store.close()
        store = ObservationStore(context, dbName, prefsName)
        store.mergeImport(first)
        assertEquals(2, store.snapshot().observations.size)
    }

    @Test fun stalePreviewRechecksCurrentRowsWithoutOverwriting() = runBlocking {
        val incoming = row(note = "백업")
        val parsed = parse(backup(listOf(incoming)))
        store.save(row(note = "나중에 저장"))
        store.saveAgeBand("6~8개월")
        store.mergeImport(parsed)
        assertEquals(setOf("백업", "나중에 저장"), store.snapshot().observations.map { it.note }.toSet())
        assertEquals("6~8개월", store.snapshot().ageBand)
    }

    @Test fun capacityFailureRollsBackAndPreservesAgeBand() = runBlocking {
        store.mergeImport(preview((0 until 9_999).map { row(base + it, "") }, "6~8개월"))
        try {
            store.mergeImport(preview(listOf(row(base + 20_000), row(base + 20_001)), "13~18개월"))
            fail("Must reject capacity overflow")
        } catch (_: StoreException) { }
        val saved = store.snapshot()
        assertEquals(9_999, saved.observations.size)
        assertEquals("6~8개월", saved.ageBand)
        assertFalse(saved.observations.any { it.id == base + 20_000 })
    }

    @Test fun successfulMigrationClearsLegacyAndDeletedRecordsNeverReturn() = runBlocking {
        val old = JSONArray().put(JSONObject().put("id", base).put("sound", "옹알이")
            .put("context", "놀이 중").put("note", "마이그레이션"))
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().putString("events", old.toString()).putString("age_band", "9~12개월").commit()
        assertEquals(1, store.snapshot().observations.size)
        assertFalse(prefs.contains("events"))
        assertFalse(prefs.contains("age_band"))
        store.delete(base)
        store.close()
        store = ObservationStore(context, dbName, prefsName)
        assertTrue(store.snapshot().observations.isEmpty())
        assertEquals("9~12개월", store.snapshot().ageBand)
        store.deleteAll()
        assertEquals("", store.snapshot().ageBand)
    }

    @Test fun malformedLegacyRecoveryIsClearedByDeleteAll() = runBlocking {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val old = JSONArray().put(JSONObject().put("id", base).put("sound", "옹알이")
            .put("context", "놀이 중")).put(JSONObject().put("secret", "broken-private-record"))
        prefs.edit().putString("events", old.toString()).commit()
        assertNotNull(store.snapshot().warning)
        assertFalse(prefs.contains("events"))
        store.deleteAll()
        assertNull(store.snapshot().warning)
        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM meta WHERE key='legacy_recovery'", null).use {
                it.moveToFirst(); assertEquals(0, it.getInt(0))
            }
        }
    }

    @Test fun concurrentStoreInstancesSerializeWrites() = runBlocking {
        val second = ObservationStore(context, dbName, prefsName)
        try {
            (0 until 60).map { index -> async {
                (if (index % 2 == 0) store else second).save(row(base + index, ""))
            } }.awaitAll()
            assertEquals(60, store.snapshot().observations.size)
        } finally { second.close() }
    }

    @Test fun corruptImportsRejectBeforeChangingExistingData() = runBlocking {
        store.save(row())
        val valid = String(backup(listOf(row())), Charsets.UTF_8)
        val cases = listOf(valid + "{}", valid.replace("\"version\":1", "\"version\":2"),
            valid.replace("\"id\":$base", "\"id\":\"$base\""),
            valid.replace("\"id\":$base", "\"id\":$base,\"id\":$base"),
            valid.replace("\"note\":", "\"unknown\":"),
            valid.replace("\"source\":\"AUDIO\"", "\"source\":\"REMOTE\""),
            valid.replace("\"id\":$base", "\"id\":9223372036854775807"),
            valid.replace("\"modelCue\":\"Babbling\"", "\"modelCue\":\"\\ud800\""))
        cases.forEach { rejects { parse(it.toByteArray(Charsets.UTF_8)) } }
        rejects { parse(byteArrayOf(0xc3.toByte(), 0x28)) }
        assertEquals(listOf(row()), store.snapshot().observations)
    }

    @Test fun byteLimitStopsUnboundedInput() {
        val source = object : InputStream() {
            override fun read(): Int = ' '.code
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                java.util.Arrays.fill(bytes, offset, offset + length, ' '.code.toByte())
                return length
            }
        }
        rejects { RecordTransfer.parseImport(source, StoreSnapshot(emptyList(), "")) }
    }

    @Test fun oversizedStringIsRejectedBeforeReaderAllocatesTheWholeToken() {
        val json = String(backup(listOf(row(note = "NOTE_SENTINEL"))), Charsets.UTF_8)
            .replace("NOTE_SENTINEL", "x".repeat(128 * 1024))
        var readBytes = 0
        val source = object : ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, length).also { if (it > 0) readBytes += it }
        }
        rejects { RecordTransfer.parseImport(source, StoreSnapshot(emptyList(), "")) }
        assertTrue("Oversized token must stop before fully decoding it: $readBytes", readBytes <= 24 * 1024)
    }

    @Test fun csvPreservesQuotesAndDisarmsSpreadsheetFormulas() {
        val output = ByteArrayOutputStream()
        RecordTransfer.writeCsv(output, StoreSnapshot(listOf(row(note = "  =HYPERLINK(\"bad\")\n아기,메모")), ""))
        val csv = output.toString("UTF-8")
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"'  =HYPERLINK(\"\"bad\"\")\n아기,메모\""))
        assertTrue(csv.endsWith("\r\n"))
    }
}
