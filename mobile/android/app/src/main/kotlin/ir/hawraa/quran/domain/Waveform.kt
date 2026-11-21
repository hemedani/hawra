package ir.hawraa.quran.domain

import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** RMS windows stored per track — comfortably more than the bars a phone shows. */
const val WAVEFORM_BUCKETS: Int = 84

/** At or below this level a window is silence, and its bar sits on the floor. */
const val WAVEFORM_FLOOR_DB: Float = -52f

/** The level that fills a bar completely. */
const val WAVEFORM_CEIL_DB: Float = -6f

/** Silence still draws this much bar, so a quiet passage reads as a track, not a gap. */
const val WAVEFORM_MIN_HEIGHT: Float = 0.12f

private const val FULL_SCALE: Float = 32_768f

fun waveformFromRms(rms: FloatArray): FloatArray = FloatArray(rms.size) { waveformHeight(rms[it]) }

fun waveformHeight(rms: Float): Float {
    if (rms <= 0f) return WAVEFORM_MIN_HEIGHT
    val db = 20f * log10(rms.coerceAtLeast(1e-6f))
    val t = ((db - WAVEFORM_FLOOR_DB) / (WAVEFORM_CEIL_DB - WAVEFORM_FLOOR_DB)).coerceIn(0f, 1f)
    return WAVEFORM_MIN_HEIGHT + (1f - WAVEFORM_MIN_HEIGHT) * t
}

fun rmsOf(energy: Double, samples: Int): Float = if (samples <= 0) 0f else sqrt(energy / samples).toFloat()

fun pcm16FullScale(sample: Short): Float = sample / FULL_SCALE

fun sampleWaveform(values: FloatArray, bars: Int): FloatArray {
    if (bars <= 0) return FloatArray(0)
    if (values.isEmpty()) return FloatArray(bars) { WAVEFORM_MIN_HEIGHT }
    val out = FloatArray(bars)
    for (bar in 0 until bars) {
        val start = (bar.toFloat() / bars * values.size).toInt().coerceIn(0, values.lastIndex)
        val end = ((bar + 1f) / bars * values.size).toInt().coerceIn(start + 1, values.size)
        var peak = 0f
        for (index in start until end) if (values[index] > peak) peak = values[index]
        out[bar] = if (peak > 0f) peak else values[start]
    }
    return out
}

private const val TWO_PI: Float = (2.0 * kotlin.math.PI).toFloat()

/**
 * Deterministic loudness strip seeded by track identity when streaming or when raw PCM peaks
 * are unavailable — phrases that swell out of a pause and fall back into one.
 */
fun sampleWaveformFor(seed: String, buckets: Int = WAVEFORM_BUCKETS): FloatArray {
    if (buckets <= 0) return FloatArray(0)
    var state = seed.fold(0x9E3779B9.toInt()) { carry, char -> carry * 31 + char.code }
    fun next(): Float {
        state = state * 1_103_515_245 + 12_345
        return ((state ushr 16) and 0x7FFF) / 32_767f
    }
    val phrases = 2.5f + next() * 3f
    val phrasePhase = next() * TWO_PI
    val grain = 7f + next() * 5f
    val grainPhase = next() * TWO_PI
    val floor = 0.20f + next() * 0.06f
    val ceiling = 0.84f + next() * 0.10f
    return FloatArray(buckets) { index ->
        val t = index / buckets.toFloat()
        val phrase = 0.5f - 0.5f * cos(t * phrases * TWO_PI + phrasePhase)
        val grainWave = 0.5f + 0.5f * sin(t * grain * TWO_PI + grainPhase)
        val tooth = next() * 0.16f - 0.08f
        val loudness = phrase * (0.72f + 0.28f * grainWave) + tooth
        (floor + (ceiling - floor) * loudness.coerceIn(0f, 1f)).coerceIn(WAVEFORM_MIN_HEIGHT, 1f)
    }
}
