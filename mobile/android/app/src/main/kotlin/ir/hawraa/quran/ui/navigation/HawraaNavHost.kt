package ir.hawraa.quran.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.hawraa.quran.ui.screens.downloads.DownloadsScreen
import ir.hawraa.quran.ui.screens.history.HistoryScreen
import ir.hawraa.quran.ui.screens.home.HomeScreen
import ir.hawraa.quran.ui.screens.presets.PresetEditorScreen
import ir.hawraa.quran.ui.screens.presets.PresetsScreen
import ir.hawraa.quran.ui.screens.reader.ReaderScreen
import ir.hawraa.quran.ui.screens.settings.SettingsScreen

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
    NavHost(navController = navController, startDestination = Routes.HOME) {
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