package ir.hawraa.quran.domain.settings

import ir.hawraa.quran.domain.recitation.PlaybackSpeed

/**
 * Listening preferences, separate from [ReadingSettings] because they apply to audio
 * only — a reader who listens at 1.25× has not asked for bigger type.
 *
 * [reciterKey] is nullable on purpose: `null` means "whatever the bundled catalogue
 * says is default", so a future change of default reciter reaches users who never
 * chose one, without a migration.
 */
data class RecitationSettings(
    val reciterKey: String? = null,
    val speed: PlaybackSpeed = PlaybackSpeed.NORMAL,
    val repeatAyah: Boolean = false,
)
