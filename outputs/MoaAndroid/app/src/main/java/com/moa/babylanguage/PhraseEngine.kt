package com.moa.babylanguage

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Offers first-person conversation starters for a caregiver to check and edit.
 * These lines are not a classification of a baby's intent, emotion, or health.
 */
class PhraseEngine(
    context: Context,
    preferencesName: String = "moa_phrase_engine"
) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun candidates(sound: String, context: String, sessionId: String): List<String> = synchronized(lock) {
        val soundKey = sound.takeIf { it.length <= 80 }?.trim()
            ?.takeIf { it in soundOpenings } ?: "기타 소리"
        val contextKey = context.takeIf { it.length <= 80 }?.trim()
            ?.takeIf { it in contextClosings } ?: "기타 상황"
        val bucket = "$soundKey|$contextKey"
        val phrases = soundOpenings.getValue(soundKey).flatMap { opening ->
            contextClosings.getValue(contextKey).map { closing -> opening + closing }
        }
        val state = readState()
        // Persist only a fixed-size digest, even if the caller supplies a long ID.
        val key = "$bucket|${digest(sessionId)}"
        state.sessions[key]?.let { indices ->
            if (indices.size == 3 && indices.distinct().size == 3 && indices.all { it in phrases.indices }) {
                return@synchronized indices.map(phrases::get)
            }
        }

        val recent = state.recent[bucket].orEmpty()
        val unused = phrases.indices.filterNot { it in recent }
        val cursor = state.cursors[bucket] ?: 0
        // Sixteen lines per bucket leave seven unused after the nine-line history.
        // Compare whole sets: adjacent flattened indices share the same opening.
        // Rotation breaks equally varied ties and changes the leading sentence.
        val chosen = variedSelection(unused, cursor, contextClosings.getValue(contextKey).size)
        state.sessions[key] = chosen
        while (state.sessions.size > MAX_SESSION_CACHE) {
            state.sessions.remove(state.sessions.keys.first())
        }
        state.recent[bucket] = (recent + chosen).takeLast(MAX_RECENT_LINES)
        state.cursors[bucket] = (cursor + 1) % PHRASES_PER_BUCKET
        writeState(state)
        chosen.map(phrases::get)
    }

    private fun variedSelection(unused: List<Int>, cursor: Int, closingCount: Int): List<Int> {
        val start = cursor % unused.size
        val rotated = unused.drop(start) + unused.take(start)
        var best = emptyList<Int>()
        var bestScore = -1
        // At most 16 choose 3 = 560 candidates; no randomness or state mutation here.
        for (a in 0 until rotated.size - 2) {
            for (b in a + 1 until rotated.size - 1) {
                for (c in b + 1 until rotated.size) {
                    val chosen = listOf(rotated[a], rotated[b], rotated[c])
                    val openings = chosen.map { it / closingCount }.distinct().size
                    val closings = chosen.map { it % closingCount }.distinct().size
                    val score = (openings + closings) * 10 + minOf(openings, closings)
                    if (score > bestScore) {
                        best = chosen
                        bestScore = score
                        if (openings == 3 && closings == 3) return best
                    }
                }
            }
        }
        return best
    }

    fun reset() = synchronized(lock) {
        check(preferences.edit().clear().commit()) { "문장 기록을 지우지 못했어요." }
    }

    private fun readState(): State {
        val root = runCatching {
            JSONObject(preferences.getString(STATE_KEY, "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        if (root.optInt("version") != STATE_VERSION) return State()
        val state = State()
        val sessions = root.optJSONArray("sessions") ?: JSONArray()
        for (i in 0 until sessions.length()) {
            val item = sessions.optJSONObject(i) ?: continue
            val key = item.optString("key")
            if (key.length !in 3..180) continue
            val indices = item.optJSONArray("indices").toIndices()
            if (indices.size == 3 && indices.distinct().size == 3 && indices.all { it in 0 until PHRASES_PER_BUCKET }) {
                state.sessions[key] = indices
            }
        }
        while (state.sessions.size > MAX_SESSION_CACHE) state.sessions.remove(state.sessions.keys.first())
        val recent = root.optJSONObject("recent") ?: JSONObject()
        val recentKeys = recent.keys()
        while (recentKeys.hasNext()) {
            val bucket = recentKeys.next()
            if (bucket !in validBuckets) continue
            state.recent[bucket] = recent.optJSONArray(bucket).toIndices()
                .filter { it in 0 until PHRASES_PER_BUCKET }.distinct().takeLast(MAX_RECENT_LINES)
        }
        val cursors = root.optJSONObject("cursors") ?: JSONObject()
        val cursorKeys = cursors.keys()
        while (cursorKeys.hasNext()) {
            val bucket = cursorKeys.next()
            if (bucket in validBuckets) state.cursors[bucket] = cursors.optInt(bucket).coerceIn(0, PHRASES_PER_BUCKET - 1)
        }
        return state
    }

    private fun writeState(state: State) {
        val root = JSONObject().put("version", STATE_VERSION)
        val sessions = JSONArray()
        state.sessions.forEach { (key, indices) ->
            sessions.put(JSONObject().put("key", key).put("indices", JSONArray(indices)))
        }
        root.put("sessions", sessions)
        root.put("recent", JSONObject().apply {
            state.recent.forEach { (bucket, indices) -> put(bucket, JSONArray(indices)) }
        })
        root.put("cursors", JSONObject().apply {
            state.cursors.forEach { (bucket, cursor) -> put(bucket, cursor) }
        })
        check(preferences.edit().putString(STATE_KEY, root.toString()).commit()) { "문장 기록을 저장하지 못했어요." }
    }

    private fun JSONArray?.toIndices(): List<Int> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i ->
            val value = opt(i)
            if (value is Number) value.toInt() else null
        }
    }

    private fun digest(input: String): String = MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private data class State(
        val sessions: LinkedHashMap<String, List<Int>> = LinkedHashMap(),
        val recent: MutableMap<String, List<Int>> = mutableMapOf(),
        val cursors: MutableMap<String, Int> = mutableMapOf()
    )

    companion object {
        private val lock = Any()
        private const val STATE_KEY = "phrase_state"
        private const val STATE_VERSION = 1
        private const val MAX_SESSION_CACHE = 128
        private const val MAX_RECENT_LINES = 9
        private const val PHRASES_PER_BUCKET = 16

        private val soundOpenings = linkedMapOf(
            "울음" to listOf(
                "내 목소리가 조금 커졌어. ",
                "방금 울음이 나왔어. ",
                "잠깐 내게 귀 기울여 줘. ",
                "내가 울 때 곁에 와 줘. "
            ),
            "웃음" to listOf(
                "내 웃음이 들렸어? ",
                "방금 방긋 웃었어. ",
                "나랑 눈을 맞춰 줄래? ",
                "너를 보며 웃음이 났어. "
            ),
            "옹알이" to listOf(
                "내가 들려준 소리 어땠어? ",
                "나도 이야기에 끼고 싶어. ",
                "옹알옹알, 네 목소리를 듣고 있어. ",
                "나도 한마디 해 볼게. "
            ),
            "기타 소리" to listOf(
                "방금 낸 소리를 들었어? ",
                "내 목소리에 잠깐 귀 기울여 줘. ",
                "작은 소리로 너를 부른 걸까? ",
                "이 소리 다음에 내가 뭘 하는지 봐 줘. "
            ),
            "영상 장면" to listOf(
                "지금 내 표정을 함께 봐 줘. ",
                "내 몸짓을 천천히 따라와 줘. ",
                "내가 보는 쪽을 함께 봐 줄래? ",
                "지금 화면 속의 나를 살펴봐 줘. "
            )
        )

        private val contextClosings = linkedMapOf(
            "수유 전" to listOf(
                "곧 먹을 시간인지 같이 살펴볼래?",
                "배가 고픈 건지 내 다른 신호도 확인해 줘.",
                "먹기 전에 잠깐 안아 주며 이야기해 줘.",
                "먼저 내 눈과 몸짓을 보고 천천히 답해 줘."
            ),
            "수유 후" to listOf(
                "방금 먹었으니 잠깐 품에서 쉬어 볼래?",
                "먹고 난 뒤 내 표정도 같이 살펴봐 줘.",
                "지금은 등을 토닥이며 말을 걸어 줘.",
                "내가 보내는 다음 신호도 천천히 봐 줘."
            ),
            "잠들기 전" to listOf(
                "조용히 곁에 있어 주면 좋겠어.",
                "눈을 비비는지 함께 살펴봐 줄래?",
                "이제 조금 쉬어 볼까?",
                "작은 목소리로 대답해 줘."
            ),
            "잠에서 깬 후" to listOf(
                "깨어난 나에게 인사해 줘.",
                "잠에서 깬 뒤 내 표정이 어떤지 봐 줘.",
                "눈을 맞추고 천천히 말을 걸어 줘.",
                "잠깐 안아 주며 이야기를 시작할까?"
            ),
            "놀이 중" to listOf(
                "같이 하던 놀이를 한 번 더 해 볼래?",
                "내가 바라보는 게 뭔지 같이 찾아봐 줘.",
                "잠깐 멈추고 내 반응을 기다려 줘.",
                "네 차례 다음엔 내 차례야!"
            ),
            "기타 상황" to listOf(
                "무엇을 바라보는지 같이 봐 줄래?",
                "잠깐 곁에 와서 내 반응을 기다려 줘.",
                "내 표정과 몸짓도 함께 살펴봐 줘.",
                "이 순간을 짧은 말로 들려 줘."
            )
        )

        private val validBuckets = soundOpenings.keys.flatMap { sound ->
            contextClosings.keys.map { context -> "$sound|$context" }
        }.toSet()
    }
}
