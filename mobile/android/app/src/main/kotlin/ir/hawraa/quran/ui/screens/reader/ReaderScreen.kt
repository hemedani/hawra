package ir.hawraa.quran.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.hawraa.quran.R
import ir.hawraa.quran.domain.quran.BasmalaItem
import ir.hawraa.quran.domain.quran.QuranVerse
import ir.hawraa.quran.domain.quran.ReaderItem
import ir.hawraa.quran.domain.quran.ReaderItems
import ir.hawraa.quran.domain.quran.SelectionPlan
import ir.hawraa.quran.domain.quran.SurahHeaderItem
import ir.hawraa.quran.domain.quran.VerseElement
import ir.hawraa.quran.domain.quran.VerseItem
import ir.hawraa.quran.domain.quran.VerseLayer
import ir.hawraa.quran.domain.quran.VerseSelection
import ir.hawraa.quran.domain.quran.playbackPlan
import ir.hawraa.quran.domain.quran.toPersianDigits
import ir.hawraa.quran.domain.settings.ReadingSettings
import ir.hawraa.quran.ui.appLocale
import ir.hawraa.quran.ui.components.BubbleButton
import ir.hawraa.quran.ui.components.GlassElevation
import ir.hawraa.quran.ui.components.GlassHeader
import ir.hawraa.quran.ui.components.GlassSurface
import ir.hawraa.quran.ui.components.ICON_GLYPHS
import ir.hawraa.quran.ui.components.LocalShellInsets
import ir.hawraa.quran.ui.components.ScreenFrame
import ir.hawraa.quran.ui.components.SelectionAction
import ir.hawraa.quran.ui.components.SelectionActionBox
import ir.hawraa.quran.ui.components.TransportIcons
import ir.hawraa.quran.ui.components.playAyahAction
import ir.hawraa.quran.ui.components.playSelectionAction
import ir.hawraa.quran.ui.quran.AyahOrnament
import ir.hawraa.quran.ui.quran.MushafBanner
import ir.hawraa.quran.ui.quran.QuranText
import ir.hawraa.quran.ui.quran.displayName
import ir.hawraa.quran.ui.quran.quranLineHeight
import ir.hawraa.quran.ui.theme.LocalGlassColors
import ir.hawraa.quran.ui.theme.LocalHawraaDarkTheme
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalQuranColors
import ir.hawraa.quran.ui.theme.useMotionEnabled
import ir.hawraa.quran.ui.theme.withReadingSettings
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop

/**
 * The reader: **one continuous scroll** through the mushaf, with the signature verse + translation
 * layout, in both themes and both locales (`docs/DESIGN.md` §11.1).
 *
 * There is no pager and no page counter. Pages still exist underneath — sessions, recitation queues
 * and the resume point are page-based — but the reader never shows a page number, and scrolling
 * runs straight through page boundaries (see [ReaderItems]).
 *
 * There is also **no bottom bar**. Actions that belong to a selection appear in a floating box over
 * that selection ([SelectionActionBox]); playback and navigation live in the dock, which the app
 * shell draws (`HawraaShell`). The scroll end is padded by the measured shell inset so the last
 * verse clears the dock; the screen owns no dock chrome of its own.
 *
 * Typography goes through [QuranText] only, so letters and diacritics stay independently colorable.
 * The verse and each of its translations are **separately selectable** ([VerseSelection]); session
 * tracking hooks into real scroll events, never a timer.
 */
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Leaving the app mid-read ends the session; coming back does not silently resume it
    // (the first scroll after returning opens a fresh one).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onReaderStopped()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Navigating away keeps the activity started, so ON_STOP never fires — but the mushaf is no
    // longer on screen and must not keep accruing reading time.
    DisposableEffect(Unit) {
        onDispose { viewModel.onScreenLeft() }
    }

    ReaderContent(
        state = state,
        onBack = onBack,
        onSettings = onSettings,
        onPageVisible = viewModel::onPageVisible,
        onReadingActivity = viewModel::onReadingActivity,
        onPlayPause = viewModel::onPlayPause,
        onPlaySelection = viewModel::onPlaySelection,
        onDownloadPage = viewModel::downloadCurrentPage,
        onOpenPlayer = { viewModel.playerStore.openControls() },
    )
}

