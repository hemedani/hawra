package ir.hawraa.quran.ui.screens.player

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.sp
import ir.hawraa.quran.R
import ir.hawraa.quran.domain.preset.PresetStepKind
import ir.hawraa.quran.domain.quran.toPersianDigits
import ir.hawraa.quran.ui.appLocale
import ir.hawraa.quran.ui.components.BubbleButton
import ir.hawraa.quran.ui.components.GlassChip
import ir.hawraa.quran.ui.components.GlassElevation
import ir.hawraa.quran.ui.components.GlassSurface
import ir.hawraa.quran.ui.components.ICON_GLYPHS
import ir.hawraa.quran.ui.components.player.HeroArtwork
import ir.hawraa.quran.ui.components.player.PlaybackStatusLine
import ir.hawraa.quran.ui.components.player.PlayerChipRow
import ir.hawraa.quran.ui.components.player.Scrubber
import ir.hawraa.quran.ui.components.player.TransportControls
import ir.hawraa.quran.ui.quran.AyahOrnament
import ir.hawraa.quran.ui.quran.QuranText
import ir.hawraa.quran.ui.quran.quranLineHeight
import ir.hawraa.quran.ui.recitation.NowPlayingStore
import ir.hawraa.quran.ui.recitation.PlayerUi
import ir.hawraa.quran.ui.recitation.PlayerVerse
import ir.hawraa.quran.ui.theme.HawraaSpring
import ir.hawraa.quran.ui.theme.LocalGlassColors
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalQuranColors
import ir.hawraa.quran.ui.theme.useMotionEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

/**
 * The full-screen player's content (`docs/DESIGN.md` §10.12).
 *
 * This is Pejvak's fixed-viewport player body, reduced to what Hawraa can actually do (D9):
 * a header, the identity tile, the sounding surah/juz, the featured verse on the reading
 * surface, the scrubber, the transport and the option chips. There is no queue, sleep timer,
 * jump, enhancer, tone or notes sheet, and no control is drawn that cannot act.
 *
 * The two colour families stay separated: the verse sits on the opaque reading surface
 * ([LocalQuranColors]), never on glass. The chrome around it is glass.
 *
 * The player is **stateless** about playback: every fact comes from [ui], every command goes
 * through [store]. Position is pulled by a leaf ticker, so a tick recomposes only the
 * scrubber and never the title, the chips or the sheet around it.
 */
