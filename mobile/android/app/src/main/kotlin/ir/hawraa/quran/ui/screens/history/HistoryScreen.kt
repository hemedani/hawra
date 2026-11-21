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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.hawraa.quran.R
import ir.hawraa.quran.domain.quran.toPersianDigits
import ir.hawraa.quran.ui.appLocale
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalQuranColors
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryContent(state = state, onBack = onBack)
}

/** Stateless body, so the layout can be screenshot-tested without Hilt or a database. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryContent(
    state: HistoryUiState,
    onBack: () -> Unit,
) {
    val geometry = LocalHawraaGeometry.current
    Scaffold(
        // Transparent, so the shell's ambient canvas shows through and the glass below it has
        // something to refract.
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> Unit

            state.isEmpty -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = geometry.dock.dockInset,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "summary") {
                    SummaryCard(
                        totalSeconds = state.totalSeconds,
                        pages = state.pagesTouched,
                        streak = state.streakDays,
                    )
                }
                state.days.forEach { day ->
                    item(key = "day-${day.day}") {
                        DayHeader(day = day)
                    }
                    items(day.rows, key = { it.clientId }) { row ->
                        SessionRow(row = row)
                    }
                }
            }
        }
    }
}

/** The three numbers the reader actually cares about, above the list. */
@Composable
private fun SummaryCard(totalSeconds: Int, pages: Int, streak: Int) {
    val quran = LocalQuranColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Stat(
                value = durationLabel(totalSeconds),
                label = stringResource(R.string.history_total_time),
                accent = quran.accent,
            )
            Stat(
                value = pages.toPersianDigits(),
                label = stringResource(R.string.history_pages),
                accent = quran.mark,
            )
            Stat(
                value = streak.toPersianDigits(),
                label = stringResource(R.string.history_streak),
                accent = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayHeader(day: HistoryDay) {
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
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = durationLabel(day.durationReadSec),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionRow(row: HistoryRow) {
    val quran = LocalQuranColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = quran.pageVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading rule: gold when the page was read through, muted when abandoned.
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(38.dp)
                    .background(
                        color = if (row.completed) quran.mark else quran.hairline,
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.surahName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.history_ayah_span, row.ayahSpan.toPersianDigits()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = durationLabel(row.durationReadSec),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (row.completed) {
                        stringResource(R.string.history_completed)
                    } else {
                        stringResource(R.string.history_interrupted)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (row.completed) quran.accent else quran.translation,
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
