package ir.hawraa.quran.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.hawraa.quran.ui.components.NavWaveKind
import ir.hawraa.quran.ui.screens.downloads.DownloadsScreen
import ir.hawraa.quran.ui.screens.history.HistoryScreen
import ir.hawraa.quran.ui.screens.home.HomeScreen
import ir.hawraa.quran.ui.screens.presets.PresetEditorScreen
import ir.hawraa.quran.ui.screens.presets.PresetsScreen
import ir.hawraa.quran.ui.screens.reader.ReaderScreen
import ir.hawraa.quran.ui.screens.settings.SettingsScreen
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.NavMotionGeometry
import ir.hawraa.quran.ui.theme.OceanSwellEasing
import ir.hawraa.quran.ui.theme.useMotionEnabled

object Routes {
    const val HOME = "home"
    /**
     * The reader takes the page **index** (0-based) to open on, so "continue reading" can
     * land on the right page without a second source of truth.
     */
    const val READER = "reader?page={page}"

    fun reader(pageIndex: Int = 0): String = "reader?page=$pageIndex"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    /** Offline audio: catalogue, scope picker and the download queue. */
    const val DOWNLOADS = "downloads"

    /** The preset list, and its editor with an optional `clientId` (absent = create). */
    const val PRESETS = "presets"
    const val PRESET_EDITOR = "presets/edit?clientId={clientId}"

    fun presetEditor(clientId: String? = null): String =
        "presets/edit?clientId=${clientId.orEmpty()}"
}

@Composable
fun HawraaNavHost(navController: NavHostController = rememberNavController()) {
    val motion = useMotionEnabled()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val nav = LocalHawraaGeometry.current.navMotion
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            oceanEnter(motion, rtl, nav, initialState.destination.route, targetState.destination.route)
        },
        exitTransition = {
            oceanExit(motion, rtl, nav, initialState.destination.route, targetState.destination.route)
        },
        popEnterTransition = {
            oceanPopEnter(motion, rtl, nav, initialState.destination.route, targetState.destination.route)
        },
        popExitTransition = {
            oceanPopExit(motion, rtl, nav, initialState.destination.route, targetState.destination.route)
        },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onStartReading = { navController.navigate(Routes.reader()) },
                onResume = { pageIndex -> navController.navigate(Routes.reader(pageIndex)) },
                onHistory = { navController.navigate(Routes.HISTORY) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.READER,
            arguments = listOf(
                navArgument("page") {
                    type = NavType.IntType
                    defaultValue = 0
                },
            ),
        ) {
            ReaderScreen(
                onBack = { navController.popBackStack() },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onDownloads = { navController.navigate(Routes.DOWNLOADS) },
                onPresets = { navController.navigate(Routes.PRESETS) },
            )
        }
        composable(Routes.DOWNLOADS) {
            DownloadsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PRESETS) {
            PresetsScreen(
                onBack = { navController.popBackStack() },
                onNew = { navController.navigate(Routes.presetEditor()) },
                onEdit = { clientId -> navController.navigate(Routes.presetEditor(clientId)) },
            )
        }
        composable(
            route = Routes.PRESET_EDITOR,
            arguments = listOf(
                navArgument("clientId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            PresetEditorScreen(onBack = { navController.popBackStack() })
        }
    }
}

/**
 * The dock's four peers in display order. Tab switches drift laterally along this
 * list; every other route is a push or a pop.
 */
internal val tabOrder: List<String> = listOf(
    Routes.HOME,
    Routes.READER,
    Routes.HISTORY,
    Routes.SETTINGS,
)

internal fun tabOrderIndex(route: String?): Int = tabOrder.indexOf(route)

/**
 * Logical tab-order direction, mirrored for RTL so a dock that reads right-to-left
 * glides the other way on screen.
 */
internal fun tabDriftDirection(fromTab: Int, toTab: Int, rtl: Boolean): Int {
    val logical = if (toTab > fromTab) 1 else -1
    return if (rtl) -logical else logical
}

/**
 * Classify a route change for the shell: a switch between dock peers drifts along the
 * tab order, a deeper stack rises (push), and a shallower one reverses (pop).
 */
internal fun navWaveKind(
    fromRoute: String?,
    toRoute: String?,
    fromDepth: Int,
    toDepth: Int,
): NavWaveKind {
    val fromTab = tabOrderIndex(fromRoute)
    val toTab = tabOrderIndex(toRoute)
    if (fromTab >= 0 && toTab >= 0 && fromTab != toTab) {
        return NavWaveKind.TabDrift(if (toTab > fromTab) 1 else -1)
    }
    return if (toDepth > fromDepth) NavWaveKind.Push else NavWaveKind.Pop
}

