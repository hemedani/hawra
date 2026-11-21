package ir.hawraa.quran.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalHawraaTokens
import ir.hawraa.quran.ui.theme.Vazirmatn

/** Empty state — one of four distinct states (DESIGN.md §9.6). Never a blank list. */
@Composable
fun EmptyState(
    icon: String,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
) {
    val tokens = LocalHawraaTokens.current
    val geo = LocalHawraaGeometry.current
    Column(
        modifier = modifier.fillMaxWidth().padding(geo.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(geo.spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(geo.tiles.xl)
                .clip(CircleShape)
                .background(tokens.glassFill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                name = icon,
                contentDescription = null,
                tint = tokens.textSecondary,
                size = geo.icons.xl,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Vazirmatn),
            color = tokens.text,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Vazirmatn),
                color = tokens.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

enum class NoticeTone { Info, Warning, Danger }

/** An inline notice (error/offline/warning) with an optional retry — not a bare caption. */
@Composable
fun InlineNotice(
    message: String,
    modifier: Modifier = Modifier,
    tone: NoticeTone = NoticeTone.Info,
    title: String? = null,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
) {
    val tokens = LocalHawraaTokens.current
    val geo = LocalHawraaGeometry.current
    val accent = when (tone) {
        NoticeTone.Info -> tokens.accentTeal
        NoticeTone.Warning -> tokens.textSecondary
        NoticeTone.Danger -> tokens.danger
    }
    val icon = when (tone) {
        NoticeTone.Info -> "info"
        NoticeTone.Warning -> "warning"
        NoticeTone.Danger -> "error"
    }
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(geo.radius.lg),
        rimLight = false,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(geo.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(name = icon, contentDescription = null, tint = accent, size = geo.icons.lg)
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = geo.spacing.md),
                verticalArrangement = Arrangement.spacedBy(geo.spacing.xs),
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = Vazirmatn),
                        color = tokens.text,
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Vazirmatn),
                    color = tokens.text,
                )
                if (action != null) {
                    action()
                }
            }
            if (retryLabel != null && onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text(
                        text = retryLabel,
                        color = accent,
                        fontFamily = Vazirmatn,
                    )
                }
            }
        }
    }
}

/** Static skeleton rows (no shimmer loop) — the loading state. */
@Composable
fun SkeletonRows(
    modifier: Modifier = Modifier,
    count: Int = 5,
) {
    val tokens = LocalHawraaTokens.current
    val geo = LocalHawraaGeometry.current
    GlassSurface(modifier = modifier, rimLight = false) {
        Column(verticalArrangement = Arrangement.spacedBy(geo.spacing.lg)) {
            repeat(count) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(geo.tiles.md)
                            .clip(RoundedCornerShape(geo.radius.sm))
                            .background(tokens.glassFill),
                    )
                    Column(
                        modifier = Modifier.weight(1f).padding(start = geo.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(geo.spacing.sm),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(geo.skeleton.lineWidth)
                                .height(geo.skeleton.lineHeight)
                                .clip(CircleShape)
                                .background(tokens.glassFill),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth(geo.skeleton.lineWidthSmall)
                                .height(geo.skeleton.lineHeightSmall)
                                .clip(CircleShape)
                                .background(tokens.glassFill),
                        )
                    }
                }
            }
        }
    }
}
