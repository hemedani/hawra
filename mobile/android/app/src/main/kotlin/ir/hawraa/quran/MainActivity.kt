package ir.hawraa.quran

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ir.hawraa.quran.core.LocalHapticsEnabled
import ir.hawraa.quran.domain.MotionSetting
import ir.hawraa.quran.ui.HawraaShell
import ir.hawraa.quran.ui.LocalizedApp
import ir.hawraa.quran.ui.theme.HawraaTheme
import ir.hawraa.quran.ui.theme.LocalMotionPreference
import ir.hawraa.quran.ui.theme.MotionPreference

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
            val motionSetting by viewModel.motionSetting.collectAsStateWithLifecycle()
            val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()

            val motion = when (motionSetting) {
                MotionSetting.FULL -> MotionPreference.Full
                MotionSetting.REDUCED -> MotionPreference.Reduced
                MotionSetting.SYSTEM -> MotionPreference.System
            }

            // Both Pejvak canvases are dark (light theme = deep teal #33505F, dark theme = near
            // black #0A0F16), so system bars always keep light icons and a transparent scrim.
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
                    navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
                )
                window.isNavigationBarContrastEnforced = false
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    window.decorView.systemUiVisibility and
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }

            // Language wraps the theme: the localized context must cover everything that
            // resolves a string, including the theme's own resources.
            CompositionLocalProvider(
                LocalMotionPreference provides motion,
                LocalHapticsEnabled provides hapticsEnabled,
            ) {
                LocalizedApp(language = appLanguage) {
                    HawraaTheme(themeMode = themeMode) {
                        Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                            HawraaShell()
                        }
                    }
                }
            }
        }
    }
}
