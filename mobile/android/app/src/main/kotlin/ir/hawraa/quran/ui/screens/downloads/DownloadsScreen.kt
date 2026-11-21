package ir.hawraa.quran.ui.screens.downloads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.hawraa.quran.R
import ir.hawraa.quran.data.local.DownloadBatch
import ir.hawraa.quran.domain.download.DownloadScope
import ir.hawraa.quran.domain.download.DownloadSizing
import ir.hawraa.quran.domain.download.EditionGranularity
import ir.hawraa.quran.domain.download.EditionInfo
import ir.hawraa.quran.domain.download.SizeUnit
import ir.hawraa.quran.domain.quran.toPersianDigits
import ir.hawraa.quran.ui.appLocale
import ir.hawraa.quran.ui.components.GlassChip
import ir.hawraa.quran.ui.components.GlassSegments
import ir.hawraa.quran.ui.components.GlassSurface
import ir.hawraa.quran.ui.theme.LocalGlassColors
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Downloads: pick a reciter and an edition, pick a scope, keep the audio on the device — and see
 * everything that is already there.
 *
 * The screen exists because the backend can answer "what exactly do I fetch?" and the device can
 * only stream. Everything the reader needs to judge a download before starting it — the ayah count,
 * the estimated size, whether some of it is already here — is on the confirmation row, because a
 * 300 MB download started by accident is a bad afternoon.
 *
 * Three things make this a *management* screen rather than a download form:
 *
 *  - the scope can be expressed in the unit the reader thinks in (a **page range**), not only in
 *    the units the wire speaks;
 *  - what is on disk is grouped **per edition** with a real byte count and a coverage bar, so
 *    "how much of Parhizgar do I have" is one line rather than thirty;
 *  - every scope that was ever requested stays listed with its own retry and delete, because a
 *    half-finished batch is the normal state of a 6236-file feature.
 */
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DownloadsContent(
        state = state,
        onBack = onBack,
        onReciter = viewModel::onReciter,
        onEdition = viewModel::onEdition,
        onMode = viewModel::onMode,
        onSurah = viewModel::onSurah,
        onAyah = viewModel::onAyah,
        onJuz = viewModel::onJuz,
        onFromPage = viewModel::onFromPage,
        onToPage = viewModel::onToPage,
        onFirstFive = viewModel::onFirstFivePages,
        onDownload = viewModel::download,
        onRetry = viewModel::retry,
        onDelete = viewModel::delete,
        onDeleteGroup = viewModel::deleteGroup,
    )
}

