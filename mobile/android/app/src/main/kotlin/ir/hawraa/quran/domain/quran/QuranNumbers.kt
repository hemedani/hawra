package ir.hawraa.quran.domain.quran

private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

/**
 * Ayah, page and juz numbers are shown in Persian digits, as in the printed mushaf
 * and in the owner's readability reference. Kept in `domain/` so it is pure and
 * testable, and so every call site formats numbers the same way.
 */
fun Int.toPersianDigits(): String {
    val raw = toString()
    val out = StringBuilder(raw.length)
    for (c in raw) {
        out.append(if (c in '0'..'9') PERSIAN_DIGITS[c - '0'] else c)
    }
    return out.toString()
}

/** Same mapping for labels that already contain digits, e.g. an ayah span. */
fun String.toPersianDigits(): String {
    val out = StringBuilder(length)
    for (c in this) {
        out.append(if (c in '0'..'9') PERSIAN_DIGITS[c - '0'] else c)
    }
    return out.toString()
}