/**
 * Stateless reader body: state in, events out. Kept separate from the ViewModel wiring so the
 * mushaf can be rendered in tests (and previewed) directly.
 */
@Composable
internal fun ReaderContent(
    state: ReaderUiState,
    onSettings: () -> Unit,
    onBack: () -> Unit = {},
    onPageVisible: (Int) -> Unit = {},
    onReadingActivity: (Int) -> Unit = {},
    onPlayPause: (Pair<Int, Int>?, Int) -> Unit = { _, _ -> },
    onPlaySelection: (SelectionPlan) -> Unit = {},
    onDownloadPage: () -> Unit = {},
    onOpenPlayer: () -> Unit = {},
) {
    val themeQuran = LocalQuranColors.current
    val dark = LocalHawraaDarkTheme.current
    val geometry = LocalHawraaGeometry.current
    val reader = geometry.reader
    val motion = useMotionEnabled()
    val shellInsets = LocalShellInsets.current
    val persian = LocalConfiguration.current.appLocale().language == "fa"
    val quran = remember(themeQuran, state.settings, dark) {
        themeQuran.withReadingSettings(state.settings, dark)
    }

    CompositionLocalProvider(LocalQuranColors provides quran) {
        val items = remember(state.pages, state.surahs) {
            ReaderItems.build(state.pages, state.surahs)
        }
        val listState = rememberLazyListState()

        // What the reader has highlighted. One entry per tapped element, so the verse and each
        // translation keep independent state — the foundation for showing three translations and a
        // commentary at once, each highlighted on its own.
        var selection by remember { mutableStateOf(VerseSelection()) }

        // Where the focused verse sits on screen, so the action box can follow it. Null when
        // nothing is selected or the verse has scrolled out of the viewport.
        var focusBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

        val visiblePageNumber by remember(items) {
            derivedStateOf {
                items.getOrNull(listState.firstVisibleItemIndex)?.page ?: 0
            }
        }
        val visiblePage = state.pages.firstOrNull { it.page == visiblePageNumber }
            ?: state.pages.firstOrNull()
        val currentSurah = state.surahOf(visiblePage)

        // Restore "continue reading" once: scroll to the first item of the stored page.
        var restored by remember(items) { mutableStateOf(false) }
        LaunchedEffect(items, state.startPageIndex) {
            if (restored || items.isEmpty()) return@LaunchedEffect
            val page = state.pages.getOrNull(state.startPageIndex)?.page
            val index = page?.let { ReaderItems.indexOfPage(items, it) } ?: -1
            if (index > 0) listState.scrollToItem(index)
            restored = true
        }

        // A new page at the top of the screen is the session's event source: it opens a session,
        // and scrolling past closes the previous one as completed.
        LaunchedEffect(items) {
            if (items.isEmpty()) return@LaunchedEffect
            snapshotFlow { items.getOrNull(listState.firstVisibleItemIndex)?.page }
                .collect { page -> if (page != null) onPageVisible(page) }
        }

        // Any scroll is the reader's own activity signal — the only thing that advances the
        // reading clock while the mushaf is on screen.
        LaunchedEffect(items) {
            if (items.isEmpty()) return@LaunchedEffect
            snapshotFlow {
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }
                .drop(1)
                .collect { (index, _) ->
                    items.getOrNull(index)?.let { onReadingActivity(it.page) }
                }
        }

        // Follow-along: bring the reciting ayah into view, but only when it is actually off screen,
        // so a reader who is scrolling is not yanked back on every ayah.
        LaunchedEffect(state.recitation.playingVerse) {
            val playing = state.recitation.playingVerse ?: return@LaunchedEffect
            val index = ReaderItems.indexOfVerse(items, playing.first, playing.second)
            if (index < 0) return@LaunchedEffect
            val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == index }
            if (!visible) listState.animateScrollToItem(index)
        }

        // A selection that scrolls out of view must take its action box with it — a floating box
        // anchored to nothing is worse than no box.
        LaunchedEffect(listState, selection) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .collect {
                    val active = selection.active ?: return@collect
                    val index = ReaderItems.indexOfVerse(items, active.surah, active.ayah)
                    if (index >= 0 && index !in visibleRange(listState)) focusBounds = null
                }
        }

        ScreenFrame(
            header = { headerModifier ->
                GlassHeader(
                    modifier = headerModifier,
                    title = currentSurah?.displayName().orEmpty(),
                    overline = visiblePage?.let { page ->
                        stringResource(
                            R.string.reader_page_juz,
                            if (persian) page.page.toPersianDigits() else page.page.toString(),
                            if (persian) page.juz.toPersianDigits() else page.juz.toString(),
                        )
                    },
                    userTitle = true,
                    leading = {
                        BubbleButton(
                            icon = ICON_GLYPHS.getValue("back"),
                            contentDescription = stringResource(R.string.back),
                            onClick = onBack,
                            size = geometry.controls.md,
                        )
                    },
                    actions = {
                        if (state.recitation.available) {
                            BubbleButton(
                                icon = ICON_GLYPHS.getValue("download"),
                                contentDescription = stringResource(R.string.player_download_page),
                                onClick = onDownloadPage,
                            )
                        }
                        BubbleButton(
                            icon = ICON_GLYPHS.getValue("settings"),
                            contentDescription = stringResource(R.string.reader_settings),
                            onClick = onSettings,
                        )
                        BubbleButton(
                            icon = ICON_GLYPHS.getValue("equalizer"),
                            contentDescription = stringResource(R.string.player_expand),
                            onClick = onOpenPlayer,
                        )
                    },
                )
            },
        ) { topInset ->
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val containerHeightPx = constraints.maxHeight.toFloat()
                val dockInsetPx = with(density) { shellInsets.bottom.toPx() }
                val topSafePx = with(density) { topInset.toPx() }

                // The reading pane floats: it keeps a margin from the screen edge so the ambient
                // canvas is visible around it, and the glass rim reads as chrome. Inside that rim
                // the pane is the opaque page colour — scripture is read on a stable field, never
                // on a translucent surface. Its top starts below the floating header.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = geometry.spacing.md,
                            end = geometry.spacing.md,
                            top = topInset,
                        ),
                ) {
                    GlassSurface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(geometry.radius.xl),
                        strong = true,
                        elevation = GlassElevation.Panel,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(quran.page),
                        ) {
                            when {
                                state.isLoading -> ReaderMessage(stringResource(R.string.reader_loading))
                                state.failed || items.isEmpty() ->
                                    ReaderMessage(stringResource(R.string.reader_error))

                                else -> LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize().testTag("mushaf-scroll"),
                                    contentPadding = PaddingValues(
                                        start = reader.pageGutter,
                                        end = reader.pageGutter,
                                        top = geometry.spacing.sm,
                                        // Room for the dock at its tallest, so the last verse is
                                        // never hidden behind it.
                                        bottom = shellInsets.bottom,
                                    ),
                                ) {
                                    items(items = items, key = { it.key }) { item ->
                                        when (item) {
                                            is SurahHeaderItem -> MushafBanner(
                                                surahNumber = item.surah.number,
                                                surahName = item.surah.displayName(),
                                                juz = item.juz,
                                                modifier = Modifier.padding(top = reader.bannerGap),
                                            )

                                            is BasmalaItem -> QuranText(
                                                text = state.basmalaUthmani,
                                                letterColor = quran.letter,
                                                diacriticColor = quran.diacritic,
                                                fontSize = state.settings.fontSizeSp.sp,
                                                lineHeight = quranLineHeight(state.settings.fontSizeSp),
                                                modifier = Modifier.padding(
                                                    top = reader.basmalaTop,
                                                    bottom = reader.basmalaBottom,
                                                ),
                                            )

                                            is VerseItem -> VerseBlock(
                                                verse = item.verse,
                                                settings = state.settings,
                                                selection = selection,
                                                playing = state.recitation.playingVerse ==
                                                    (item.verse.surah to item.verse.ayah),
                                                onToggle = { element ->
                                                    selection = selection.toggle(element)
                                                },
                                                onFocusBounds = { rect -> focusBounds = rect },
                                            )
                                        }
                                    }
                                    item(key = "end") { Spacer(Modifier.height(reader.afterBanner)) }
                                }
                            }
                        }
                    }
                }

                // ---- the floating action box, anchored to the selection ----
                val active = selection.active
                AnimatedVisibility(
                    visible = active != null,
                    enter = if (motion) {
                        fadeIn() + scaleIn(initialScale = 0.94f)
                    } else {
                        EnterTransition.None
                    },
                    exit = if (motion) {
                        fadeOut() + scaleOut(targetScale = 0.94f)
                    } else {
                        ExitTransition.None
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    if (active != null) {
                        var boxHeight by remember { mutableStateOf(0) }
                        val gapPx = with(density) { geometry.spacing.sm.toPx() }
                        val bounds = focusBounds
                        val y = when {
                            bounds == null ->
                                containerHeightPx - dockInsetPx - boxHeight - gapPx

                            // Above the verse when there is room, below it otherwise, and never
                            // over the dock or under the top bar.
                            bounds.top - boxHeight - gapPx >= topSafePx ->
                                bounds.top - boxHeight - gapPx

                            bounds.bottom + gapPx + boxHeight <= containerHeightPx - dockInsetPx ->
                                bounds.bottom + gapPx

                            else -> containerHeightPx - dockInsetPx - boxHeight - gapPx
                        }

                        // Only the actions that can actually do something. With no recitation
                        // catalogue there is no play button, rather than a dead one. The play action
                        // says what it will do: a plain single-verse pick plays from that ayah to the
                        // end of the surah (D31) and is labelled "Play ayah N"; a selection that
                        // narrows the scope — two or more verses, or any translation/tafsir — becomes
                        // "Play selection" and plays only what was picked (D32). Download is the
                        // explicit half of "keep this offline": the same page scope the player and the
                        // silent auto-cache use, so the three can never disagree.
                        val plan = selection.playbackPlan()
                        val actions = buildList {
                            if (state.recitation.available) {
                                if (plan != null && plan.isSelectionRun) {
                                    add(playSelectionAction { onPlaySelection(plan) })
                                } else {
                                    add(
                                        playAyahAction(active.ayah) {
                                            onPlayPause(
                                                active.surah to active.ayah,
                                                visiblePage?.page ?: visiblePageNumber,
                                            )
                                        },
                                    )
                                }
                                add(
                                    SelectionAction(
                                        key = "download",
                                        label = stringResource(R.string.player_download_page),
                                        icon = TransportIcons.Download,
                                        onClick = onDownloadPage,
                                    ),
                                )
                            }
                        }

                        SelectionActionBox(
                            actions = actions,
                            modifier = Modifier
                                .offset { IntOffset(0, y.roundToInt().coerceAtLeast(0)) }
                                .onGloballyPositioned { boxHeight = it.size.height },
                        )
                    }
                }
            }
        }
    }
}