/** Stateless body, so a screenshot test can render it without Hilt or a database. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DownloadsContent(
    state: DownloadsUiState,
    onBack: () -> Unit,
    onReciter: (String) -> Unit = {},
    onEdition: (String) -> Unit = {},
    onMode: (ScopeMode) -> Unit = {},
    onSurah: (Int) -> Unit = {},
    onAyah: (Int) -> Unit = {},
    onJuz: (Int) -> Unit = {},
    onFromPage: (Int) -> Unit = {},
    onToPage: (Int) -> Unit = {},
    onFirstFive: () -> Unit = {},
    onDownload: () -> Unit = {},
    onRetry: (DownloadBatch) -> Unit = {},
    onDelete: (DownloadBatch) -> Unit = {},
    onDeleteGroup: (EditionGroup) -> Unit = {},
) {
    val geometry = LocalHawraaGeometry.current
    val glass = LocalGlassColors.current
    val persian = LocalConfiguration.current.appLocale().language == "fa"

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = geometry.screenPadding,
                    end = geometry.screenPadding,
                    top = geometry.spacing.sm,
                    bottom = geometry.dock.dockInset,
                ),
        ) {
            StorageSummary(
                totalBytes = state.totalBytes,
                pendingFiles = state.pendingFiles,
                persian = persian,
            )

            if (state.catalogueUnavailable) {
                Spacer(Modifier.height(geometry.spacing.xl))
                CatalogueUnavailable()
            }

            if (state.reciters.isNotEmpty()) {
                Spacer(Modifier.height(geometry.spacing.xxl))
                SectionTitle(stringResource(R.string.downloads_reciter))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(geometry.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(geometry.spacing.sm),
                ) {
                    state.reciters.forEach { reciter ->
                        GlassChip(
                            label = reciter.displayName(persian),
                            selected = reciter.key == state.selectedReciterKey,
                            onClick = { onReciter(reciter.key) },
                            modifier = Modifier.testTag("download_reciter_${reciter.key}"),
                        )
                    }
                }

                Spacer(Modifier.height(geometry.spacing.xl))
                SectionTitle(stringResource(R.string.downloads_edition))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(geometry.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(geometry.spacing.sm),
                ) {
                    state.editions.forEach { edition ->
                        EditionChip(
                            edition = edition,
                            selected = edition.key == state.selectedEdition?.key,
                            persian = persian,
                            onClick = { onEdition(edition.key) },
                        )
                    }
                }

                val edition = state.selectedEdition
                if (edition != null) {
                    Spacer(Modifier.height(geometry.spacing.sm))
                    Text(
                        text = granularityNote(edition.granularity),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (edition.granularity == EditionGranularity.JUZ) {
                            glass.textSecondary
                        } else {
                            glass.textTertiary
                        },
                        modifier = Modifier.testTag("download_granularity_note"),
                    )
                }

                Spacer(Modifier.height(geometry.spacing.xl))
                SectionTitle(stringResource(R.string.downloads_scope))
                GlassSegments(
                    options = state.scopeModes,
                    selected = state.mode,
                    onSelect = onMode,
                    label = { mode ->
                        stringResource(
                            when (mode) {
                                ScopeMode.PAGE -> R.string.downloads_scope_page
                                ScopeMode.SURAH -> R.string.downloads_scope_surah
                                ScopeMode.JUZ -> R.string.downloads_scope_juz
                                ScopeMode.AYAH -> R.string.downloads_scope_ayah
                            },
                        )
                    },
                    testTagOf = { mode -> "download_mode_${mode.name.lowercase()}" },
                )

                Spacer(Modifier.height(geometry.spacing.lg))
                when (state.mode) {
                    ScopeMode.PAGE -> PagePicker(
                        state = state,
                        persian = persian,
                        onFromPage = onFromPage,
                        onToPage = onToPage,
                        onFirstFive = onFirstFive,
                    )

                    ScopeMode.SURAH -> NumberPicker(
                        label = stringResource(R.string.downloads_surah_label, digits(state.surah, persian)),
                        value = state.surah,
                        range = 1..114,
                        onChange = onSurah,
                        testTag = "download_scope_surah",
                    )

                    ScopeMode.JUZ -> NumberPicker(
                        label = stringResource(R.string.downloads_juz_label, digits(state.juz, persian)),
                        value = state.juz,
                        range = 1..30,
                        onChange = onJuz,
                        testTag = "download_scope_juz",
                    )

                    ScopeMode.AYAH -> Column {
                        NumberPicker(
                            label = stringResource(R.string.downloads_surah_label, digits(state.surah, persian)),
                            value = state.surah,
                            range = 1..114,
                            onChange = onSurah,
                            testTag = "download_scope_surah",
                        )
                        Spacer(Modifier.height(geometry.spacing.md))
                        NumberPicker(
                            label = stringResource(
                                R.string.downloads_ayah_label,
                                digits(state.surah, persian),
                                digits(state.ayah, persian),
                            ),
                            value = state.ayah,
                            range = 1..state.ayahCountOfSurah.coerceAtLeast(1),
                            onChange = onAyah,
                            testTag = "download_scope_ayah",
                        )
                    }
                }

                Spacer(Modifier.height(geometry.spacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = scopeSummary(state, persian),
                        style = MaterialTheme.typography.bodyMedium,
                        color = glass.text,
                        modifier = Modifier.testTag("download_scope_summary"),
                    )
                    Text(
                        text = stringResource(
                            R.string.downloads_ayah_count,
                            digits(state.scopeAyahs, persian),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.textTertiary,
                    )
                }

                Spacer(Modifier.height(geometry.spacing.lg))
                PrimaryAction(
                    label = stringResource(
                        R.string.downloads_action,
                        sizeLabel(state.estimatedBytes, persian),
                    ),
                    enabled = state.canDownload,
                    busy = state.busy,
                    onClick = onDownload,
                )

                // Feedback for the last action, shown in place rather than as a snackbar: it
                // describes what the queue now holds, which is worth reading twice.
                val notice = state.notice
                if (notice != null) {
                    Spacer(Modifier.height(geometry.spacing.sm))
                    Text(
                        text = when (notice) {
                            is DownloadNotice.Queued -> stringResource(
                                R.string.downloads_notice_queued,
                                digits(notice.files, persian),
                                digits(notice.alreadyHave, persian),
                            )

                            DownloadNotice.CatalogueUnavailable ->
                                stringResource(R.string.downloads_catalogue_unavailable)

                            is DownloadNotice.Failed -> stringResource(
                                R.string.downloads_notice_failed,
                                notice.reason.orEmpty(),
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (notice is DownloadNotice.Failed) glass.danger else glass.textSecondary,
                        modifier = Modifier.testTag("download_notice"),
                    )
                }
            }

            Spacer(Modifier.height(geometry.spacing.xxl))
            SectionTitle(stringResource(R.string.downloads_saved))
            val groups = state.groups
            if (groups.isEmpty()) {
                Text(
                    text = stringResource(R.string.downloads_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = glass.textSecondary,
                )
                Spacer(Modifier.height(geometry.spacing.xs))
                Text(
                    text = stringResource(R.string.downloads_empty_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = glass.textTertiary,
                )
            } else {
                groups.forEach { group ->
                    EditionGroupCard(
                        group = group,
                        state = state,
                        persian = persian,
                        onRetry = onRetry,
                        onDelete = onDelete,
                        onDeleteGroup = { onDeleteGroup(group) },
                    )
                    Spacer(Modifier.height(geometry.spacing.md))
                }
            }
        }
    }
}

// ---- pickers -------------------------------------------------------------------------

/**
 * The page range, which is the unit the reader actually thinks in.
 *
 * Two sliders rather than a start-plus-count: "from page 3 to page 7" is how someone describes a
 * stretch of mushaf out loud, and a count would have to be converted in the head. The start slider
 * carries the end along when dragged past it, so the pair can never be inverted.
 *
 * The "first five pages" chip is not a special case in the model — it is the same two sliders set
 * to 1 and 5. It exists because it is the most common request by far, and asking for it should not
 * require finding two thumbs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PagePicker(
    state: DownloadsUiState,
    persian: Boolean,
    onFromPage: (Int) -> Unit,
    onToPage: (Int) -> Unit,
    onFirstFive: () -> Unit,
) {
    val geometry = LocalHawraaGeometry.current
    val glass = LocalGlassColors.current

    if (state.pageCount <= 0) {
        Text(
            text = stringResource(R.string.downloads_pages_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = glass.textSecondary,
        )
        return
    }

    NumberPicker(
        label = stringResource(R.string.downloads_page_from, digits(state.fromPage, persian)),
        value = state.fromPage,
        range = 1..state.pageCount,
        onChange = onFromPage,
        testTag = "download_scope_page_from",
    )
    Spacer(Modifier.height(geometry.spacing.md))
    NumberPicker(
        label = stringResource(R.string.downloads_page_to, digits(state.toPage, persian)),
        value = state.toPage,
        range = state.fromPage..state.pageCount,
        onChange = onToPage,
        testTag = "download_scope_page_to",
    )

    Spacer(Modifier.height(geometry.spacing.sm))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(geometry.spacing.sm)) {
        GlassChip(
            label = stringResource(R.string.downloads_first_five),
            selected = state.fromPage == 1 && state.toPage == 5,
            onClick = onFirstFive,
            modifier = Modifier.testTag("download_first_five"),
        )
    }
}

/** A labelled slider over an integer range, snapped to whole numbers. */
@Composable
private fun NumberPicker(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    testTag: String,
) {
    val geometry = LocalHawraaGeometry.current
    val glass = LocalGlassColors.current
    val first = range.first
    val last = range.last.coerceAtLeast(first)

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = glass.text,
        )
        Slider(
            value = value.coerceIn(first, last).toFloat(),
            onValueChange = { raw -> onChange(raw.roundToInt().coerceIn(first, last)) },
            valueRange = first.toFloat()..last.toFloat(),
            modifier = Modifier.fillMaxWidth().testTag(testTag),
        )
        Spacer(Modifier.height(geometry.spacing.xxs))
    }
}

