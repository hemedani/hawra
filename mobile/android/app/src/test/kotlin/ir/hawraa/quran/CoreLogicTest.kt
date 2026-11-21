package ir.hawraa.quran

import ir.hawraa.quran.data.local.SyncStatus
import ir.hawraa.quran.domain.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure-logic tests: no Android framework, runs on the JVM. */
class CoreLogicTest {

    @Test
    fun themeMode_roundTripsThroughKey() {
        for (mode in ThemeMode.entries) {
            assertEquals(mode, ThemeMode.fromKey(mode.key))
        }
    }

    @Test
    fun themeMode_unknownKeyFallsBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("bogus"))
    }

    @Test
    fun syncStatus_roundTripsThroughKey() {
        for (status in SyncStatus.entries) {
            assertEquals(status, SyncStatus.fromKey(status.key))
        }
    }

    @Test
    fun syncStatus_unknownKeyFallsBackToPending() {
        assertEquals(SyncStatus.PENDING, SyncStatus.fromKey("bogus"))
    }
}