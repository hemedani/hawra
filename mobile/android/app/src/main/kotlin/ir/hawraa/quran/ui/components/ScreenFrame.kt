package ir.hawraa.quran.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry

/**
 * Wraps a screen with the floating [GlassHeader] and optional [subHeader] (e.g. a
 * floating tab-chip row) and tells the body the total top padding those chrome layers
 * occupy. Both [header] and [subHeader] live as siblings outside the [backdropSource]
 * body so they never sample themselves while the list scrolls smoothly behind them.
 *
 * The body is a backdrop source at `zIndex = 1f`; outside the shell ([LocalBackdrop] is
 * null) it simply draws its content.
 */
@Composable
fun ScreenFrame(
    modifier: Modifier = Modifier,
    header: @Composable (Modifier) -> Unit,
    subHeader: (@Composable () -> Unit)? = null,
    content: @Composable (topInset: Dp) -> Unit,
) {
    val geo = LocalHawraaGeometry.current
    val density = LocalDensity.current
    val backdrop = LocalBackdrop.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var headerHeight by remember {
        mutableStateOf(statusTop + geo.header.minHeight + geo.header.margin * 2)
    }
    var subHeaderHeight by remember { mutableStateOf(geo.spacing.none) }
    val contentTopInset = if (subHeader != null) {
        headerHeight + geo.spacing.xs + subHeaderHeight + geo.spacing.sm
    } else {
        headerHeight
    }

    Box(modifier.fillMaxSize()) {
        // The recorded backdrop is the body; the header and sub-header panes above
        // sample it. The shell's modal blur lives on an ancestor of this node, never on it.
        Box(
            Modifier
                .fillMaxSize()
                .backdropSource(backdrop, zIndex = 1f),
        ) {
            content(contentTopInset)
        }
        header(
            Modifier
                .align(Alignment.TopCenter)
                .onGloballyPositioned { coords ->
                    if (coords.size.height > 0) {
                        headerHeight = with(density) { coords.size.height.toDp() }
                    }
                },
        )
        if (subHeader != null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(top = headerHeight + geo.spacing.xs)
                    .onGloballyPositioned { coords ->
                        if (coords.size.height > 0) {
                            val measured = with(density) { coords.size.height.toDp() }
                            if (subHeaderHeight != measured) subHeaderHeight = measured
                        }
                    },
            ) {
                subHeader()
            }
        }
    }
}