// ---- sections ------------------------------------------------------------------------

@Composable
private fun StorageSummary(totalBytes: Long, pendingFiles: Int, persian: Boolean) {
    val geometry = LocalHawraaGeometry.current
    val glass = LocalGlassColors.current
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(geometry.radius.card),
        strong = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = geometry.spacing.lg, vertical = geometry.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.downloads_storage),
                    style = MaterialTheme.typography.labelMedium,
                    color = glass.textSecondary,
                )
                Text(
                    text = sizeLabel(totalBytes, persian),
                    style = MaterialTheme.typography.titleLarge,
                    color = glass.text,
                )
            }
            if (pendingFiles > 0) {
                Text(
                    text = stringResource(R.string.downloads_pending, digits(pendingFiles, persian)),
                    style = MaterialTheme.typography.labelMedium,
                    color = glass.accent,
                )
            }
        }
    }
}

@Composable
private fun CatalogueUnavailable() {
    val geometry = LocalHawraaGeometry.current
    val glass = LocalGlassColors.current
    val shape = RoundedCornerShape(geometry.radius.sm)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(glass.danger.copy(alpha = if (glass.dark) 0.18f else 0.09f))
            .border(BorderStroke(1.dp, glass.danger.copy(alpha = 0.32f)), shape)
            .padding(horizontal = geometry.spacing.md, vertical = geometry.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(geometry.spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = glass.danger,
            modifier = Modifier.size(geometry.icons.sm),
        )
        Text(
            text = stringResource(R.string.downloads_catalogue_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = glass.danger,
        )
    }
}

@Composable
private fun EditionChip(
    edition: EditionInfo,
    selected: Boolean,
    persian: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassChip(
            label = edition.displayLabel(persian),
            selected = selected,
            onClick = onClick,
            modifier = Modifier.testTag("download_edition_${edition.key}"),
        )
        Text(
            text = edition.technicalSummary(),
            // `48 kbps · ayah` is a technical token, not prose: in an RTL paragraph the bidi
            // algorithm reorders it into `kb · ayah 48`, which reads as a different fact.
            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
            color = LocalGlassColors.current.textTertiary,
        )
    }
}

