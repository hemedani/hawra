package ir.hawraa.quran

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ir.hawraa.quran.ui.HawraaShell
import ir.hawraa.quran.ui.LocalizedApp
import ir.hawraa.quran.ui.theme.HawraaTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
            // Language wraps the theme: the localized context must cover everything that
            // resolves a string, including the theme's own resources.
            LocalizedApp(language = appLanguage) {
                HawraaTheme(themeMode = themeMode) {
                    HawraaShell()
                }
            }
        }
    }
}
