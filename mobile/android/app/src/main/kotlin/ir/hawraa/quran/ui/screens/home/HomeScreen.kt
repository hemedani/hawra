package ir.hawraa.quran.ui.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.hawraa.quran.R
import ir.hawraa.quran.domain.quran.toPersianDigits
import ir.hawraa.quran.ui.components.BubbleButton
import ir.hawraa.quran.ui.components.ChevronIcon
import ir.hawraa.quran.ui.components.DiamondBullet
import ir.hawraa.quran.ui.components.GlassHeader
import ir.hawraa.quran.ui.components.GlassSurface
import ir.hawraa.quran.ui.components.ICON_GLYPHS
import ir.hawraa.quran.ui.components.LocalShellInsets
import ir.hawraa.quran.ui.components.PrimaryButton
import ir.hawraa.quran.ui.components.ScreenFrame
import ir.hawraa.quran.ui.quran.QuranLettersFontFamily
import ir.hawraa.quran.ui.quran.QuranText
import ir.hawraa.quran.ui.quran.quranLineHeight
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalHawraaTokens
import ir.hawraa.quran.ui.theme.LocalQuranColors
import ir.hawraa.quran.ui.theme.OceanCascade
import ir.hawraa.quran.ui.theme.oceanEntrance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The landing page.
 *
 * The whole screen is an argument for the two product pillars: the mushaf itself is the
 * hero (a real page of Uthmani text, set in the real face, inside the continue card), and
 * the tracking is quiet but present (today's minutes against a small daily goal, and the
 * streak). Everything else is restrained — one filled action, two ghost ones — because a
 * reading app's home screen should invite one thing, not five.
 *
 * The chrome is the shell's: a floating glass header over the same ambient canvas every
 * destination uses, with the body scrolling behind it.
 */
@Composable
fun HomeScreen(
    onStartReading: () -> Unit,
    onResume: (Int) -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onStartReading = onStartReading,
        onResume = onResume,
        onHistory = onHistory,
        onSettings = onSettings,
    )
}