/**
 * One edition's holdings: how much, how far along, and every scope under it.
 *
 * The header is the answer to "how much of this reciter do I have", the bar is the answer to "is it
 * finished", and the rows below are the answer to "which parts". Deleting is offered twice on
 * purpose — per scope for surgical removal, and per edition for "free this space" — because after
 * a five-page download the second is the only one anyone wants.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditionGroupCard(
    group: EditionGroup,
    state: DownloadsUiState,
    persian: Boolean,
    onRetry: (DownloadBatch) -> Unit,
    onDelete: (DownloadBatch) -> Unit,
    onDeleteGroup: () -> Unit,
) {
    val geometry = LocalHawraaGeometry.current
    val glass = LocalGlassColors.current
    val reciterName = state.reciters.firstOrNull { it.key == group.reciterKey }?.displayName(persian)
        ?: group.reciterKey
    val editionLabel = state.reciters
        .asSequence()
        .flatMap { it.editions.asSequence() }
        .firstOrNull { it.key == group.editionKey }
        ?.displayLabel(persian)
        .orEmpty()

    GlassSurface(
        modifier = Modifier.fillMaxWidth().testTag("download_group_${group.editionKey}"),
        shape = RoundedCornerShape(geometry.radius.md),
        rimLight = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = geometry.spacing.lg, vertical = geometry.spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reciterName,
                        style = MaterialTheme.typography.titleSmall,
                        color = glass.text,
                    )
                    if (editionLabel.isNotBlank()) {
                        Text(
                            text = editionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = glass.textTertiary,
                        )
                    }
                }
                if (group.isComplete) {
                    Text(
                        text = stringResource(R.string.downloads_complete),
                        style = MaterialTheme.typography.labelMedium,
                        color = glass.accent,
                    )
                }
            }

            Spacer(Modifier.height(geometry.spacing.sm))
            LinearProgressIndicator(
                progress = { group.fraction },
                modifier = Modifier.fillMaxWidth(),
                color = glass.accent,
                trackColor = glass.track,
            )

            Spacer(Modifier.height(geometry.spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val failed = if (group.failed > 0) {
                    stringResource(R.string.downloads_failed_count, digits(group.failed, persian))
                } else {
                    null
                }
                Text(
                    text = listOfNotNull(
                        sizeLabel(group.bytes, persian),
                        stringResource(
                            R.string.downloads_progress,
                            digits(group.done, persian),
                            digits(group.total, persian),
                        ),
                        failed,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (group.failed > 0) glass.danger else glass.textTertiary,
                )
                TextAction(
                    label = stringResource(R.string.downloads_delete_all),
                    onClick = onDeleteGroup,
                    tint = glass.danger,
                )
            }

            Spacer(Modifier.height(geometry.spacing.sm))
            group.batches.forEach { batch ->
                BatchRow(
                    batch = batch,
                    persian = persian,
                    onRetry = { onRetry(batch) },
                    onDelete = { onDelete(batch) },
                )
            }
        }
    }
}

@Composable
private fun BatchRow(
    batch: DownloadBatch,
    persian: Boolean,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val geometry = LocalHawraaGeometry.current
    val glass = LocalGlassColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = geometry.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = scopeLabel(DownloadScope.parse(batch.scopeKey), batch.scopeKey, persian),
                style = MaterialTheme.typography.bodySmall,
                color = glass.textSecondary,
            )
            Text(
                text = listOfNotNull(
                    stringResource(
                        R.string.downloads_progress,
                        digits(batch.done, persian),
                        digits(batch.total, persian),
                    ),
                    if (batch.failed > 0) {
                        stringResource(R.string.downloads_failed_count, digits(batch.failed, persian))
                    } else {
                        null
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = if (batch.failed > 0) glass.danger else glass.textTertiary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(geometry.spacing.md)) {
            if (batch.failed > 0) {
                TextAction(
                    label = stringResource(R.string.downloads_retry),
                    onClick = onRetry,
                    tint = glass.accent,
                )
            }
            TextAction(
                label = stringResource(R.string.downloads_delete),
                onClick = onDelete,
                tint = glass.danger,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = LocalGlassColors.current.textSecondary,
        modifier = Modifier.padding(bottom = LocalHawraaGeometry.current.spacing.sm),
    )
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit, tint: Color) {
    val geometry = LocalHawraaGeometry.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(geometry.radius.pill))
            .clickable(onClick = onClick)
            .padding(horizontal = geometry.spacing.md, vertical = geometry.spacing.xs)
            .semantics { contentDescription = label },
    )
}

/** The one filled action on this screen: accent fill, accent-tinted label. */
@Composable
private fun PrimaryAction(label: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val geometry = LocalHawraaGeometry.current
    val glass = LocalGlassColors.current
    val shape = RoundedCornerShape(geometry.radius.pill)
    val active = enabled && !busy

    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (active) glass.onAccent else glass.textTertiary,
        // Centred: the pill is full-width, and a start-aligned label sits against the border.
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) glass.accent else glass.glassFill)
            .border(BorderStroke(1.dp, if (active) glass.accent else glass.glassBorder), shape)
            .clickable(enabled = active, onClick = onClick)
            .padding(vertical = geometry.spacing.md)
            .testTag("download_action"),
    )
}

