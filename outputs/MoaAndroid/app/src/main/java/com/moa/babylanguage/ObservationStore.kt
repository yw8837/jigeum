package com.moa.babylanguage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.io.File

internal data class Observation(
    val id: Long,
    val sound: String,
    val context: String,
    val note: String,
    val phrase: String = "",
    val source: String = "AUDIO",
    val modelCue: String = ""
)

internal data class StoreSnapshot(
    val observations: List<Observation>,
    val ageBand: String,
    val warning: String? = null,
    // Local-only provenance for relocated imports; never included in JSON/CSV exports.
    val importOrigins: Map<String, Long> = emptyMap(),
    val mediaIds: Set<Long> = emptySet()
)

internal class StoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * IO-only, serialized across instances. Migration commits records and recovery copies
 * before clearing legacy preferences. Deletion also covers recovery copies.
 */
internal class ObservationStore(context: Context,
    databaseName: String = "moa_observations.db",
    legacyName: String = "moa_observations"
) {
    private val appContext = context.applicationContext
    private val dbHelper = Helper(appContext, databaseName)
    private val mutex = locks.getOrPut(appContext.getDatabasePath(databaseName).absolutePath) { Mutex() }
    private val legacy = appContext.getSharedPreferences(legacyName, Context.MODE_PRIVATE)

    suspend fun close(): Unit = withContext(Dispatchers.IO) { mutex.withLock { dbHelper.close() } }

    suspend fun snapshot(): StoreSnapshot = onStore { db ->
        val rows = ArrayList<Observation>()
        db.query("observations", COLUMNS, null, null, null, null, "id DESC").use { cursor ->
            while (cursor.moveToNext()) {
                if (rows.size == MAX_ROWS) throw StoreException("기록이 10,000건을 넘습니다. 저장소를 점검해 주세요.")
                rows += Observation(
                    cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                    cursor.getString(3), cursor.getString(4), cursor.getString(5), cursor.getString(6)
                )
            }
        }
        val mediaWarning = cleanupDeletedMedia(db)
        StoreSnapshot(rows, meta(db, "age_band") ?: "", mediaWarning ?: meta(db, "migration_warning"), importOrigins(db), RecordMedia.ids(appContext, rows))
    }

    suspend fun save(record: Observation, renderedMedia: File? = null): Unit = onStore { db ->
        validateRecord(record)
        cleanupDeletedMedia(db)
        check(meta(db, "delete_media:${record.id}") == null) { "이전 영상 정리가 끝나지 않았어요. 잠시 후 다시 저장해 주세요." }
        var adopted = false
        try { transaction(db) {
            if (count(db) >= MAX_ROWS) throw StoreException("기록은 최대 10,000건까지 저장할 수 있어요. 기존 기록은 유지됐어요.")
            if (renderedMedia != null) { RecordMedia.adopt(appContext, record.id, renderedMedia); adopted = true }
            try { db.insertOrThrow("observations", null, values(record)) }
            catch (cause: android.database.sqlite.SQLiteConstraintException) {
                throw StoreException("같은 번호의 기록이 이미 있어요. 다시 시도해 주세요.", cause)
            }
        } } catch (error: Throwable) {
            if (adopted) RecordMedia.delete(appContext, record.id)
            throw error
        }
    }

    suspend fun delete(id: Long): Unit = onStore { db ->
        transaction(db) {
            db.delete("observations", "id=?", arrayOf(id.toString()))
            putMeta(db, "delete_media:$id", id.toString())
            db.delete("meta", "key GLOB ? AND value=?", arrayOf("import_origin:*", id.toString()))
            meta(db, "legacy_recovery")?.let { raw ->
                val old = JSONArray(raw)
                val retained = JSONArray()
                for (index in 0 until old.length()) {
                    val item = old.opt(index)
                    if (item !is JSONObject || item.optLong("id", -1L) != id) retained.put(item)
                }
                if (retained.length() == 0)
                    db.delete("meta", "key IN (?,?)", arrayOf("legacy_recovery", "migration_warning"))
                else putMeta(db, "legacy_recovery", retained.toString())
            }
        }
        cleanupDeletedMedia(db)
        Unit
    }

    /** Matches the old 'all records' action, including the selected age band. */
    suspend fun deleteAll(): Unit = onStore { db ->
        transaction(db) {
            RecordMedia.allIds(appContext).forEach { putMeta(db, "delete_media:$it", it.toString()) }
            db.delete("observations", null, null)
            db.delete("meta", "key GLOB ?", arrayOf("import_origin:*"))
            db.delete("meta", "key IN (?,?)", arrayOf("legacy_recovery", "migration_warning"))
            putMeta(db, "age_band", "")
        }
        cleanupDeletedMedia(db)
        Unit
    }

    /** Commit deletion intent with the row; failed media cleanup stays retryable after restart. */
    private fun cleanupDeletedMedia(db: SQLiteDatabase): String? {
        val pending = ArrayList<Pair<String, Long>>()
        db.rawQuery("SELECT key,value FROM meta WHERE key GLOB ?", arrayOf("delete_media:*")).use { cursor ->
            while (cursor.moveToNext()) pending += cursor.getString(0) to cursor.getString(1).toLong()
        }
        var incomplete = false
        pending.forEach { (key, id) ->
            if (runCatching { RecordMedia.delete(appContext, id) }.isSuccess) db.delete("meta", "key=?", arrayOf(key))
            else incomplete = true
        }
        return if (incomplete) "기록은 지웠지만 영상 파일 정리가 끝나지 않았어요. 다음 실행에서 다시 정리해요. 저장 공간을 확인해 주세요." else null
    }

    suspend fun cleanupMedia(): Unit = onStore { db ->
        val retained = HashSet<Long>()
        db.rawQuery("SELECT id FROM observations", null).use { cursor -> while (cursor.moveToNext()) retained += cursor.getLong(0) }
        RecordMedia.allIds(appContext).filter { it !in retained }.forEach { putMeta(db, "delete_media:$it", it.toString()) }
        cleanupDeletedMedia(db)
        Unit
    }

    suspend fun saveAgeBand(value: String): Unit = onStore { db ->
        if (value !in AGE_BANDS) throw StoreException("월령 범위를 다시 골라 주세요.")
        transaction(db) { putMeta(db, "age_band", value) }
    }

    /**
     * Recheck every imported row against the current DB within one transaction. If anything
     * fails, SQLite rolls back records, local import origins and age band together.
     */
    suspend fun mergeImport(imported: ImportPreview): Unit = onStore { db ->
        cleanupDeletedMedia(db)
        if (imported.records.size > MAX_ROWS || imported.ageBand !in AGE_BANDS)
            throw StoreException("가져오기 파일의 기록 수 또는 월령이 올바르지 않아요.")
        imported.records.forEach(::validateRecord)
        transaction(db) {
            val current = HashMap<Long, Observation>()
            db.query("observations", COLUMNS, null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    if (current.size == MAX_ROWS) throw StoreException("기록이 10,000건을 넘습니다.")
                    val row = Observation(cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getString(4), cursor.getString(5), cursor.getString(6))
                    current[row.id] = row
                }
            }
            val plan = RecordTransfer.planMerge(current.values.toList(), imported.records, importOrigins(db))
            plan.additions.forEach {
                if (meta(db, "delete_media:${it.id}") != null) throw StoreException("이전 영상 정리를 마친 뒤 백업을 가져와 주세요.")
                db.insertOrThrow("observations", null, values(it))
            }
            plan.importOrigins.forEach { (origin, target) -> putMeta(db, "import_origin:$origin", target.toString()) }
            if ((meta(db, "age_band") ?: "").isBlank() && imported.ageBand.isNotBlank())
                putMeta(db, "age_band", imported.ageBand)
        }
    }

    private suspend fun <T> onStore(block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val db = dbHelper.writableDatabase
                migrateIfNeeded(db)
                block(db)
            } catch (cause: StoreException) {
                throw cause
            } catch (cause: Exception) {
                throw StoreException("기기 기록을 처리하지 못했어요. 기존 기록은 유지됐는지 확인해 주세요.", cause)
            }
        }
    }

    private fun migrateIfNeeded(db: SQLiteDatabase) {
        if (meta(db, "migration_v1") == "done") {
            clearLegacy()
            return
        }
        val raw = try { legacy.getString("events", "[]") ?: "[]" }
            catch (cause: ClassCastException) { throw StoreException("기존 기록 형식을 읽지 못했어요. 원본은 보관돼 있어요.", cause) }
        val oldAge = try { legacy.getString("age_band", "") ?: "" }
            catch (_: ClassCastException) { "" }
        val array = try { JSONArray(raw) }
            catch (cause: Exception) { throw StoreException("기존 기록 파일이 손상돼 이전하지 못했어요. 원본은 보관돼 있어요.", cause) }
        if (array.length() > MAX_ROWS) throw StoreException("기존 기록이 10,000건을 넘어 자동 이전을 중단했어요. 원본은 보관돼 있어요.")
        var invalid = 0
        val recovery = JSONArray()
        val seen = HashSet<Long>()
        val valid = ArrayList<Observation>(array.length())
        for (i in 0 until array.length()) {
            try {
                val item = array.getJSONObject(i)
                val id = item.getLong("id")
                if (id <= 0 || id in seen) { invalid++; recovery.put(item); continue }
                val record = Observation(
                    id, item.getString("sound"), item.getString("context"),
                    item.optString("note"), item.optString("phrase"),
                    item.optString("source", "AUDIO"), item.optString("modelCue")
                )
                validateRecord(record)
                seen.add(id)
                valid += record
            } catch (_: Exception) { invalid++; recovery.put(array.opt(i)) }
        }
        transaction(db) {
            if (count(db) + valid.size > MAX_ROWS)
                throw StoreException("기존 기록을 합치면 10,000건을 넘어 이전을 중단했어요. 원본은 보관돼 있어요.")
            valid.forEach { db.insertWithOnConflict("observations", null, values(it), SQLiteDatabase.CONFLICT_IGNORE) }
            putMeta(db, "age_band", if (oldAge in AGE_BANDS) oldAge else "")
            if (invalid > 0) {
                putMeta(db, "legacy_recovery", recovery.toString())
                putMeta(db, "migration_warning", "기존 기록에서 손상되거나 중복된 항목 ${invalid}건을 건너뛰었어요. 해당 원본은 기기에 보관돼 있으며 전체 기록 삭제 시 함께 지워져요.")
            }
            putMeta(db, "migration_v1", "done")
        }
        clearLegacy()
    }

    private fun clearLegacy() {
        if ((legacy.contains("events") || legacy.contains("age_band")) &&
            !legacy.edit().remove("events").remove("age_band").commit())
            throw StoreException("이전 기록 사본을 정리하지 못했어요. 저장 공간을 확인하고 다시 시도해 주세요.")
    }

    private class Helper(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 1) {
        override fun onConfigure(db: SQLiteDatabase) {
            db.rawQuery("PRAGMA secure_delete=ON", null).use { it.moveToFirst() }
        }
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE observations (id INTEGER PRIMARY KEY NOT NULL, sound TEXT NOT NULL, context TEXT NOT NULL, note TEXT NOT NULL, phrase TEXT NOT NULL, source TEXT NOT NULL, model_cue TEXT NOT NULL)")
            db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            throw StoreException("기록 저장소 버전을 자동으로 변경할 수 없어요.")
        }
    }

    companion object {
        internal const val MAX_ROWS = 10_000
        internal const val MAX_TIMESTAMP = 253402300799999L
        private val locks = ConcurrentHashMap<String, Mutex>()
        private val COLUMNS = arrayOf("id", "sound", "context", "note", "phrase", "source", "model_cue")
        internal val AGE_BANDS = setOf("", "6~8개월", "9~12개월", "13~18개월")

        internal fun validateRecord(row: Observation) {
            if (row.id <= 0 || row.id > MAX_TIMESTAMP || row.sound.length > 40 || row.context.length > 40 ||
                row.note.length > 1000 || row.phrase.length > 160 ||
                row.modelCue.length > 40 || row.source !in setOf("AUDIO", "CAMERA", "VIDEO_FILE"))
                throw StoreException("기록의 값이나 길이가 올바르지 않아요. 기존 기록은 유지됐어요.")
            listOf(row.sound, row.context, row.note, row.phrase, row.source, row.modelCue).forEach {
                if (!RecordTransfer.validUnicode(it)) throw StoreException("기록에 잘못된 문자가 있어요. 기존 기록은 유지됐어요.")
            }
        }

        private fun values(row: Observation) = ContentValues().apply {
            put("id", row.id); put("sound", row.sound); put("context", row.context)
            put("note", row.note); put("phrase", row.phrase); put("source", row.source)
            put("model_cue", row.modelCue)
        }

        private fun count(db: SQLiteDatabase): Int =
            db.rawQuery("SELECT COUNT(*) FROM observations", null).use {
                it.moveToFirst(); it.getInt(0)
            }

        private fun meta(db: SQLiteDatabase, key: String): String? =
            db.rawQuery("SELECT value FROM meta WHERE key=?", arrayOf(key)).use {
                if (it.moveToFirst()) it.getString(0) else null
            }

        private fun importOrigins(db: SQLiteDatabase): Map<String, Long> {
            val origins = HashMap<String, Long>()
            db.rawQuery("SELECT key,value FROM meta WHERE key GLOB ?", arrayOf("import_origin:*")).use { cursor ->
                var entries = 0
                while (cursor.moveToNext()) {
                    if (++entries > MAX_ROWS) throw StoreException("가져오기 기록 정보가 10,000건을 넘습니다. 저장소를 점검해 주세요.")
                    val target = cursor.getString(1).toLongOrNull() ?: continue
                    origins[cursor.getString(0).removePrefix("import_origin:")] = target
                }
            }
            return origins
        }

        private fun putMeta(db: SQLiteDatabase, key: String, value: String) {
            db.execSQL("INSERT OR REPLACE INTO meta(key,value) VALUES(?,?)", arrayOf(key, value))
        }

        private inline fun <T> transaction(db: SQLiteDatabase, block: () -> T): T {
            db.beginTransaction()
            try {
                val value = block()
                db.setTransactionSuccessful()
                return value
            } finally { db.endTransaction() }
        }
    }
}
