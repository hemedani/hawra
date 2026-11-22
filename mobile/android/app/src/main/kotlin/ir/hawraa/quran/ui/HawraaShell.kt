package ir.hawraa.quran.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
import ir.hawraa.quran.ui.components.DockTab
import ir.hawraa.quran.ui.components.LocalBackdrop
import ir.hawraa.quran.ui.components.LocalSheetPresence
import ir.hawraa.quran.ui.components.LocalShellInsets
import ir.hawraa.quran.ui.components.NavWaveKind
import ir.hawraa.quran.ui.components.NowPlayingUi
import ir.hawraa.quran.ui.components.SheetPresence
import ir.hawraa.quran.ui.components.ShellInsets
import ir.hawraa.quran.ui.components.WaveSweep
import ir.hawraa.quran.ui.components.backdropSource
import ir.hawraa.quran.ui.components.rememberBackdrop
import ir.hawraa.quran.ui.navigation.HawraaNavHost
import ir.hawraa.quran.ui.navigation.Routes
import ir.hawraa.quran.ui.navigation.navWaveKind
import ir.hawraa.quran.ui.screens.auth.AuthScreen
import ir.hawraa.quran.ui.screens.player.NowPlayingSheet
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry

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
    val player by viewModel.player.collectAsStateWithLifecycle()
    val playerExpanded by viewModel.playerExpanded.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val geometry = LocalHawraaGeometry.current

    // One recorded current per route change: the ambient canvas surges and the WaveSweep
    // crest crosses in the same direction. The kind is decided from the tab order first,
    // then from the back-stack depth, so a dock tap drifts while a detail screen rises.
    var previousRoute by remember { mutableStateOf<String?>(null) }
    var previousDepth by remember { mutableIntStateOf(0) }
    var navTrigger by remember { mutableIntStateOf(0) }
    var navKind by remember { mutableStateOf<NavWaveKind>(NavWaveKind.Push) }
    LaunchedEffect(backStackEntry?.id) {
        val entry = backStackEntry ?: return@LaunchedEffect
        val toRoute = entry.destination.route
        val depth = navController.currentBackStack.value.size
        if (previousRoute == null) {
            previousRoute = toRoute
            previousDepth = depth
            return@LaunchedEffect
        }
        navKind = navWaveKind(previousRoute, toRoute, previousDepth, depth)
        previousRoute = toRoute
        previousDepth = depth
        navTrigger++
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val horizontalSign = if (rtl) -1f else 1f
    val nav = geometry.navMotion
    val (canvasShiftX, canvasShiftY) = when (val kind = navKind) {
        is NavWaveKind.TabDrift -> {
            val x = if (kind.dir >= 0) -nav.canvasTabShift else nav.canvasTabShift
            x * horizontalSign to nav.canvasTabY
        }
        NavWaveKind.Push -> nav.canvasPushX to nav.canvasPushY
        NavWaveKind.Pop -> nav.canvasPopX to nav.canvasPopY
    }

    // The sheet streams its morph progress here so the chrome behind it can frost while the
    // player opens. The radius is computed for both API bands: on 31+ it feeds `Modifier.blur`,
    // and on 29–30 the sheet reads it as its scrim alpha (there is no RenderEffect blur there).
    var morphProgress by remember { mutableFloatStateOf(0f) }
    val sheetPresence = remember { SheetPresence() }
    val backdrop = rememberBackdrop()
    val shellInsets = remember(geometry.dock.dockInset) {
        ShellInsets(bottom = geometry.dock.dockInset)
    }
    val playerRadius: Dp = geometry.frost.backdrop * morphProgress.coerceIn(0f, 1f)
    val backdropRadius: Dp = when {
        sheetPresence.count > 0 -> geometry.frost.backdrop
        else -> playerRadius
    }
    val backdropBlurModifier = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && backdropRadius.value > 0f
    ) {
        Modifier.blur(backdropRadius)
    } else {
        Modifier
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientCanvas(
            navTrigger = navTrigger,
            navShiftX = canvasShiftX,
            navShiftY = canvasShiftY,
            modifier = Modifier.backdropSource(backdrop, zIndex = 0f),
        )
        when (authState) {
            AuthState.Restoring -> Unit
            AuthState.SignedOut -> AuthScreen()
            is AuthState.SignedIn -> {
                CompositionLocalProvider(
                    LocalBackdrop provides backdrop,
                    LocalShellInsets provides shellInsets,
                    LocalSheetPresence provides sheetPresence,
                ) {
                // The nav host and the dock are the chrome the player frosts. They live in their
                // own box so the blur never touches the sheet, which is the surface the blur is
                // there to reveal.
                Box(modifier = Modifier.fillMaxSize().then(backdropBlurModifier)) {
                    HawraaNavHost(navController = navController)
                    // The full-screen player covers the whole app, so the dock gets out of the
                    // way while it is open.
                    if (!playerExpanded) {
                        AppDock(
                            selectedRoute = route ?: Routes.HOME,
                            onSelect = { tab -> navController.navigateTopLevel(tab.navigationRoute()) },
                            modifier = Modifier.align(Alignment.BottomCenter),
                            nowPlaying = player?.mini()?.let { it.toUi() },
                            miniProgress = viewModel.miniProgress,
                            onPlayPause = viewModel::togglePlayPause,
                            onNext = viewModel::nextAyah,
                            onExpand = viewModel::openControls,
                            onMiniPlayerRect = viewModel::onMiniPlayerRect,
                        )
                    }
                    // The crest crosses above the chrome but stays inside the blurred
                    // box, so the player sheet remains the topmost surface. `WaveSweep`
                    // mirrors the logical tab direction itself for RTL, so the shell
                    // passes the classifier's kind unchanged.
                    WaveSweep(
                        trigger = navTrigger,
                        kind = navKind,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // The sheet is a sibling of the blurred chrome, drawn above it, so it survives
                // navigation and morphs out of the dock's own row rather than appearing as a
                // second surface.
                NowPlayingSheet(
                    store = viewModel.playerStore,
                    backdropBlur = backdropRadius.value,
                    onExpandProgress = { morphProgress = it },
                    miniProgress = viewModel.miniProgress,
                )
                }
            }
        }
    }
}

/** The navigable route for a dock peer. The reader is opened at its saved/start page. */
private fun DockTab.navigationRoute(): String = when (this) {
    DockTab.Home -> Routes.HOME
    DockTab.Read -> Routes.reader()
    DockTab.History -> Routes.HISTORY
    DockTab.Settings -> Routes.SETTINGS
}

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
        val number = if (persian) juz.toPersianDigits() else juz.toString()
        return NowPlayingUi(
            title = stringResource(R.string.dock_juz_title, number),
            subtitle = stringResource(R.string.dock_juz_subtitle, reciter),
            number = number,
            isPlaying = isPlaying,
            waiting = busy,
        )
    }
    return NowPlayingUi(
        title = surahName,
        subtitle = stringResource(
            R.string.dock_now_playing,
            if (persian) ayah.toPersianDigits() else ayah.toString(),
            reciter,
        ),
        number = if (persian) surahNumber.toPersianDigits() else surahNumber.toString(),
        isPlaying = isPlaying,
        waiting = busy,
    )
}