// ---- labels ---------------------------------------------------------------------------

/** The scope in the reader's own words: pages when they picked pages, verses otherwise. */
@Composable
private fun scopeSummary(state: DownloadsUiState, persian: Boolean): String {
    val pages = state.scopePages
    if (state.mode == ScopeMode.PAGE && pages != null) {
        return stringResource(
            R.string.downloads_page_label,
            digits(pages.first, persian),
            digits(pages.second, persian),
        )
    }
    return scopeLabel(state.scope, state.scope.scopeKey, persian)
}

@Composable
private fun granularityNote(granularity: EditionGranularity): String = when (granularity) {
    EditionGranularity.AYAH -> stringResource(R.string.downloads_granularity_ayah)
    EditionGranularity.SURAH -> stringResource(R.string.downloads_granularity_surah)
    EditionGranularity.JUZ -> stringResource(R.string.downloads_granularity_juz)
}

/**
 * `surah 2` / `juz 1` / `2:6 – 2:16` in the reader's language and digits. An unrecognised scope key
 * is shown verbatim rather than guessed at, so a batch from a newer build is still identifiable.
 */
@Composable
private fun scopeLabel(scope: DownloadScope?, raw: String, persian: Boolean): String = when (scope) {
    is DownloadScope.Surah -> stringResource(R.string.downloads_surah_label, digits(scope.surah, persian))
    is DownloadScope.Juz -> stringResource(R.string.downloads_juz_label, digits(scope.number, persian))
    is DownloadScope.Ayah -> stringResource(
        R.string.downloads_ayah_label,
        digits(scope.surah, persian),
        digits(scope.ayah, persian),
    )

    is DownloadScope.Range -> stringResource(
        R.string.downloads_range_label,
        digits(scope.fromSurah, persian),
        digits(scope.fromAyah, persian),
        digits(scope.toSurah, persian),
        digits(scope.toAyah, persian),
    )

    null -> raw
}

/** Persian digits when the UI is Persian; plain otherwise. */
private fun digits(value: Int, persian: Boolean): String =
    if (persian) value.toString().toPersianDigits() else value.toString()

/**
 * A byte count in the reader's digits and language. The unit is a resource, the number is not:
 * a Persian reader must see `۱۲٫۴ مگابایت`, not `12.4 MB` and not `۱۲٫۴ MB`.
 */
@Composable
private fun sizeLabel(bytes: Long, persian: Boolean): String {
    val size = DownloadSizing.human(bytes)
    val raw = if (size.value < 10.0) {
        String.format(Locale.ROOT, "%.1f", size.rounded())
    } else {
        size.rounded().toInt().toString()
    }
    val number = if (persian) raw.replace(".", "\u066B").toPersianDigits() else raw
    val unit = when (size.unit) {
        SizeUnit.KB -> stringResource(R.string.size_kb)
        SizeUnit.MB -> stringResource(R.string.size_mb)
        SizeUnit.GB -> stringResource(R.string.size_gb)
    }
    return "$number $unit"
}