@Composable
fun PlayerContent(
    ui: PlayerUi,
    store: NowPlayingStore,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Hides the hero tile while the sheet flies its own copy between the dock and the hero. */
    heroTileAlpha: Float = 1f,
    /** Publishes the hero tile's coordinates so the sheet can land the flight exactly on it. */
    onHeroTilePositioned: ((LayoutCoordinates) -> Unit)? = null,
    /** Downward delta the body could not scroll, in px. */
    onPullDown: (Float) -> Unit = {},
    /** The pull gesture ended with release velocity in px/s (`0f` on a slow lift). */
    onPullEnd: (Float) -> Unit = {},
    /** True while the sheet has been pulled partway down from its resting height. */
    isPulled: () -> Boolean = { false },
) {
    val glass = LocalGlassColors.current
    val geometry = LocalHawraaGeometry.current
    val persian = LocalConfiguration.current.appLocale().language == "fa"
    val motion = useMotionEnabled()

    val identity = if (persian) ui.identityNumber.toPersianDigits() else ui.identityNumber.toString()
    val reciter = if (persian) ui.reciterNameFa else ui.reciterNameEn
    val title = if (ui.juzNumber != null) {
        stringResource(R.string.dock_juz_title, identity)
    } else {
        if (persian) ui.titleFa else ui.titleEn
    }
    val subtitle = if (ui.juzNumber != null) {
        stringResource(R.string.dock_juz_subtitle, reciter)
    } else {
        reciter
    }
    val stepLabel = ui.stepKind?.let { stringResource(it.labelRes()) }
    val activity = listOfNotNull(ui.presetNameFa, stepLabel).joinToString(" · ")

    // Pejvak scales its hero on a track change; Hawraa does the same on the identity tile, but
    // only when motion is allowed. With motion off the tile is simply at rest.
    var heroScale by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(ui.identityNumber, ui.juzNumber, motion) {
        if (!motion) {
            heroScale = 1f
            return@LaunchedEffect
        }
        heroScale = HERO_ENTRANCE_SCALE
        animate(
            initialValue = HERO_ENTRANCE_SCALE,
            targetValue = 1f,
            animationSpec = HawraaSpring.bouncy,
        ) { value, _ -> heroScale = value }
    }

    // The pull has to end even when the finger leaves without a fling. Watching the pointer
    // stream in the initial pass — before any child can consume it — gives a slow lift a home
    // without stealing the scroll, the scrubber or a button press.
    val pullDown by rememberUpdatedState(onPullDown)
    val pullEnd by rememberUpdatedState(onPullEnd)
    val pulled by rememberUpdatedState(isPulled)
    val headerDragState = rememberDraggableState { dy ->
        if (dy > 0f || pulled()) pullDown(dy)
    }
    val bodyScroll = rememberScrollState()
    val pullConnection = remember(bodyScroll) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0f && pulled()) {
                    pullDown(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y > 0f && bodyScroll.value <= 0) {
                    pullDown(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pulled()) {
                    pullEnd(available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (pulled()) pullEnd(available.y)
                return Velocity.Zero
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(glass.canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.isNotEmpty() && event.changes.none { it.pressed }) {
                            if (pulled()) pullEnd(0f)
                        }
                    }
                }
            }
            .testTag("player"),
    ) {
        PlayerHeader(
            title = stringResource(R.string.player_now_playing),
            activity = activity,
            isPlaying = ui.isPlaying && !ui.busy,
            onClose = onClose,
            dragState = headerDragState,
            onDragStopped = { velocity -> if (pulled()) pullEnd(velocity) },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .nestedScroll(pullConnection)
                .verticalScroll(bodyScroll)
                .padding(horizontal = geometry.spacing.xl)
                .padding(bottom = geometry.spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(geometry.spacing.xl),
        ) {
            HeroArtwork(
                size = geometry.player.heroSize,
                seedKey = identity,
                number = identity,
                tileAlpha = heroTileAlpha,
                onTilePositioned = onHeroTilePositioned,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = heroScale
                        scaleY = heroScale
                    }
                    .testTag("player-hero"),
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(geometry.spacing.xs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = glass.text,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = glass.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // A juz track has no verse to feature: the identity tile and the juz title already say
            // what is sounding, and a verse card would be a claim the file cannot make.
            ui.featuredVerse?.let { verse ->
                FeaturedVerseCard(verse = verse, fontSizeSp = ui.fontSizeSp)
            }

            ScrubberSection(
                store = store,
                isPlaying = ui.isPlaying,
                waiting = ui.busy,
                waveSeed = identity,
            )

            PlaybackStatusLine(
                waiting = ui.busy,
                error = if (ui.failed) stringResource(R.string.recitation_failed) else null,
            )

            TransportControls(
                isPlaying = ui.isPlaying,
                waiting = ui.busy,
                onToggle = store::toggle,
                onNext = store::next,
                onPrevious = store::previous,
            )

            PlayerOptions(ui = ui, store = store, persian = persian)
        }
    }
}

/**
 * The header: close on the start edge, a centred title with the running preset/step under it,
 * and a non-interactive playing indicator on the end edge.
 *
 * Pejvak puts a queue button on the end. Hawraa has no queue (D9), and a button that opens
 * nothing is worse than no button, so the end carries state instead: the same equaliser the
 * dock and the old player used.
 */
@Composable
private fun PlayerHeader(
    title: String,
    activity: String,
    isPlaying: Boolean,
    onClose: () -> Unit,
    dragState: DraggableState,
    onDragStopped: suspend CoroutineScope.(Float) -> Unit,
) {
    val glass = LocalGlassColors.current
    val geometry = LocalHawraaGeometry.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                orientation = Orientation.Vertical,
                state = dragState,
                onDragStopped = onDragStopped,
            )
            .padding(horizontal = geometry.spacing.lg, vertical = geometry.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            BubbleButton(
                icon = ICON_GLYPHS.getValue("close"),
                contentDescription = stringResource(R.string.player_close),
                onClick = onClose,
                size = geometry.controls.lg,
                iconSize = geometry.icons.md,
                modifier = Modifier.testTag("player-close"),
            )
        }
        Column(
            modifier = Modifier.weight(2f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = glass.text,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (activity.isNotEmpty()) {
                Text(
                    text = activity,
                    style = MaterialTheme.typography.labelSmall,
                    color = glass.accent,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            PlayingPulse(active = isPlaying)
        }
    }
}

/**
 * The featured verse on the opaque reading surface. Glass frames it; the verse itself never sits
 * on glass (DESIGN §16). The translation is separated by the mushaf's own hairline.
 */
@Composable
private fun FeaturedVerseCard(verse: PlayerVerse, fontSizeSp: Int) {
    val quran = LocalQuranColors.current
    val geometry = LocalHawraaGeometry.current
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(geometry.radius.xl),
        strong = true,
        elevation = GlassElevation.Panel,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(quran.page)
                .padding(geometry.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AyahOrnament(number = verse.ayah, highlight = quran.accent)
            Spacer(Modifier.height(geometry.spacing.md))
            QuranText(
                text = verse.arabic,
                letterColor = quran.letter,
                diacriticColor = quran.diacritic,
                fontSize = fontSizeSp.sp,
                lineHeight = quranLineHeight(fontSizeSp),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!verse.translation.isNullOrBlank()) {
                Spacer(Modifier.height(geometry.spacing.lg))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(geometry.glass.hairline)
                        .background(quran.hairline),
                )
                Spacer(Modifier.height(geometry.spacing.md))
                Text(
                    text = verse.translation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = quran.translation,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The scrubber and its position ticker.
 *
 * This is its own composable so the once-a-second position read recomposes only this leaf, never
 * the title, the hero or the chips. The initial read happens during composition, exactly as the
 * reader's old player did, so the first frame already shows the real position.
 */
@Composable
private fun ScrubberSection(
    store: NowPlayingStore,
    isPlaying: Boolean,
    waiting: Boolean,
    waveSeed: String,
) {
    var position by remember(store) { mutableStateOf(store.positionProvider()) }
    LaunchedEffect(store) {
        while (true) {
            delay(POSITION_TICK_MS)
            position = store.positionProvider()
        }
    }
    Scrubber(
        positionSec = position.first / 1_000,
        durationSec = position.second / 1_000,
        isPlaying = isPlaying,
        waiting = waiting,
        waveSeed = waveSeed,
        // [Scrubber] already reports milliseconds (see `scrubberSeekMs`), and the store
        // takes milliseconds — no conversion belongs at this seam.
        onSeek = { positionMs -> store.seek(positionMs.toLong()) },
    )
}

/** The chips that can act: speed, repeat, download (when a page exists), reciters and presets. */
@Composable
private fun PlayerOptions(
    ui: PlayerUi,
    store: NowPlayingStore,
    persian: Boolean,
) {
    val glass = LocalGlassColors.current
    val geometry = LocalHawraaGeometry.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(geometry.spacing.sm),
    ) {
        PlayerChipRow {
            GlassChip(
                label = ui.speed.label(persian),
                selected = ui.speed.value != 1f,
                description = stringResource(R.string.recitation_speed),
                onClick = store::cycleSpeed,
                modifier = Modifier.testTag("player-speed"),
            )
            GlassChip(
                label = stringResource(R.string.recitation_repeat),
                selected = ui.repeatAyah,
                onClick = store::toggleRepeat,
                modifier = Modifier.testTag("player-repeat"),
            )
            if (ui.downloadablePage != null) {
                GlassChip(
                    label = stringResource(R.string.player_download_page),
                    selected = false,
                    onClick = store::download,
                    modifier = Modifier.testTag("player-download"),
                )
            }
        }

        if (ui.reciters.size > 1) {
            Text(
                text = stringResource(R.string.player_reciters),
                style = MaterialTheme.typography.labelSmall,
                color = glass.textTertiary,
                modifier = Modifier.fillMaxWidth(),
            )
            PlayerChipRow {
                ui.reciters.forEach { reciter ->
                    GlassChip(
                        label = if (persian) reciter.nameFa else reciter.nameEn,
                        selected = reciter.key == ui.selectedReciterKey,
                        onClick = { store.selectReciter(reciter.key) },
                        modifier = Modifier.testTag("player-reciter-${reciter.key}"),
                    )
                }
            }
        }

        // "Plain" is only meaningful when there is a preset to leave; an empty preset list would
        // leave one chip that does nothing, which the player must never show.
        if (ui.presets.isNotEmpty()) {
            Text(
                text = stringResource(R.string.preset_section),
                style = MaterialTheme.typography.labelSmall,
                color = glass.textTertiary,
                modifier = Modifier.fillMaxWidth(),
            )
            PlayerChipRow {
                GlassChip(
                    label = stringResource(R.string.preset_plain),
                    selected = ui.presetNameFa == null,
                    onClick = store::exitPreset,
                    modifier = Modifier.testTag("player-preset-plain"),
                )
                ui.presets.forEach { preset ->
                    GlassChip(
                        label = preset.nameFa,
                        selected = ui.presetNameFa == preset.nameFa,
                        onClick = { store.playPreset(preset.clientId) },
                        modifier = Modifier.testTag("player-preset-${preset.clientId}"),
                    )
                }
            }
        }
    }
}

/**
 * The playing equaliser. A leaf: it animates on its own and recomposes nothing around it. Under
 * reduced motion the bars are static at a mid-wave shape, so the state is still legible.
 */
@Composable
private fun PlayingPulse(active: Boolean, modifier: Modifier = Modifier) {
    val glass = LocalGlassColors.current
    val geometry = LocalHawraaGeometry.current
    val motion = useMotionEnabled()
    val phase = if (motion) {
        val transition = rememberInfiniteTransition(label = "playerPulse")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = PULSE_PERIOD_MS),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "playerPulsePhase",
        )
        value
    } else {
        PULSE_STATIC_PHASE
    }
    val bars = PULSE_BARS
    Row(
        modifier = modifier
            .size(width = geometry.spacing.huge, height = geometry.controls.md)
            .padding(geometry.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(geometry.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(bars) { index ->
            val local = if (active) {
                val offset = index.toFloat() / bars
                val t = (phase + offset) % 1f
                PULSE_MIN + (1f - PULSE_MIN) * kotlin.math.abs(0.5f - t) * 2f
            } else {
                PULSE_IDLE
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

private const val HERO_ENTRANCE_SCALE = 0.96f
private const val PULSE_PERIOD_MS = 900
private const val PULSE_STATIC_PHASE = 0.5f
private const val PULSE_BARS = 4
private const val PULSE_MIN = 0.3f
private const val PULSE_IDLE = 0.28f
private const val POSITION_TICK_MS = 500L
