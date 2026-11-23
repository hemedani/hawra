package ir.hawraa.quran.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.hawraa.quran.R
import ir.hawraa.quran.domain.quran.toPersianDigits
import ir.hawraa.quran.ui.appLocale
import ir.hawraa.quran.ui.components.GlassHeader
import ir.hawraa.quran.ui.components.GlassSurface
import ir.hawraa.quran.ui.components.LocalShellInsets
import ir.hawraa.quran.ui.components.ScreenFrame
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalHawraaTokens
import ir.hawraa.quran.ui.theme.OceanCascade
import ir.hawraa.quran.ui.theme.oceanEntrance
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryContent(state = state)
}

/** Stateless body, so the layout can be screenshot-tested without Hilt or a database. */
@Composable
internal fun HistoryContent(
    state: HistoryUiState,
) {
    val geometry = LocalHawraaGeometry.current
    val shellInsets = LocalShellInsets.current

    ScreenFrame(
        header = { headerModifier ->
            GlassHeader(
                modifier = headerModifier,
                title = stringResource(R.string.history_title),
                overline = stringResource(R.string.history_overline),
                userTitle = true,
            )
        },
    ) { topInset ->
        when {
            state.isLoading -> Unit

            state.isEmpty -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topInset, bottom = shellInsets.bottom),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = LocalHawraaTokens.current.textSecondary,
                )
            }

            else -> OceanCascade {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = geometry.spacing.lg,
                        end = geometry.spacing.lg,
                        top = topInset,
                        bottom = shellInsets.bottom,
                    ),
                    verticalArrangement = Arrangement.spacedBy(geometry.spacing.sm),
                ) {
                    item(key = "summary") {
                        SummaryCard(
                            totalSeconds = state.totalSeconds,
                            pages = state.pagesTouched,
                            streak = state.streakDays,
                            modifier = Modifier.oceanEntrance(0),
                        )
                    }
                    state.days.forEachIndexed { dayIndex, day ->
                        item(key = "day-${day.day}") {
                            DayHeader(
                                day = day,
                                modifier = Modifier.oceanEntrance(dayIndex * 8 + 1),
                            )
                        }
                        itemsIndexed(day.rows, key = { _, row -> row.clientId }) { rowIndex, row ->
                            SessionRow(
                                row = row,
                                modifier = Modifier.oceanEntrance(dayIndex * 8 + rowIndex + 2),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The three numbers the reader actually cares about, above the list. */
@Composable
private fun SummaryCard(
    totalSeconds: Int,
    pages: Int,
    streak: Int,
    modifier: Modifier = Modifier,
) {
    val geometry = LocalHawraaGeometry.current
    val tokens = LocalHawraaTokens.current
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(geometry.radius.card),
        strong = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = geometry.spacing.sm,
                    vertical = geometry.spacing.lg,
                ),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Stat(
                value = durationLabel(totalSeconds),
                label = stringResource(R.string.history_total_time),
                accent = tokens.accent,
            )
            Stat(
                value = pages.toPersianDigits(),
                label = stringResource(R.string.history_pages),
                accent = tokens.accent,
            )
            Stat(
                value = streak.toPersianDigits(),
                label = stringResource(R.string.history_streak),
                accent = tokens.accent,
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
        Spacer(Modifier.height(LocalHawraaGeometry.current.spacing.xxs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = LocalHawraaTokens.current.textSecondary,
        )
    }
}

@Composable
private fun DayHeader(day: HistoryDay, modifier: Modifier = Modifier) {
    val geometry = LocalHawraaGeometry.current
    val tokens = LocalHawraaTokens.current
    val today = LocalDate.now()
    // The app language, not the device one: a reader on an English phone with the app in
    // Persian should still see Persian dates.
    val locale = LocalConfiguration.current.appLocale()
    val label = when (day.day) {
        today -> stringResource(R.string.history_today)
        today.minusDays(1) -> stringResource(R.string.history_yesterday)
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(
            Date(day.day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()),
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = geometry.spacing.md, bottom = geometry.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = tokens.text,
        )
        Spacer(Modifier.width(geometry.spacing.sm))
        Text(
            text = durationLabel(day.durationReadSec),
            style = MaterialTheme.typography.labelMedium,
            color = tokens.textSecondary,
        )
    }
}

@Composable
private fun SessionRow(row: HistoryRow, modifier: Modifier = Modifier) {
    val geometry = LocalHawraaGeometry.current
    val tokens = LocalHawraaTokens.current
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(geometry.radius.md),
        rimLight = false,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(geometry.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading rule: accent when the page was read through, muted when abandoned.
            Box(
                modifier = Modifier
                    .width(geometry.glass.rimHeight)
                    .height(geometry.controls.sm)
                    .background(
                        color = if (row.completed) tokens.accent else tokens.glassBorder,
                        shape = RoundedCornerShape(geometry.glass.rimHeight),
                    ),
            )
            Spacer(Modifier.width(geometry.spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.surahName,
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.text,
                )
                Spacer(Modifier.height(geometry.spacing.xxs))
                Text(
                    text = stringResource(R.string.history_ayah_span, row.ayahSpan.toPersianDigits()),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = durationLabel(row.durationReadSec),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = tokens.text,
                )
                Spacer(Modifier.height(geometry.spacing.xxs))
                Text(
                    text = if (row.completed) {
                        stringResource(R.string.history_completed)
                    } else {
                        stringResource(R.string.history_interrupted)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (row.completed) tokens.accent else tokens.textTertiary,
                )
            }
        }
    }
}

/** "45 s" / "12 min" / "1 h 20 min", in the reader's digits. */
@Composable
private fun durationLabel(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    return when {
        totalSeconds < 60 -> stringResource(
            R.string.history_duration_seconds,
            totalSeconds.toPersianDigits(),
        )
        hours < 1 -> stringResource(
            R.string.history_duration_minutes,
            minutes.toPersianDigits(),
        )
        else -> stringResource(
            R.string.history_duration_hours,
            hours.toPersianDigits(),
            (minutes % 60).toPersianDigits(),
        )
    }
}
