package ir.hawraa.quran.ui.screens.reader

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.hawraa.quran.R
import ir.hawraa.quran.domain.preset.PresetStepKind
import ir.hawraa.quran.domain.quran.toPersianDigits
import ir.hawraa.quran.domain.recitation.PlaybackSpeed
import ir.hawraa.quran.ui.components.BubbleButton
import ir.hawraa.quran.ui.components.GlassChip
import ir.hawraa.quran.ui.components.GlassElevation
import ir.hawraa.quran.ui.components.GlassSurface
import ir.hawraa.quran.ui.components.TransportIcons
import ir.hawraa.quran.ui.quran.AyahOrnament
import ir.hawraa.quran.ui.quran.QuranText
import ir.hawraa.quran.ui.quran.quranLineHeight
import ir.hawraa.quran.ui.theme.LocalGlassColors
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalQuranColors
import kotlin.math.roundToInt

/**
 * The full-screen player (`docs/DESIGN.md` §10.10, the immersive counterpart to the dock's
 * [ir.hawraa.quran.ui.components.MiniPlayerRow]).
 *
 * Reached by tapping the mini-player; the shell hides the dock while it is open so this covers the
 * whole screen. It is drawn by the reader (which owns the engine and the verses) and is **stateless**
 * — every fact it shows is passed in — so a screenshot can render it without a live player.
 *
 * The two colour families stay separated (DESIGN §16): the verse sits on the opaque **reading
 * surface** ([LocalQuranColors], the reader's own ink), never on glass; the chrome around it is
 * glass. The accent means "this is playing"; the bar is a determinate position read, not a poll the
 * session depends on.
 */