/** Stateless body, so the landing page can be screenshot-tested without Hilt. */
@Composable
internal fun HomeContent(
    state: HomeUiState,
    onStartReading: () -> Unit,
    onResume: (Int) -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    val geometry = LocalHawraaGeometry.current
    val shellInsets = LocalShellInsets.current

    ScreenFrame(
        header = { headerModifier ->
            GlassHeader(
                modifier = headerModifier,
                title = stringResource(R.string.home_title),
                overline = stringResource(R.string.auth_title_signin),
                userTitle = true,
                actions = {
                    BubbleButton(
                        icon = ICON_GLYPHS.getValue("settings"),
                        contentDescription = stringResource(R.string.settings_title),
                        onClick = onSettings,
                    )
                },
            )
        },
    ) { topInset ->
        OceanCascade {
            // No opaque background: the app shell paints the ambient canvas behind every screen,
            // and an opaque page colour here would hide it — which is exactly what makes the glass
            // stop reading as glass. The body scrolls under the floating header.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        top = topInset,
                        bottom = shellInsets.bottom + geometry.spacing.xxl,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HeroHeader(modifier = Modifier.oceanEntrance(0))

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = geometry.spacing.xl),
                ) {
                    ContinueCard(
                        state = state,
                        onOpen = {
                            val resume = state.resume
                            if (resume != null) onResume(resume.pageIndex) else onStartReading()
                        },
                        modifier = Modifier.oceanEntrance(1),
                    )

                    Spacer(Modifier.height(geometry.spacing.md))
                    TodayStrip(
                        state = state,
                        modifier = Modifier.oceanEntrance(2),
                    )
                    Spacer(Modifier.height(geometry.spacing.xl))

                    PrimaryButton(
                        label = stringResource(R.string.home_start_reading),
                        onClick = onStartReading,
                        modifier = Modifier.oceanEntrance(3),
                    )

                    Spacer(Modifier.height(geometry.spacing.sm))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(geometry.spacing.sm),
                    ) {
                        GhostAction(
                            label = stringResource(R.string.history_title),
                            onClick = onHistory,
                            modifier = Modifier.weight(1f).oceanEntrance(4),
                        )
                        GhostAction(
                            label = stringResource(R.string.settings_title),
                            onClick = onSettings,
                            modifier = Modifier.weight(1f).oceanEntrance(5),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Wordmark on a lattice of faint octagrams, faded into the paper at the bottom so the
 * pattern never competes with the card. The lattice is the same eight-point geometry as
 * the ayah marker, at 7% opacity: a texture, not an illustration.
 */
@Composable
private fun HeroHeader(modifier: Modifier = Modifier) {
    val quran = LocalQuranColors.current
    val tokens = LocalHawraaTokens.current
    val geometry = LocalHawraaGeometry.current

    Box(
        modifier = modifier.fillMaxWidth().height(geometry.home.heroHeight),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = geometry.spacing.huge.toPx()
            val radius = step * 0.46f
            val hairline = geometry.glass.hairline.toPx()
            val ink = quran.mark.copy(alpha = 0.07f)
            // The lattice is stroked with a gradient brush rather than a solid colour, so it fades
            // out towards the cards by itself. Fading it with a box painted over the top does not
            // work here: the hero sits on the shell's ambient canvas, so a fade to the mushaf paper
            // left a hard white band where the hero met the grey canvas, and a fade to transparent
            // is not a fade at all.
            val brush = Brush.verticalGradient(
                0f to ink,
                0.7f to ink,
                1f to Color.Transparent,
                endY = size.height,
            )
            var row = 0
            var y = -step
            while (y < size.height + step) {
                var x = if (row % 2 == 0) -step / 2f else 0f
                while (x < size.width + step) {
                    drawPath(
                        path = octagram(Offset(x, y), radius),
                        brush = brush,
                        style = Stroke(width = hairline),
                    )
                    x += step
                }
                row++
                y += step
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // The wordmark is set in the mushaf face: the app should look like the book.
            Text(
                text = stringResource(R.string.app_name),
                fontFamily = QuranLettersFontFamily,
                fontSize = geometry.home.wordmarkSizeSp.sp,
                color = tokens.accent,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(geometry.spacing.xs))
            OrnamentRule()
            Spacer(Modifier.height(geometry.spacing.sm))
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.labelLarge,
                color = tokens.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Gold rule broken by a diamond — the same motif as the banner's terminals. */
@Composable
private fun OrnamentRule() {
    val quran = LocalQuranColors.current
    val geometry = LocalHawraaGeometry.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(geometry.home.ornamentRuleWidth)
                .height(geometry.glass.hairline)
                .background(quran.mark.copy(alpha = 0.5f)),
        )
        Spacer(Modifier.width(geometry.spacing.sm))
        Box(
            Modifier
                .size(geometry.home.ornamentDiamond)
                .rotate(45f)
                .background(quran.mark.copy(alpha = 0.85f)),
        )
        Spacer(Modifier.width(geometry.spacing.sm))
        Box(
            Modifier
                .width(geometry.home.ornamentRuleWidth)
                .height(geometry.glass.hairline)
                .background(quran.mark.copy(alpha = 0.5f)),
        )
    }
}

/**
 * "Pick up where you left off", shown as a page rather than a row: the opening verses of
 * the page the reader stopped on, set in the mushaf face at reading size. Before there is
 * any history it falls back to the basmala, so the card is never empty.
 *
 * The card itself is glass chrome, but the verses sit on an opaque [QuranColors.page]
 * inset — scripture is never painted directly on a glass token.
 */
@Composable
private fun ContinueCard(
    state: HomeUiState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val quran = LocalQuranColors.current
    val tokens = LocalHawraaTokens.current
    val geometry = LocalHawraaGeometry.current
    val resume = state.resume
    val preview = resume?.preview?.takeIf { it.isNotBlank() } ?: state.basmala
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shape = RoundedCornerShape(geometry.radius.card)

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(role = Role.Button, onClick = onOpen),
        shape = shape,
        strong = true,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = geometry.spacing.lg, vertical = geometry.spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_resume_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textSecondary,
                    )
                    Spacer(Modifier.height(geometry.spacing.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = resume?.surahName
                                ?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.home_subtitle),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.accent,
                        )
                        Spacer(Modifier.width(geometry.spacing.sm))
                        DiamondBullet(tint = quran.mark)
                        Spacer(Modifier.width(geometry.spacing.sm))
                        Text(
                            text = stringResource(
                                R.string.home_page,
                                (resume?.page ?: 1).toPersianDigits(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textSecondary,
                        )
                    }
                }
                ChevronIcon(
                    pointsLeft = rtl,
                    tint = tokens.textSecondary,
                    size = geometry.icons.md,
                    strokeWidth = geometry.glass.borderWidth,
                )
            }

            Spacer(Modifier.height(geometry.spacing.md))

            if (preview.isNotBlank()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(geometry.radius.md))
                        .background(quran.page)
                        .padding(
                            horizontal = geometry.spacing.md,
                            vertical = geometry.spacing.sm,
                        ),
                ) {
                    QuranText(
                        text = preview,
                        letterColor = quran.letter,
                        diacriticColor = quran.diacritic,
                        fontSize = geometry.home.previewSizeSp.sp,
                        lineHeight = quranLineHeight(geometry.home.previewSizeSp.toInt()),
                        textAlign = TextAlign.Start,
                        maxLines = 2,
                    )
                }
                Spacer(Modifier.height(geometry.spacing.md))
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(geometry.glass.hairline)
                    .background(tokens.glassBorder),
            )
            Spacer(Modifier.height(geometry.spacing.sm))
            Text(
                text = stringResource(R.string.home_open),
                style = MaterialTheme.typography.labelLarge,
                color = tokens.accent,
            )
        }
    }
}

