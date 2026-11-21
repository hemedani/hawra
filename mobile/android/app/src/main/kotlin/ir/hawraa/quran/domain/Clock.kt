package ir.hawraa.quran.domain

/**
 * Monotonic-enough wall clock, injected so session tracking can be tested without
 * sleeping. Production binding is `System.currentTimeMillis`.
 */
fun interface Clock {
    fun nowMillis(): Long
}