@Composable
fun NowPlayingScreen(
    surahName: String,
    ayah: Int?,
    juz: Int?,
    verseArabic: String,
    translationText: String?,
    featuredStep: PresetStepKind?,
    reciterName: String,
    presetName: String?,
    isPlaying: Boolean,
    buffering: Boolean,
    failed: Boolean,
    speed: PlaybackSpeed,
    repeatAyah: Boolean,
    reciters: List<ReciterChoice>,
    selectedReciterKey: String?,
    fontSizeSp: Int,
    persian: Boolean,
    positionMs: Long,
    durationMs: Long,
    downloading: Boolean,
    presets: List<PresetOption>,
    activePresetName: String?,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleRepeat: () -> Unit,
    onSelectReciter: (String) -> Unit,
    onDownload: () -> Unit,
    onPlayPreset: (String) -> Unit,
    onExitPreset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlassColors.current
    val quran = LocalQuranColors.current
    val geometry = LocalHawraaGeometry.current

    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(glass.canvas)
            .statusBarsPadding()
            .padding(horizontal = geometry.spacing.xl)
            .testTag("player"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ---- top bar: close + context ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = geometry.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BubbleButton(
                icon = TransportIcons.ChevronDown,
                contentDescription = stringResource(R.string.player_close),
                onClick = onClose,
                size = geometry.controls.lg,
                iconSize = geometry.icons.md,
                modifier = Modifier.testTag("player-close"),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = geometry.spacing.md),
            ) {
                Text(
                    text = stringResource(R.string.player_now_playing),
                    style = MaterialTheme.typography.labelMedium,
                    color = glass.textTertiary,
                )
                Text(
                    text = presetName ?: reciterName,
                    style = MaterialTheme.typography.titleMedium,
                    color = glass.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PlayingPulse(active = isPlaying && !buffering)
        }

        // ---- hero: the verse on the reading surface ----
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(geometry.radius.xl),
            strong = true,
            elevation = GlassElevation.Panel,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // The reading surface, opaque, so the mushaf is read not decorated (DESIGN §16).
                    .background(quran.page)
                    .padding(geometry.spacing.xl),
            ) {
                val scroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Surah + ayah/juz line, in chrome ink (this is the panel's label, not scripture).
                    val ayahNumber = ayah ?: 0
                    Text(
                        text = if (juz != null) {
                            stringResource(R.string.dock_juz_title, if (persian) juz.toPersianDigits() else juz.toString())
                        } else {
                            "$surahName · " + stringResource(
                                R.string.history_ayah_span,
                                if (persian) ayahNumber.toPersianDigits() else ayahNumber.toString(),
                            )
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = glass.text,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(geometry.spacing.xl))
                    AyahOrnament(
                        number = ayahNumber,
                        highlight = quran.accent,
                    )
                    Spacer(Modifier.height(geometry.spacing.md))
                    QuranText(
                        text = verseArabic,
                        letterColor = quran.letter,
                        diacriticColor = quran.diacritic,
                        fontSize = fontSizeSp.sp,
                        lineHeight = quranLineHeight(fontSizeSp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!translationText.isNullOrBlank()) {
                        Spacer(Modifier.height(geometry.spacing.xl))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(quran.hairline),
                        )
                        Spacer(Modifier.height(geometry.spacing.lg))
                        if (featuredStep != null) {
                            Text(
                                text = stringResource(featuredStep.labelRes()),
                                style = MaterialTheme.typography.labelSmall,
                                color = glass.accent,
                            )
                            Spacer(Modifier.height(geometry.spacing.xs))
                        }
                        Text(
                            text = translationText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = quran.translation,
                            textAlign = TextAlign.Center,
                            lineHeight = 26.sp,
                        )
                    }
                    if (failed) {
                        Spacer(Modifier.height(geometry.spacing.lg))
                        Text(
                            text = stringResource(R.string.recitation_failed),
                            style = MaterialTheme.typography.labelMedium,
                            color = glass.danger,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(geometry.spacing.xl))

        // ---- progress: determinate position read ----
        Column(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(geometry.spacing.xs)
                    .clip(RoundedCornerShape(geometry.radius.pill))
                    .background(glass.track)
                    .testTag("player-progress-track"),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress * 100f)
                        .fillMaxHeight()
                        .background(glass.accent),
                )
            }
            Spacer(Modifier.height(geometry.spacing.xs))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = formatClock(positionMs, persian),
                    style = MaterialTheme.typography.labelSmall,
                    color = glass.textSecondary,
                )
                Text(
                    text = formatClock(durationMs, persian),
                    style = MaterialTheme.typography.labelSmall,
                    color = glass.textSecondary,
                )
            }
        }

        Spacer(Modifier.height(geometry.spacing.lg))

        // ---- transport ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BubbleButton(
                icon = TransportIcons.Previous,
                contentDescription = stringResource(R.string.recitation_previous_ayah),
                onClick = onPrevious,
                size = geometry.controls.xl,
                iconSize = geometry.icons.lg,
                modifier = Modifier.testTag("player-prev"),
            )
            Spacer(Modifier.width(geometry.spacing.lg))
            BubbleButton(
                icon = if (isPlaying) TransportIcons.Pause else TransportIcons.Play,
                contentDescription = stringResource(
                    if (isPlaying) R.string.recitation_pause else R.string.recitation_play,
                ),
                onClick = onToggle,
                size = geometry.controls.play,
                iconSize = geometry.icons.lg,
                filled = true,
                busy = buffering,
                modifier = Modifier.testTag("player-play"),
            )
            Spacer(Modifier.width(geometry.spacing.lg))
            BubbleButton(
                icon = TransportIcons.Next,
                contentDescription = stringResource(R.string.recitation_next_ayah),
                onClick = onNext,
                size = geometry.controls.xl,
                iconSize = geometry.icons.lg,
                modifier = Modifier.testTag("player-next"),
            )
        }

        Spacer(Modifier.height(geometry.spacing.lg))

        // ---- speed + repeat + download ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(geometry.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassChip(
                label = speed.label(persian),
                selected = speed.value != 1f,
                description = stringResource(R.string.recitation_speed),
                onClick = onCycleSpeed,
                modifier = Modifier.testTag("player-speed"),
            )
            GlassChip(
                label = stringResource(R.string.recitation_repeat),
                selected = repeatAyah,
                onClick = onToggleRepeat,
                modifier = Modifier.testTag("player-repeat"),
            )
            Spacer(Modifier.weight(1f))
            GlassChip(
                label = stringResource(
                    if (downloading) R.string.player_downloading else R.string.player_download_page,
                ),
                selected = false,
                onClick = onDownload,
                modifier = Modifier.testTag("player-download"),
            )
        }

        // ---- preset picker: the recipe to play, or plain recitation ----
        Spacer(Modifier.height(geometry.spacing.lg))
        Text(
            text = stringResource(R.string.preset_section),
            style = MaterialTheme.typography.labelSmall,
            color = glass.textTertiary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(geometry.spacing.xs))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(geometry.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(geometry.spacing.xs),
        ) {
            GlassChip(
                label = stringResource(R.string.preset_plain),
                selected = activePresetName == null,
                onClick = onExitPreset,
                modifier = Modifier.testTag("player-preset-plain"),
            )
            presets.forEach { preset ->
                GlassChip(
                    label = preset.nameFa,
                    selected = activePresetName == preset.nameFa,
                    onClick = { onPlayPreset(preset.clientId) },
                    modifier = Modifier.testTag("player-preset-${preset.clientId}"),
                )
            }
        }

        // ---- reciter switcher ----
        if (reciters.size > 1) {
            Spacer(Modifier.height(geometry.spacing.lg))
            Text(
                text = stringResource(R.string.player_reciters),
                style = MaterialTheme.typography.labelSmall,
                color = glass.textTertiary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(geometry.spacing.xs))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(geometry.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(geometry.spacing.xs),
            ) {
                reciters.forEach { reciter ->
                    GlassChip(
                        label = if (persian) reciter.nameFa else reciter.nameEn,
                        selected = reciter.key == selectedReciterKey,
                        onClick = { onSelectReciter(reciter.key) },
                        modifier = Modifier.testTag("player-reciter-${reciter.key}"),
                    )
                }
            }
        }

        Spacer(Modifier.height(geometry.spacing.xl))
    }
}

