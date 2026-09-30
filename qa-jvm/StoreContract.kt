package com.moa.babylanguage
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
    val importOrigins: Map<String, Long> = emptyMap()
)

internal class StoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal object ObservationStore {
 const val MAX_ROWS = 10_000
 const val MAX_TIMESTAMP = 253402300799999L
 val AGE_BANDS = setOf("", "6~8개월", "9~12개월", "13~18개월")
        internal fun validateRecord(row: Observation) {
            if (row.id <= 0 || row.id > MAX_TIMESTAMP || row.sound.length > 40 || row.context.length > 40 ||
                row.note.length > 1000 || row.phrase.length > 160 ||
                row.modelCue.length > 40 || row.source !in setOf("AUDIO", "CAMERA", "VIDEO_FILE"))
                throw StoreException("기록의 값이나 길이가 올바르지 않아요. 기존 기록은 유지됐어요.")
            listOf(row.sound, row.context, row.note, row.phrase, row.source, row.modelCue).forEach {
                if (!RecordTransfer.validUnicode(it)) throw StoreException("기록에 잘못된 문자가 있어요. 기존 기록은 유지됐어요.")
            }
        }

}