/**
 * Page transitions in Pejvak's ocean vocabulary (DESIGN.md §9):
 * - dock peers glide laterally along [tabOrder] with a subtle buoyant crest;
 * - a push rises into clarity while the parent recedes deeper;
 * - a pop mirrors the dive smoothly.
 * Scale stays intentionally subtle so typography never swims while settling.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.oceanEnter(
    motion: Boolean,
    rtl: Boolean,
    nav: NavMotionGeometry,
    fromRoute: String?,
    toRoute: String?,
): EnterTransition {
    if (!motion) return EnterTransition.None
    val fromTab = tabOrderIndex(fromRoute)
    val toTab = tabOrderIndex(toRoute)
    if (fromTab >= 0 && toTab >= 0 && fromTab != toTab) {
        val dir = tabDriftDirection(fromTab, toTab, rtl)
        return fadeIn(tween(nav.tabFadeInMs, easing = OceanSwellEasing)) +
            slideInHorizontally(
                tween(nav.tabEnterMs, easing = OceanSwellEasing),
            ) { dir * it / nav.tabEnterHorizontalDivisor } +
            slideInVertically(
                tween(nav.tabEnterMs, easing = OceanSwellEasing),
            ) { it / nav.tabEnterVerticalDivisor } +
            scaleIn(
                tween(nav.tabEnterMs, easing = OceanSwellEasing),
                initialScale = nav.tabEnterScale,
            )
    }
    return fadeIn(tween(nav.pushFadeInMs, easing = OceanSwellEasing)) +
        slideInVertically(
            tween(nav.pushEnterMs, easing = OceanSwellEasing),
        ) { it / nav.pushEnterVerticalDivisor } +
        scaleIn(
            tween(nav.pushEnterMs, easing = OceanSwellEasing),
            initialScale = nav.pushEnterScale,
        )
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.oceanExit(
    motion: Boolean,
    rtl: Boolean,
    nav: NavMotionGeometry,
    fromRoute: String?,
    toRoute: String?,
): ExitTransition {
    if (!motion) return ExitTransition.None
    val fromTab = tabOrderIndex(fromRoute)
    val toTab = tabOrderIndex(toRoute)
    if (fromTab >= 0 && toTab >= 0 && fromTab != toTab) {
        val dir = tabDriftDirection(fromTab, toTab, rtl)
        return fadeOut(tween(nav.tabFadeOutMs)) +
            slideOutHorizontally(
                tween(nav.tabExitMs, easing = OceanSwellEasing),
            ) { -dir * it / nav.tabExitHorizontalDivisor } +
            scaleOut(
                tween(nav.tabExitMs, easing = OceanSwellEasing),
                targetScale = nav.tabExitScale,
            )
    }
    return fadeOut(tween(nav.pushFadeOutMs)) +
        slideOutVertically(
            tween(nav.pushExitMs, easing = OceanSwellEasing),
        ) { -it / nav.pushExitVerticalDivisor } +
        scaleOut(
            tween(nav.pushExitMs, easing = OceanSwellEasing),
            targetScale = nav.pushExitScale,
        )
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.oceanPopEnter(
    motion: Boolean,
    rtl: Boolean,
    nav: NavMotionGeometry,
    fromRoute: String?,
    toRoute: String?,
): EnterTransition {
    if (!motion) return EnterTransition.None
    val fromTab = tabOrderIndex(fromRoute)
    val toTab = tabOrderIndex(toRoute)
    if (fromTab >= 0 && toTab >= 0 && fromTab != toTab) {
        return oceanEnter(motion, rtl, nav, fromRoute, toRoute)
    }
    return fadeIn(tween(nav.popFadeInMs, easing = OceanSwellEasing)) +
        slideInVertically(
            tween(nav.popEnterMs, easing = OceanSwellEasing),
        ) { -it / nav.popEnterVerticalDivisor } +
        scaleIn(
            tween(nav.popEnterMs, easing = OceanSwellEasing),
            initialScale = nav.popEnterScale,
        )
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.oceanPopExit(
    motion: Boolean,
    rtl: Boolean,
    nav: NavMotionGeometry,
    fromRoute: String?,
    toRoute: String?,
): ExitTransition {
    if (!motion) return ExitTransition.None
    val fromTab = tabOrderIndex(fromRoute)
    val toTab = tabOrderIndex(toRoute)
    if (fromTab >= 0 && toTab >= 0 && fromTab != toTab) {
        return oceanExit(motion, rtl, nav, fromRoute, toRoute)
    }
    return fadeOut(tween(nav.popFadeOutMs)) +
        slideOutVertically(
            tween(nav.popExitMs, easing = OceanSwellEasing),
        ) { it / nav.popExitVerticalDivisor } +
        scaleOut(
            tween(nav.popExitMs, easing = OceanSwellEasing),
            targetScale = nav.popExitScale,
        )
}
