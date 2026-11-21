package ir.hawraa.quran

import ir.hawraa.quran.ui.components.ramp
import ir.hawraa.quran.ui.components.waveOffsetAt
import ir.hawraa.quran.ui.components.wavePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins the wave curve the dock, sheet and player all share. These are the Pejvak
 * expectations: a zero wavelength/amplitude is flat, a quarter wavelength is a crest,
 * a half wavelength is back on the baseline, and the taper fades the ends in.
 */
@RunWith(RobolectricTestRunner::class)
class WaveHelperTest {

    @Test
    fun ramp_isSmoothstepAndClamps() {
        assertEquals(0f, ramp(0f, 10f), 0.0001f)
        assertEquals(0.5f, ramp(5f, 10f), 0.0001f)
        assertEquals(1f, ramp(10f, 10f), 0.0001f)
        assertEquals(1f, ramp(20f, 10f), 0.0001f)
        assertEquals(1f, ramp(3f, 0f), 0.0001f)
    }

    @Test
    fun waveOffsetAt_zeroWavelengthIsFlat() {
        assertEquals(0f, waveOffsetAt(12f, 4f, 0f, 1f, 0.5f), 0.0001f)
    }

    @Test
    fun waveOffsetAt_zeroAmplitudeIsFlat() {
        assertEquals(0f, waveOffsetAt(12f, 0f, 64f, 1f, 0.5f), 0.0001f)
    }

    @Test
    fun waveOffsetAt_quarterWavelengthIsCrest() {
        val amplitude = 2.5f
        val wavelength = 100f
        assertEquals(
            amplitude,
            waveOffsetAt(wavelength / 4f, amplitude, wavelength, 0f, 0f),
            0.0001f,
        )
    }

    @Test
    fun waveOffsetAt_halfWavelengthIsBaseline() {
        assertEquals(0f, waveOffsetAt(50f, 2.5f, 100f, 0f, 0f), 0.0001f)
    }

    @Test
    fun waveOffsetAt_variationBreathesTheCrest() {
        val base = waveOffsetAt(25f, 2.5f, 100f, 0f, 0f)
        val breathed = waveOffsetAt(25f, 2.5f, 100f, 0f, 0.5f)
        assertTrue("variation must change the crest height", base != breathed)
    }

    @Test
    fun wavePath_emptySpanIsEmpty() {
        val path = wavePath(
            from = 10f,
            to = 10f,
            cy = 4f,
            amplitude = 2f,
            wavelength = 32f,
            phase = 0f,
            taper = 4f,
            variation = 0.2f,
        )
        assertTrue(path.isEmpty)
    }

    @Test
    fun wavePath_zeroAmplitudeIsAFlatLine() {
        val path = wavePath(
            from = 0f,
            to = 120f,
            cy = 8f,
            amplitude = 0f,
            wavelength = 32f,
            phase = 0f,
            taper = 4f,
            variation = 0.2f,
        )
        assertFalse(path.isEmpty)
        val bounds = path.getBounds()
        assertEquals(0f, bounds.left, 0.0001f)
        assertEquals(120f, bounds.right, 0.0001f)
        assertEquals(8f, bounds.top, 0.0001f)
        assertEquals(8f, bounds.bottom, 0.0001f)
    }

    @Test
    fun wavePath_staysWithinAmplitudeAndTapersToTheBaseline() {
        val amplitude = 3f
        val cy = 20f
        val path = wavePath(
            from = 0f,
            to = 200f,
            cy = cy,
            amplitude = amplitude,
            wavelength = 48f,
            phase = 0f,
            taper = 12f,
            variation = 0f,
        )
        assertFalse(path.isEmpty)
        val bounds = path.getBounds()
        assertTrue("crest above baseline", bounds.top < cy)
        assertTrue("trough below baseline", bounds.bottom > cy)
        assertTrue("stays within amplitude", bounds.top >= cy - amplitude - 0.001f)
        assertTrue("stays within amplitude", bounds.bottom <= cy + amplitude + 0.001f)
        assertEquals(0f, bounds.left, 0.0001f)
        assertEquals(200f, bounds.right, 0.0001f)
    }
}
