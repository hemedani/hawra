package ir.hawraa.quran.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ir.hawraa.quran.R
import ir.hawraa.quran.domain.auth.AuthState
import ir.hawraa.quran.domain.quran.toPersianDigits
import ir.hawraa.quran.ui.components.AmbientCanvas
import ir.hawraa.quran.ui.components.AppDock
import ir.hawraa.quran.ui.components.DockIcons
import ir.hawraa.quran.ui.components.DockItem
import ir.hawraa.quran.ui.components.NowPlayingUi
import ir.hawraa.quran.ui.navigation.HawraaNavHost
import ir.hawraa.quran.ui.navigation.Routes
import ir.hawraa.quran.ui.recitation.NowPlayingStore
import ir.hawraa.quran.ui.screens.auth.AuthScreen

/**
 * The app shell: an ambient canvas, the navigation graph, and the dock floating over both
 * (`docs/DESIGN.md` §10.4, §11).
 *
 * The dock is drawn **here** rather than by each screen for the same reason the reference app does
 * it: one panel that survives navigation. If every screen drew its own copy, moving between them
 * would tear the player half down and rebuild it, and the two halves would drift apart the moment
 * one screen changed its padding.
 *
 * Screens do not know about the dock; they only owe it [ir.hawraa.quran.ui.theme.DockGeometry.dockInset]
 * of bottom padding so the last row is never hidden behind it.
 *
 * The **gate** lives here too, because it is the one decision that has to be made before any
 * destination exists. While it is closed the dock is not drawn at all: a navigation bar that leads
 * to screens the reader is not allowed to see would be worse than no bar at all. `Restoring` draws
 * the canvas and nothing else — a blink, not a screen.
 */
@Composable
fun HawraaShell(
    viewModel: ShellViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val playerExpanded by viewModel.playerExpanded.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientCanvas()
        when (authState) {
            AuthState.Restoring -> Unit
            AuthState.SignedOut -> AuthScreen()
            is AuthState.SignedIn -> {
                HawraaNavHost(navController = navController)
                // The full-screen player covers the whole app, so the dock gets out of the way
                // while it is open. The player is drawn by the reader (it owns the engine), above
                // the nav host; the dock is the only shell chrome that must yield to it.
                if (!playerExpanded) {
                    AppDock(
                        items = dockItems(navController, route),
                        modifier = Modifier.align(Alignment.BottomCenter),
                        nowPlaying = nowPlaying?.let { it.toUi() },
                        onPlayPause = viewModel::togglePlayPause,
                        onNext = viewModel::nextAyah,
                        onOpenPlayer = viewModel::openControls,
                    )
                }
            }
        }
    }
}

/**
 * The four destinations, in order. Icon-only peers — no labels, no raised centre action
 * (`docs/DESIGN.md` §10.4).
 */
@Composable
private fun dockItems(navController: NavHostController, route: String?): List<DockItem> = listOf(
    DockItem(
        key = "home",
        label = stringResource(R.string.home_title),
        icon = Icons.Filled.Home,
        selected = route == Routes.HOME,
        onClick = { navController.navigateTopLevel(Routes.HOME) },
    ),
    DockItem(
        key = "read",
        label = stringResource(R.string.reader_title),
        icon = Icons.AutoMirrored.Filled.List,
        selected = route?.startsWith("reader") == true,
        onClick = { navController.navigateTopLevel(Routes.reader()) },
    ),
    DockItem(
        key = "history",
        label = stringResource(R.string.history_title),
        icon = DockIcons.History,
        selected = route == Routes.HISTORY,
        onClick = { navController.navigateTopLevel(Routes.HISTORY) },
    ),
    DockItem(
        key = "settings",
        label = stringResource(R.string.settings_title),
        icon = Icons.Filled.Settings,
        selected = route == Routes.SETTINGS,
        onClick = { navController.navigateTopLevel(Routes.SETTINGS) },
    ),
)

/**
 * The dock's navigation is a **sibling switch**, not a push.
 *
 * `popUpTo(start) { saveState }` + `restoreState` keeps one entry per destination, so tapping
 * "Read" a second time returns to the page the reader had reached instead of resetting it — the
 * saved `SavedStateHandle` is what carries the start page. `launchSingleTop` stops a re-tap from
 * stacking duplicates.
 */
private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Localises the store's model for the dock. Both scripts are carried; the view decides. */
@Composable
private fun ir.hawraa.quran.ui.recitation.NowPlaying.toUi(): NowPlayingUi {
    val persian = LocalConfiguration.current.appLocale().language == "fa"
    val surahName = if (persian) surahNameFa else surahNameEn
    val reciter = if (persian) reciterNameFa else reciterNameEn
    val juz = juzNumber
    if (juz != null) {
        // A juz file has no verse position, so the row says what is actually sounding. Showing
        // "ayah 1" here would be the juz's first verse, which is not where playback started.
        //
        // The tile carries the **juz** number, not the juz's starting surah: the title already
        // reads «جزء ۵», and a tile reading «۴» beside it would put two different numbers in one
        // 58 dp row with nothing to say which is which.
        return NowPlayingUi(
            title = stringResource(
                R.string.dock_juz_title,
                if (persian) juz.toPersianDigits() else juz.toString(),
            ),
            subtitle = stringResource(R.string.dock_juz_subtitle, reciter),
            tileNumber = juz,
            isPlaying = isPlaying,
            busy = busy,
        )
    }
    return NowPlayingUi(
        title = surahName,
        subtitle = stringResource(
            R.string.dock_now_playing,
            ayah.toPersianDigits(),
            reciter,
        ),
        tileNumber = surahNumber,
        isPlaying = isPlaying,
        busy = busy,
    )
}