/**
 * A small equaliser that breathes while audio is sounding. It is a leaf: it animates on its own and
 * recomposes nothing around it, so the player's text never redraws per frame.
 */
@Composable
private fun PlayingPulse(active: Boolean, modifier: Modifier = Modifier) {
    val glass = LocalGlassColors.current
    val geometry = LocalHawraaGeometry.current
    val transition = rememberInfiniteTransition(label = "pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulsePhase",
    )
    val bars = 4
    Row(
        modifier = modifier
            .size(width = geometry.spacing.huge, height = geometry.controls.md)
            .padding(geometry.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(geometry.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(bars) { i ->
            // Each bar peaks at a different point in the cycle so the row reads as a wave, not a blink.
            val local = if (active) {
                val offset = i.toFloat() / bars
                val t = ((phase + offset) % 1f)
                0.3f + 0.7f * kotlin.math.abs(0.5f - t) * 2f
            } else {
                0.28f
            }
            Box(
                modifier = Modifier
                    .width(geometry.spacing.xs)
                    .fillMaxHeight(local)
                    .clip(RoundedCornerShape(geometry.radius.pill))
                    .background(if (active) glass.accent else glass.track),
            )
        }
    }
}

private fun PresetStepKind.labelRes(): Int = when (this) {
    PresetStepKind.AYAH -> R.string.preset_step_ayah
    PresetStepKind.TRANSLATION -> R.string.preset_step_translation
    PresetStepKind.TAFSIR -> R.string.preset_step_tafsir
    PresetStepKind.SILENCE -> R.string.preset_step_silence
}

/** `m:ss`, in Persian digits when [persian]. A clock, not a localized sentence. */
private fun formatClock(ms: Long, persian: Boolean): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val text = "%d:%02d".format(minutes, seconds)
    return if (persian) text.toPersianDigits() else text
}
