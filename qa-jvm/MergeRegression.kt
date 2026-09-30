package com.moa.babylanguage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun main() {
    var cases = 0
    fun test(name: String, body: () -> Unit) { body(); cases++; println("PASS: $name") }
    val t = 1_750_000_000_000L
    val a = Observation(t, "옹알이", "놀이 중", "A", "한마디", "AUDIO", "Babbling")
    val b = a.copy(note = "B")
    val separateA = a.copy(id = t + 100)
    test("timestamp collision preserves distinct same-day identical content") {
        val plan = RecordTransfer.planMerge(listOf(b, separateA), listOf(a))
        check(plan.additions.size == 1) { "Distinct collision record discarded: $plan" }
        check(plan.conflicts == 1 && plan.duplicates == 0)
        check(plan.additions.single().copy(id = t) == a)
    }
    test("persisted provenance deduplicates a relocated source on retry") {
        val plan = RecordTransfer.planMerge(listOf(b, separateA), listOf(a))
        val next = RecordTransfer.planMerge(listOf(b, separateA) + plan.additions, listOf(a), plan.importOrigins)
        check(next.additions.isEmpty() && next.duplicates == 1)
    }
    test("same content at two colliding source timestamps stays distinct") {
        val existing = listOf(b, b.copy(id = t + 100))
        val plan = RecordTransfer.planMerge(existing, listOf(a, separateA))
        check(plan.additions.size == 2 && plan.conflicts == 2)
        val retry = RecordTransfer.planMerge(existing + plan.additions, listOf(separateA, a), plan.importOrigins)
        check(retry.duplicates == 2 && retry.additions.isEmpty())
    }
    test("duplicate rows in the same incoming batch resolve once") {
        val plan = RecordTransfer.planMerge(listOf(b), listOf(a, a))
        check(plan.conflicts == 1 && plan.duplicates == 1 && plan.additions.size == 1)
    }
    test("unrelated replacement at a provenance target cannot erase an import") {
        val plan = RecordTransfer.planMerge(listOf(b), listOf(a))
        val replacement = plan.additions.single().copy(note = "unrelated")
        val next = RecordTransfer.planMerge(listOf(b, replacement), listOf(a), plan.importOrigins)
        check(next.additions.size == 1 && next.duplicates == 0)
    }
    test("missing provenance target permits deliberate restore") {
        val plan = RecordTransfer.planMerge(listOf(b), listOf(a))
        val next = RecordTransfer.planMerge(listOf(b), listOf(a), plan.importOrigins)
        check(next.additions.size == 1 && next.duplicates == 0)
    }
    test("exact original timestamp and content remains a duplicate") {
        val plan = RecordTransfer.planMerge(listOf(a), listOf(a))
        check(plan.duplicates == 1 && plan.additions.isEmpty())
    }
    test("relocation wraps within original calendar day") {
        val zone = ZoneId.systemDefault()
        val day = LocalDate.of(2024, 1, 31)
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val plan = RecordTransfer.planMerge(listOf(b.copy(id = end)), listOf(a.copy(id = end)))
        check(Instant.ofEpochMilli(plan.additions.single().id).atZone(zone).toLocalDate() == day)
    }
    test("maximum timestamp collision stays valid and retry is bounded") {
        val originalZone = java.util.TimeZone.getDefault()
        try {
            for (zoneName in listOf("UTC", "Pacific/Kiritimati", "America/Los_Angeles")) {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zoneName))
                val max = ObservationStore.MAX_TIMESTAMP
                val incoming = a.copy(id = max)
                val existing = listOf(b.copy(id = max))
                val plan = RecordTransfer.planMerge(existing, listOf(incoming, incoming))
                val target = plan.additions.single()
                check(target.id in 1..max)
                check(Instant.ofEpochMilli(target.id).atZone(ZoneId.systemDefault()).toLocalDate() ==
                    Instant.ofEpochMilli(max).atZone(ZoneId.systemDefault()).toLocalDate())
                check(plan.importOrigins.size == 1 && plan.duplicates == 1)
                val retry = RecordTransfer.planMerge(existing + target, listOf(incoming), plan.importOrigins)
                check(retry.duplicates == 1 && retry.importOrigins.isEmpty())
            }
        } finally { java.util.TimeZone.setDefault(originalZone) }
    }
    test("full store fails without returning a partial merge") {
        val full = (0 until ObservationStore.MAX_ROWS).map { b.copy(id = t + it) }
        var rejected = false
        try { RecordTransfer.planMerge(full, listOf(a)) } catch (_: StoreException) { rejected = true }
        check(rejected)
    }
    test("origin identity covers timestamp and every record field") {
        val variants = listOf(a.copy(id = t + 1), a.copy(sound = "x"), a.copy(context = "x"),
            a.copy(note = "x"), a.copy(phrase = "x"), a.copy(source = "CAMERA"), a.copy(modelCue = "x"))
        check(variants.all { RecordTransfer.importOrigin(it) != RecordTransfer.importOrigin(a) })
        check(RecordTransfer.importOrigin(a) == RecordTransfer.importOrigin(a.copy()))
    }
    println("$cases/$cases pure merge checks passed; no Android parser, SQLite, UI or renderer execution")
}