/** Today against the daily goal, plus the streak when there is one worth showing. */
@Composable
private fun TodayStrip(
    state: HomeUiState,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHawraaTokens.current
    val geometry = LocalHawraaGeometry.current
    val goalMinutes = state.dailyGoalSeconds / 60

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(geometry.radius.lg),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = geometry.spacing.lg, vertical = geometry.spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_today),
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.textSecondary,
                    )
                    Spacer(Modifier.height(geometry.spacing.xs))
                    Text(
                        text = if (state.hasReadToday) {
                            stringResource(
                                R.string.home_minutes,
                                state.todayMinutes.coerceAtLeast(1).toPersianDigits(),
                            )
                        } else {
                            stringResource(R.string.home_nothing_yet)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.hasReadToday) tokens.accent else tokens.textSecondary,
                    )
                }
                if (state.streakDays > 1) {
                    StreakPill(days = state.streakDays)
                }
            }

            Spacer(Modifier.height(geometry.spacing.md))
            GoalBar(fraction = state.goalFraction)
            Spacer(Modifier.height(geometry.spacing.sm))
            Text(
                text = if (state.goalFraction >= 1f) {
                    stringResource(R.string.home_goal_met)
                } else {
                    stringResource(
                        R.string.home_goal,
                        state.todayMinutes.toPersianDigits(),
                        goalMinutes.toPersianDigits(),
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (state.goalFraction >= 1f) tokens.accent else tokens.textSecondary,
            )
        }
    }
}

@Composable
private fun StreakPill(days: Int) {
    val tokens = LocalHawraaTokens.current
    val geometry = LocalHawraaGeometry.current
    GlassSurface(
        shape = RoundedCornerShape(geometry.radius.pill),
        filled = false,
        rimLight = false,
    ) {
        Text(
            text = stringResource(R.string.home_streak_days, days.toPersianDigits()),
            style = MaterialTheme.typography.labelMedium,
            color = tokens.accentTeal,
            modifier = Modifier.padding(
                horizontal = geometry.spacing.md,
                vertical = geometry.spacing.xs,
            ),
        )
    }
}

@Composable
private fun GoalBar(fraction: Float) {
    val tokens = LocalHawraaTokens.current
    val geometry = LocalHawraaGeometry.current
    // Progress runs from the leading edge, which is the right side in RTL. The canvas is
    // not mirrored for us, so the fill is placed explicitly.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(geometry.bars.thick)
            .clip(RoundedCornerShape(geometry.radius.pill)),
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(
            color = tokens.glassBorder,
            size = size,
            cornerRadius = radius,
        )
        if (fraction > 0f) {
            val filled = size.width * fraction
            val left = if (rtl) size.width - filled else 0f
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(tokens.accent, tokens.accentTeal),
                    startX = if (rtl) size.width else 0f,
                    endX = if (rtl) size.width - filled else filled,
                ),
                topLeft = Offset(left, 0f),
                size = Size(filled, size.height),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
private fun GhostAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHawraaTokens.current
    val geometry = LocalHawraaGeometry.current
    val shape = RoundedCornerShape(geometry.radius.md)
    GlassSurface(
        modifier = modifier
            .height(geometry.controls.lg)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick),
        shape = shape,
        filled = false,
        rimLight = false,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = tokens.text,
            )
        }
    }
}

/** Two overlapping squares: the eight-point star of the ayah marker, as a texture. */
private fun octagram(center: Offset, radius: Float): Path {
    val path = Path()
    for (square in 0 until 2) {
        val phase = square * PI.toFloat() / 4f
        for (corner in 0 until 4) {
            val angle = phase + corner * PI.toFloat() / 2f
            val x = center.x + radius * cos(angle)
            val y = center.y + radius * sin(angle)
            if (corner == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }
    return path
}
