package ir.hawraa.quran

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ir.hawraa.quran.ui.components.AmbientCanvas
import ir.hawraa.quran.ui.components.AppDock
import ir.hawraa.quran.ui.components.NowPlayingUi

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
 * [selected] is a route key (`home`, `reader`, `history`, `settings`).
 */
@Composable
internal fun TestShell(
    selected: String,
    modifier: Modifier = Modifier,
    nowPlaying: NowPlayingUi? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AmbientCanvas()
        content()
        AppDock(
            selectedRoute = selected,
            onSelect = {},
            modifier = Modifier.align(Alignment.BottomCenter),
            nowPlaying = nowPlaying,
        )
    }
}
