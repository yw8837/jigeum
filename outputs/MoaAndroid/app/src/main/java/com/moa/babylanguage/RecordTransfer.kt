package com.moa.babylanguage

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.time.ZoneId

internal data class ImportPreview(
    val records: List<Observation>, val ageBand: String,
    val addedCount: Int, val duplicateCount: Int, val conflictCount: Int
)

/** Streaming, bounded backups. Caller owns the streams and the SAF destination. */
internal object RecordTransfer {
    internal const val MAX_INPUT_BYTES = 48L * 1024 * 1024
    private const val FORMAT = "jigeum-observations"

    fun parseImport(input: InputStream, existing: StoreSnapshot): ImportPreview {
        try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            val reader = JsonReader(InputStreamReader(BoundedInput(input), decoder)).apply { isLenient = false }
            val seen = HashSet<String>()
            var format: String? = null
            var version: Long? = null
            var age: String? = null
            var rows: List<Observation>? = null
            reader.beginObject()
            while (reader.hasNext()) {
                val key = reader.nextName()
                require(seen.add(key))
                when (key) {
                    "format" -> format = string(reader, 40)
                    "version" -> version = integer(reader)
                    "ageBand" -> age = string(reader, 20)
                    "records" -> {
                        val items = ArrayList<Observation>()
                        reader.beginArray()
                        while (reader.hasNext()) {
                            require(items.size < ObservationStore.MAX_ROWS)
                            items += readRecord(reader)
                        }
                        reader.endArray()
                        rows = items
                    }
                    else -> throw IllegalArgumentException()
                }
            }
            reader.endObject()
            require(reader.peek() == JsonToken.END_DOCUMENT)
            require(format == FORMAT && version == 1L && age in ObservationStore.AGE_BANDS && rows != null)
            val plan = planMerge(existing.observations, rows)
            return ImportPreview(rows, age!!, plan.added, plan.duplicates, plan.conflicts)
        } catch (cause: StoreException) {
            throw cause
        } catch (cause: Exception) {
            throw StoreException("백업 파일 형식이나 크기가 올바르지 않아요. 앱에서 내보낸 JSON 백업을 골라 주세요. 기존 기록은 그대로 있어요.", cause)
        }
    }

    fun writeJson(output: OutputStream, snapshot: StoreSnapshot) {
        validateSnapshot(snapshot)
        val writer = JsonWriter(OutputStreamWriter(output, Charsets.UTF_8))
        writer.beginObject().name("format").value(FORMAT).name("version").value(1L)
            .name("ageBand").value(snapshot.ageBand).name("records").beginArray()
        snapshot.observations.forEach { row ->
            writer.beginObject().name("id").value(row.id).name("sound").value(row.sound)
                .name("context").value(row.context).name("note").value(row.note)
                .name("phrase").value(row.phrase).name("source").value(row.source)
                .name("modelCue").value(row.modelCue).endObject()
        }
        writer.endArray().endObject()
        writer.flush()
    }

    fun writeCsv(output: OutputStream, snapshot: StoreSnapshot) {
        validateSnapshot(snapshot)
        val writer = OutputStreamWriter(output, Charsets.UTF_8)
        writer.write("\uFEFF")
        writer.write("기록번호,날짜,소리,상황,아기말,보호자메모,입력방식,모델단서\r\n")
        snapshot.observations.forEach { row ->
            val fields = listOf(row.id.toString(), Instant.ofEpochMilli(row.id).atZone(ZoneId.systemDefault()).toString(),
                row.sound, row.context, row.phrase, row.note, row.source, row.modelCue)
            writer.write(fields.joinToString(",") { csvCell(it) } + "\r\n")
        }
        writer.flush()
    }

    private fun csvCell(value: String): String {
        // Spreadsheet applications can execute quoted formulas too. Neutralize before quoting.
        val first = value.dropWhile { it.isWhitespace() || it == '\uFEFF' || it < ' ' }.firstOrNull()
        val safe = if (first in listOf('=', '+', '-', '@') || value.firstOrNull() in listOf('\t', '\r', '\n')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }

    private fun validateSnapshot(snapshot: StoreSnapshot) {
        require(snapshot.ageBand in ObservationStore.AGE_BANDS && snapshot.observations.size <= ObservationStore.MAX_ROWS)
        snapshot.observations.forEach(ObservationStore::validateRecord)
        require(snapshot.observations.map { it.id }.toSet().size == snapshot.observations.size)
    }

    private fun string(reader: JsonReader, max: Int): String {
        require(reader.peek() == JsonToken.STRING)
        return reader.nextString().also { require(it.length <= max && validUnicode(it)) }
    }

    private fun integer(reader: JsonReader): Long {
        require(reader.peek() == JsonToken.NUMBER)
        val text = reader.nextString()
        require(text.matches(Regex("[0-9]{1,16}")))
        return text.toLong()
    }

    private fun readRecord(reader: JsonReader): Observation {
        val seen = HashSet<String>()
        var id: Long? = null
        val strings = HashMap<String, String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            require(seen.add(key))
            when (key) {
                "id" -> id = integer(reader)
                "sound", "context", "modelCue" -> strings[key] = string(reader, 40)
                "note" -> strings[key] = string(reader, 1000)
                "phrase" -> strings[key] = string(reader, 160)
                "source" -> strings[key] = string(reader, 20)
                else -> throw IllegalArgumentException()
            }
        }
        reader.endObject()
        require(id != null && strings.keys == setOf("sound", "context", "modelCue", "note", "phrase", "source"))
        return Observation(id, strings.getValue("sound"), strings.getValue("context"), strings.getValue("note"),
            strings.getValue("phrase"), strings.getValue("source"), strings.getValue("modelCue"))
            .also(ObservationStore::validateRecord)
    }

    internal data class MergePlan(val additions: List<Observation>, val added: Int, val duplicates: Int, val conflicts: Int)

    /** Shared by preview and transactional import, so stale previews are rechecked on commit. */
    internal fun planMerge(existing: List<Observation>, incoming: List<Observation>): MergePlan {
        val current = existing.associateBy { it.id }.toMutableMap()
        val zone = ZoneId.systemDefault()
        fun day(id: Long) = Instant.ofEpochMilli(id).atZone(zone).toLocalDate()
        fun key(row: Observation) = day(row.id) to row.copy(id = 0)
        val contents = current.values.map { key(it) }.toHashSet()
        val additions = ArrayList<Observation>()
        var added = 0
        var duplicates = 0
        var conflicts = 0
        for (row in incoming) {
            ObservationStore.validateRecord(row)
            val atId = current[row.id]
            if (atId == row || (atId != null && key(row) in contents)) { duplicates++; continue }
            if (current.size >= ObservationStore.MAX_ROWS) throw StoreException("가져온 기록을 합치면 10,000건을 넘어요. 기존 기록은 유지됐어요.")
            val merged = if (atId == null) { added++; row } else {
                conflicts++
                // IDs are timestamps. Never move an old memory into today's album on collision.
                val date = day(row.id)
                val start = maxOf(1L, date.atStartOfDay(zone).toInstant().toEpochMilli())
                val end = minOf(ObservationStore.MAX_TIMESTAMP, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)
                var candidate = row.id
                do { candidate = if (candidate == end) start else candidate + 1 } while (candidate in current && candidate != row.id)
                if (candidate == row.id) throw StoreException("같은 날짜의 기록을 합치지 못했어요. 기존 기록은 유지됐어요.")
                row.copy(id = candidate)
            }
            current[merged.id] = merged
            contents += key(merged)
            additions += merged
        }
        return MergePlan(additions, added, duplicates, conflicts)
    }

    internal fun validUnicode(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val char = value[index++]
            if (Character.isHighSurrogate(char)) {
                if (index == value.length || !Character.isLowSurrogate(value[index++])) return false
            } else if (Character.isLowSurrogate(char) || (char < ' ' && char !in listOf('\n', '\r', '\t'))) return false
        }
        return true
    }

    private class BoundedInput(input: InputStream) : FilterInputStream(input) {
        private var count = 0L
        private var inString = false
        private var escaped = false
        private var tokenBytes = 0
        private var scalarBytes = 0
        override fun read(): Int = `in`.read().also {
            if (it >= 0) { checkCount(1); checkToken(it) }
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = `in`.read(buffer, offset, length).also { size ->
            if (size > 0) {
                checkCount(size)
                for (index in offset until offset + size) checkToken(buffer[index].toInt() and 0xff)
            }
        }
        private fun checkCount(size: Int) { count += size; require(count <= MAX_INPUT_BYTES) }
        private fun checkToken(byte: Int) {
            // Inspect bytes before the decoder/JsonReader can allocate an oversized token.
            // A legal 1,000-code-unit note needs at most 6,000 JSON-escaped bytes.
            // Quote/backslash ASCII bytes cannot occur inside a UTF-8 continuation byte.
            if (!inString) {
                if (byte == '"'.code) { inString = true; tokenBytes = 0; escaped = false; scalarBytes = 0 }
                else if (byte <= 32 || byte == 123 || byte == 125 || byte == 91 || byte == 93 || byte == 58 || byte == 44) scalarBytes = 0
                else require(++scalarBytes <= 64)
            } else {
                require(++tokenBytes <= 8_192)
                if (escaped) escaped = false
                else if (byte == '\\'.code) escaped = true
                else if (byte == '"'.code) inString = false
            }
        }
    }
}
