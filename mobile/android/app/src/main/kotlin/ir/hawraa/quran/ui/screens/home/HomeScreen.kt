package ir.hawraa.quran.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.hawraa.quran.R
import ir.hawraa.quran.domain.quran.toPersianDigits
import ir.hawraa.quran.ui.components.ChevronIcon
import ir.hawraa.quran.ui.components.DiamondBullet
import ir.hawraa.quran.ui.quran.QuranLettersFontFamily
import ir.hawraa.quran.ui.quran.QuranText
import ir.hawraa.quran.ui.quran.quranLineHeight
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalQuranColors
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
    val quran = LocalQuranColors.current
    val geometry = LocalHawraaGeometry.current

    // No opaque background: the app shell paints the ambient canvas behind every screen, and an
    // opaque page colour here would hide it — which is exactly what makes the glass stop reading
    // as glass.
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = geometry.dock.dockInset),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeroHeader()

            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
                ContinueCard(
                    state = state,
                    onOpen = {
                        val resume = state.resume
                        if (resume != null) onResume(resume.pageIndex) else onStartReading()
                    },
                )

                Spacer(Modifier.height(14.dp))
                TodayStrip(state = state)
                Spacer(Modifier.height(22.dp))

                Button(
                    onClick = onStartReading,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = quran.accent,
                        contentColor = quran.onAccent,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.home_start_reading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostAction(
                        label = stringResource(R.string.history_title),
                        onClick = onHistory,
                        modifier = Modifier.weight(1f),
                    )
                    GhostAction(
                        label = stringResource(R.string.settings_title),
                        onClick = onSettings,
                        modifier = Modifier.weight(1f),
                    )
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
private fun HeroHeader() {
    val quran = LocalQuranColors.current

    Box(
        modifier = Modifier.fillMaxWidth().height(252.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 44.dp.toPx()
            val radius = step * 0.46f
            val hairline = 1.dp.toPx()
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
                text = stringResource(R.string.home_title),
                fontFamily = QuranLettersFontFamily,
                fontSize = 70.sp,
                color = quran.accent,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            OrnamentRule()
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.labelLarge,
                color = quran.translation,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Gold rule broken by a diamond — the same motif as the banner's terminals. */
@Composable
private fun OrnamentRule() {
    val quran = LocalQuranColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(54.dp).height(1.dp).background(quran.mark.copy(alpha = 0.5f)))
        Spacer(Modifier.width(9.dp))
        Box(
            Modifier
                .size(6.dp)
                .rotate(45f)
                .background(quran.mark.copy(alpha = 0.85f)),
        )
        Spacer(Modifier.width(9.dp))
        Box(Modifier.width(54.dp).height(1.dp).background(quran.mark.copy(alpha = 0.5f)))
    }
}

/**
 * "Pick up where you left off", shown as a page rather than a row: the opening verses of
 * the page the reader stopped on, set in the mushaf face at reading size. Before there is
 * any history it falls back to the basmala, so the card is never empty.
 */
@Composable
private fun ContinueCard(state: HomeUiState, onOpen: () -> Unit) {
    val quran = LocalQuranColors.current
    val resume = state.resume
    val preview = resume?.preview?.takeIf { it.isNotBlank() } ?: state.basmala
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = quran.onAccent,
        border = BorderStroke(1.dp, quran.mark.copy(alpha = 0.5f)),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_resume_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = quran.translation,
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = resume?.surahName
                                ?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.home_subtitle),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = quran.accent,
                        )
                        Spacer(Modifier.width(9.dp))
                        DiamondBullet(tint = quran.mark)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = stringResource(
                                R.string.home_page,
                                (resume?.page ?: 1).toPersianDigits(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = quran.translation,
                        )
                    }
                }
                ChevronIcon(
                    pointsLeft = rtl,
                    tint = quran.mark.copy(alpha = 0.8f),
                    size = 20.dp,
                    strokeWidth = 1.6.dp,
                )
            }

            Spacer(Modifier.height(12.dp))

            if (preview.isNotBlank()) {
                QuranText(
                    text = preview,
                    letterColor = quran.letter,
                    diacriticColor = quran.diacritic,
                    fontSize = 26.sp,
                    lineHeight = quranLineHeight(26),
                    textAlign = TextAlign.Start,
                    maxLines = 2,
                )
                Spacer(Modifier.height(12.dp))
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(quran.hairline.copy(alpha = 0.55f)),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.home_open),
                style = MaterialTheme.typography.labelLarge,
                color = quran.accent,
            )
        }
    }
}

/** Today against the daily goal, plus the streak when there is one worth showing. */
@Composable
private fun TodayStrip(state: HomeUiState) {
    val quran = LocalQuranColors.current
    val goalMinutes = state.dailyGoalSeconds / 60

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_today),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
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
                        color = if (state.hasReadToday) {
                            quran.accent
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (state.streakDays > 1) {
                    StreakPill(days = state.streakDays)
                }
            }

            Spacer(Modifier.height(12.dp))
            GoalBar(fraction = state.goalFraction)
            Spacer(Modifier.height(7.dp))
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
                color = if (state.goalFraction >= 1f) {
                    quran.accent
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun StreakPill(days: Int) {
    val quran = LocalQuranColors.current
    Surface(
        shape = RoundedCornerShape(50),
        color = quran.mark.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, quran.mark.copy(alpha = 0.35f)),
    ) {
        Text(
            text = stringResource(R.string.home_streak_days, days.toPersianDigits()),
            style = MaterialTheme.typography.labelMedium,
            color = quran.mark,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun GoalBar(fraction: Float) {
    val quran = LocalQuranColors.current
    // Progress runs from the leading edge, which is the right side in RTL. The canvas is
    // not mirrored for us, so the fill is placed explicitly.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50))) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(
            color = quran.mark.copy(alpha = 0.18f),
            size = size,
            cornerRadius = radius,
        )
        if (fraction > 0f) {
            val filled = size.width * fraction
            val left = if (rtl) size.width - filled else 0f
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(quran.accent, quran.mark),
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
    val quran = LocalQuranColors.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, quran.mark.copy(alpha = 0.45f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = quran.accent),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
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
