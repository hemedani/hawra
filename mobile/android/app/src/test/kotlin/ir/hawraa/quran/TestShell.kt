package ir.hawraa.quran

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ir.hawraa.quran.ui.components.AmbientCanvas
import ir.hawraa.quran.ui.components.AppDock
import ir.hawraa.quran.ui.components.DockIcons
import ir.hawraa.quran.ui.components.DockItem
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
 * The four destinations and their glyphs are copied from `HawraaShell.dockItems` **including**
 * `DockIcons.History`, which is hand-drawn because `material-icons-core` has no clock. Using a stock
 * icon here once meant that drawing was never rendered by any screenshot.
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
            items = testDockItems(selected),
            modifier = Modifier.align(Alignment.BottomCenter),
            nowPlaying = nowPlaying,
        )
    }
}

@Composable
internal fun testDockItems(selected: String): List<DockItem> = listOf(
    DockItem("home", stringResource(R.string.home_title), Icons.Filled.Home, selected == "home") {},
    DockItem(
        "read",
        stringResource(R.string.reader_title),
        Icons.AutoMirrored.Filled.List,
        selected == "read",
    ) {},
    DockItem("history", stringResource(R.string.history_title), DockIcons.History, selected == "history") {},
    DockItem("settings", stringResource(R.string.settings_title), Icons.Filled.Settings, selected == "settings") {},
)
