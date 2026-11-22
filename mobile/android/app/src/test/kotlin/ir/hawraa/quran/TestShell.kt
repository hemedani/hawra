package ir.hawraa.quran

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import ir.hawraa.quran.ui.components.AmbientCanvas
import ir.hawraa.quran.ui.components.AppDock
import ir.hawraa.quran.ui.components.LocalBackdrop
import ir.hawraa.quran.ui.components.LocalShellInsets
import ir.hawraa.quran.ui.components.NowPlayingUi
import ir.hawraa.quran.ui.components.ShellInsets
import ir.hawraa.quran.ui.components.backdropSource
import ir.hawraa.quran.ui.components.rememberBackdrop
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry

/**
 * The screenshot harness's copy of the app frame: ambient canvas, a screen, and the dock — the same
 * composition `HawraaShell` builds (`docs/DESIGN.md` §10.4, §11).
 *
 * This exists because **every screen is deliberately transparent** so the shell's canvas can show
 * through. A capture of a bare `HomeContent`/`SettingsContent` therefore renders on the window's
 * default white, which is a screen the app never shows — and a visual gate that photographs a screen
 * the app never shows is not a gate. The dock is included for the same reason: it is part of the
 * layout on every destination, and leaving it out hides exactly the defects it is most likely to
 * have (content sliding under it, the seam, the collapsed height).
 *
 * Like the real shell, it records the canvas as a [LocalBackdrop] source and measures the dock into
 * [LocalShellInsets] so a screen's glass chrome and its bottom clearance match production.
 *
 * [selected] is a route key (`home`, `reader`, `history`, `settings`).
 */
@Composable
internal fun TestShell(
    selected: String,
    modifier: Modifier = Modifier,
    nowPlaying: NowPlayingUi? = null,
    content: @Composable () -> Unit,
) {
    val geometry = LocalHawraaGeometry.current
    val density = LocalDensity.current
    val backdrop = rememberBackdrop()
    var dockClearance by remember { mutableStateOf(geometry.dock.dockInset) }

    CompositionLocalProvider(
        LocalBackdrop provides backdrop,
        LocalShellInsets provides ShellInsets(bottom = dockClearance),
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            AmbientCanvas(Modifier.backdropSource(backdrop, zIndex = 0f))
            content()
            AppDock(
                selectedRoute = selected,
                onSelect = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onGloballyPositioned { coords ->
                        if (coords.isAttached && coords.size.height > 0) {
                            dockClearance = with(density) { coords.size.height.toDp() }
                        }
                    },
                nowPlaying = nowPlaying,
            )
        }
    }
}