/** Indices currently laid out, so "is the selected verse still on screen" is a cheap lookup. */
private fun visibleRange(state: androidx.compose.foundation.lazy.LazyListState): IntRange {
    val info = state.layoutInfo.visibleItemsInfo
    if (info.isEmpty()) return IntRange.EMPTY
    return info.first().index..info.last().index
}

/**
 * One verse: the Arabic pane and one pane per translation, each with its **own** tap state.
 *
 * Tapping the Arabic highlights only the Arabic; tapping a translation highlights only that
 * translation. That is the whole point of the split: the block is a stack of independent panes,
 * not one unit with one colour, so a future layout can show three translations and a commentary
 * side by side and let the reader point at any one of them.
 */
@Composable
private fun VerseBlock(
    verse: QuranVerse,
    settings: ReadingSettings,
    selection: VerseSelection,
    playing: Boolean,
    onToggle: (VerseElement) -> Unit,
    onFocusBounds: (androidx.compose.ui.geometry.Rect?) -> Unit,
) {
    val quran = LocalQuranColors.current
    val geometry = LocalHawraaGeometry.current
    val reader = geometry.reader
    val verseSelected = selection.isSelected(verse.surah, verse.ayah, VerseLayer.VERSE)
    val focused = selection.isFocused(verse.surah, verse.ayah)
    // Tapping the Arabic peeks at its translations even with translations switched off — a
    // lookup, not a mode change: nothing is selected and the setting is untouched.
    val showTranslations = settings.showTranslation || focused

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = geometry.spacing.xs)
            // Only the focused verse reports its bounds, so there is one callback per selection
            // rather than one per pane.
            .then(
                if (focused) {
                    Modifier.onGloballyPositioned { onFocusBounds(it.boundsInRoot()) }
                } else {
                    Modifier
                },
            ),
    ) {
        Pane(
            selected = verseSelected,
            playing = playing,
            tag = "verse-${verse.key}",
            onClick = { onToggle(VerseElement.verse(verse.surah, verse.ayah)) },
        ) {
            AyahOrnament(
                number = verse.ayah,
                highlight = when {
                    verseSelected -> quran.mark
                    playing -> quran.accent
                    else -> null
                },
            )
            Spacer(Modifier.width(reader.ornamentGap))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                QuranText(
                    text = verse.textUthmani,
                    letterColor = quran.letter,
                    diacriticColor = quran.diacritic,
                    fontSize = settings.fontSizeSp.sp,
                    lineHeight = quranLineHeight(settings.fontSizeSp),
                )
            }
        }

        if (showTranslations) {
            verse.translations.forEach { translation ->
                Pane(
                    selected = selection.isSelected(
                        verse.surah,
                        verse.ayah,
                        VerseLayer.TRANSLATION,
                        translation.key,
                    ),
                    playing = false,
                    tag = "translation-${verse.key}-${translation.key}",
                    // Indented past the rosette: the translation belongs to the verse, not to the
                    // ornament beside it.
                    modifier = Modifier.padding(
                        start = reader.translationIndent,
                        end = geometry.spacing.xs,
                        top = geometry.spacing.xxs,
                    ),
                    onClick = {
                        onToggle(
                            VerseElement.translation(verse.surah, verse.ayah, translation.key),
                        )
                    },
                ) {
                    Text(
                        text = translation.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = quran.translation,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.weight(1f).padding(horizontal = geometry.spacing.xxs),
                    )
                }
            }
        }
    }
}

