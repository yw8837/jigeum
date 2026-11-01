package com.moa.babylanguage

import java.util.Locale

/** Short access codes are human-entered; legacy codes remain case-sensitive. Review codes are separate. */
internal fun normalizeAccessCode(input: String): String {
    val trimmed = input.trim()
    if (trimmed.length >= 32) return trimmed
    return trimmed.replace(" ", "").replace("-", "").uppercase(Locale.ROOT)
}

internal fun validAccessCode(code: String): Boolean =
    code.matches(Regex("[A-Z0-9]{8,12}")) || validReviewCode(code)
