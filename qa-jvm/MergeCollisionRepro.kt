package com.moa.babylanguage

fun main() {
    val t = 1_750_000_000_000L
    val incoming = Observation(t, "옹알이", "놀이 중", "A", "한마디", "AUDIO", "Babbling")
    val existing = listOf(incoming.copy(note = "B"), incoming.copy(id = t + 100))
    val plan = RecordTransfer.planMerge(existing, listOf(incoming))
    check(plan.additions.size == 1 && plan.conflicts == 1 && plan.duplicates == 0) {
        "Distinct collision record discarded: $plan"
    }
    println("PASS: distinct timestamp collision preserved")
}