/**
 * A tappable pane: a wash when selected, a bar on the leading edge, and an accent wash when it is
 * the ayah being recited. Selection outranks playback, so a tapped verse keeps its mark while the
 * recitation moves past it.
 */
@Composable
private fun Pane(
    selected: Boolean,
    playing: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val quran = LocalQuranColors.current
    val geometry = LocalHawraaGeometry.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val wash = when {
        selected -> quran.selection
        playing -> quran.playing
        else -> Color.Transparent
    }
    val edge = if (selected) quran.selectionEdge else quran.playingEdge

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(geometry.radius.lg))
            .background(wash)
            .clickable(onClick = onClick)
            .testTag(tag)
            .drawBehind {
                if (!selected && !playing) return@drawBehind
                val bar = geometry.reader.selectionBar.toPx()
                // Leading edge: right in RTL, left in LTR.
                val x = if (rtl) size.width - bar else 0f
                drawRoundRect(
                    color = edge,
                    topLeft = Offset(x, 0f),
                    size = Size(bar, size.height),
                    cornerRadius = CornerRadius(bar),
                )
            }
            .padding(horizontal = geometry.spacing.sm, vertical = geometry.reader.panePaddingV),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

@Composable
private fun ReaderMessage(text: String) {
    val glass = LocalGlassColors.current
    val geometry = LocalHawraaGeometry.current
    Box(
        modifier = Modifier.fillMaxSize().padding(geometry.spacing.xxxl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = glass.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
