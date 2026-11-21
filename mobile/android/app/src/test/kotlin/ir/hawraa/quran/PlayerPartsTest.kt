package ir.hawraa.quran

import ir.hawraa.quran.domain.WAVEFORM_BUCKETS
import ir.hawraa.quran.domain.WAVEFORM_MIN_HEIGHT
import ir.hawraa.quran.ui.components.player.decorativeWaveform
import ir.hawraa.quran.ui.components.player.scrubberFraction
import ir.hawraa.quran.ui.components.player.scrubberFractionAt
import ir.hawraa.quran.ui.components.player.scrubberSeekMs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure mapping behind the scrubber: position → fraction → milliseconds, and the
 * seeded fallback strip that keeps the dock and player drawing water when no measured
 * waveform exists.
 */
class PlayerPartsTest {

    @Test
    fun fraction_is_position_over_duration() {
        assertEquals(0f, scrubberFraction(0, 180), 0.0001f)
        assertEquals(0.5f, scrubberFraction(90, 180), 0.0001f)
        assertEquals(1f, scrubberFraction(180, 180), 0.0001f)
    }

    @Test
    fun fraction_clamps_outside_the_track() {
        assertEquals(0f, scrubberFraction(-12, 180), 0.0001f)
        assertEquals(1f, scrubberFraction(400, 180), 0.0001f)
    }

    @Test
    fun fraction_is_zero_for_an_unknown_duration() {
        assertEquals(0f, scrubberFraction(30, 0), 0.0001f)
        assertEquals(0f, scrubberFraction(30, -5), 0.0001f)
    }

    @Test
    fun gesture_fraction_maps_x_onto_the_track() {
        assertEquals(0f, scrubberFractionAt(0f, 400f), 0.0001f)
        assertEquals(0.25f, scrubberFractionAt(100f, 400f), 0.0001f)
        assertEquals(1f, scrubberFractionAt(400f, 400f), 0.0001f)
        assertEquals(1f, scrubberFractionAt(900f, 400f), 0.0001f)
        assertEquals(0f, scrubberFractionAt(-20f, 400f), 0.0001f)
        assertEquals(0f, scrubberFractionAt(100f, 0f), 0.0001f)
    }

    @Test
    fun seek_maps_fraction_to_milliseconds() {
        assertEquals(0, scrubberSeekMs(0f, 180))
        assertEquals(90_000, scrubberSeekMs(0.5f, 180))
        assertEquals(180_000, scrubberSeekMs(1f, 180))
    }

    @Test
    fun seek_clamps_a_loose_fraction() {
        assertEquals(0, scrubberSeekMs(-0.4f, 180))
        assertEquals(180_000, scrubberSeekMs(1.8f, 180))
    }

    @Test
    fun seek_is_zero_for_an_unknown_duration() {
        assertEquals(0, scrubberSeekMs(0.5f, 0))
    }

    @Test
    fun decorative_wave_is_deterministic() {
        val first = decorativeWaveform("2:255")
        val second = decorativeWaveform("2:255")
        assertArrayEquals(first, second, 0f)
    }

    @Test
    fun blank_seed_falls_back_to_the_same_water() {
        assertArrayEquals(decorativeWaveform("hawraa"), decorativeWaveform(""), 0f)
    }

    @Test
    fun decorative_wave_is_never_flat() {
        val wave = decorativeWaveform("2:255")
        val min = wave.min()
        val max = wave.max()
        assertTrue("a fallback strip must have crests", max - min > 0.2f)
        assertTrue("it must not be a bare line", wave.distinct().size > 1)
    }

    @Test
    fun decorative_wave_stays_bounded() {
        val wave = decorativeWaveform("juz:30")
        assertTrue(wave.all { it >= WAVEFORM_MIN_HEIGHT })
        assertTrue(wave.all { it <= 1f })
    }

    @Test
    fun decorative_wave_has_the_requested_bucket_count() {
        assertEquals(WAVEFORM_BUCKETS, decorativeWaveform("1:1").size)
        assertEquals(16, decorativeWaveform("1:1", buckets = 16).size)
        assertEquals(0, decorativeWaveform("1:1", buckets = 0).size)
    }

    @Test
    fun different_seeds_paint_different_water() {
        assertFalse(decorativeWaveform("1:1").contentEquals(decorativeWaveform("2:255")))
    }
}
