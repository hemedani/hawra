package ir.hawraa.quran

import ir.hawraa.quran.ui.components.NavWaveKind
import ir.hawraa.quran.ui.navigation.Routes
import ir.hawraa.quran.ui.navigation.navWaveKind
import ir.hawraa.quran.ui.navigation.tabDriftDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shell's current classifier: a dock switch drifts along the tab order, a deeper
 * stack pushes, a shallower one pops, and RTL mirrors only the lateral drift.
 */
class NavMotionTest {

    @Test
    fun dockSwitchDriftsInTabOrder() {
        assertEquals(NavWaveKind.TabDrift(1), navWaveKind(Routes.HOME, Routes.READER, 1, 2))
        assertEquals(NavWaveKind.TabDrift(-1), navWaveKind(Routes.SETTINGS, Routes.HOME, 2, 1))
    }

    @Test
    fun deeperStackPushes() {
        assertEquals(NavWaveKind.Push, navWaveKind(Routes.SETTINGS, Routes.DOWNLOADS, 2, 3))
        assertEquals(NavWaveKind.Push, navWaveKind(Routes.READER, Routes.READER, 1, 2))
    }

    @Test
    fun shallowerStackPops() {
        assertEquals(NavWaveKind.Pop, navWaveKind(Routes.DOWNLOADS, Routes.SETTINGS, 3, 2))
        assertEquals(NavWaveKind.Pop, navWaveKind(Routes.PRESET_EDITOR, Routes.PRESETS, 4, 3))
    }

    @Test
    fun rtlMirrorsOnlyTheLateralDrift() {
        assertEquals(1, tabDriftDirection(0, 1, rtl = false))
        assertEquals(-1, tabDriftDirection(0, 1, rtl = true))
        assertEquals(-1, tabDriftDirection(3, 0, rtl = false))
        assertEquals(1, tabDriftDirection(3, 0, rtl = true))
    }
}
