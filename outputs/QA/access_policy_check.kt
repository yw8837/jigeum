package com.moa.babylanguage

/** Host-side regression runner for the real policy. No Android/billing bypass in the app. */
fun main() {
    var passed = 0
    fun test(name: String, body: () -> Unit) { body(); passed++; println("PASS: $name") }

    test("free features never consult billing") {
        val policy = AccessPolicy { error("Free work consulted billing") }
        AccessFeature.entries.filterNot { it.requiresPass }.forEach { check(policy.canStart(it)) }
    }
    test("all three paid features recheck current entitlement") {
        var current = true
        val policy = AccessPolicy { current }
        val paid = listOf(AccessFeature.CAMERA, AccessFeature.STORED_VIDEO, AccessFeature.PDF)
        paid.forEach { check(policy.canStart(it)) }
        current = false
        paid.forEach { check(!policy.canStart(it)) }
        check(policy.canStart(AccessFeature.EDIT_SAVE_DRAFT))
    }
    test("expired and disposed media generations reject late callbacks") {
        var current = true
        val session = MediaAccessSession(AccessPolicy { current }, AccessFeature.STORED_VIDEO)
        check(session.isCurrent())
        current = false
        check(!session.isCurrent())
        current = true
        session.close()
        check(session.isClosed)
        check(!session.isCurrent())
        check(MediaAccessSession(AccessPolicy { current }, AccessFeature.STORED_VIDEO).isCurrent())
    }
    test("denial invalidates generation even after restoration") {
        var current = false
        val session = MediaAccessSession(AccessPolicy { current }, AccessFeature.CAMERA)
        check(!session.isCurrent())
        current = true
        check(!session.isCurrent())
    }
    test("old callbacks cannot redirect a newer screen") {
        var redirects = 0
        val disposed = MediaAccessSession(AccessPolicy { true }, AccessFeature.CAMERA)
        disposed.close()
        check(!disposed.check { redirects++ })
        check(redirects == 0)
        val expired = MediaAccessSession(AccessPolicy { false }, AccessFeature.STORED_VIDEO)
        check(!expired.check { redirects++ })
        check(!expired.check { redirects++ })
        check(redirects == 1)
    }
    println("$passed policy checks passed")
}
