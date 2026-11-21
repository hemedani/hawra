package ir.hawraa.quran

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import ir.hawraa.quran.domain.AppLanguage
import ir.hawraa.quran.ui.LocalizedApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The per-app language switcher has to actually change resolved strings and layout
 * direction, not just persist a preference. The device is pinned to Persian, so an
 * English assertion can only pass if [LocalizedApp] really overrode the locale.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "fa-rIR-w411dp-h891dp-xxhdpi")
class LocalizedAppTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun Sample() {
        Text(stringResource(R.string.history_title))
        Text(stringResource(R.string.banner_juz, "1"))
        Text(
            text = "direction:" + LocalLayoutDirection.current.name,
        )
    }

    @Test
    fun device_default_is_persian() {
        composeRule.setContent { LocalizedApp(AppLanguage.SYSTEM) { Sample() } }
        composeRule.onNodeWithText("تاریخچه").assertIsDisplayed()
        composeRule.onNodeWithText("direction:Rtl").assertIsDisplayed()
    }

    @Test
    fun english_override_translates_and_flips_layout() {
        composeRule.setContent { LocalizedApp(AppLanguage.ENGLISH) { Sample() } }
        composeRule.onNodeWithText("History").assertIsDisplayed()
        composeRule.onNodeWithText("Juz 1").assertIsDisplayed()
        composeRule.onNodeWithText("direction:Ltr").assertIsDisplayed()
    }

    @Test
    fun persian_override_keeps_persian_and_rtl() {
        composeRule.setContent { LocalizedApp(AppLanguage.PERSIAN) { Sample() } }
        composeRule.onNodeWithText("تاریخچه").assertIsDisplayed()
        composeRule.onNodeWithText("direction:Rtl").assertIsDisplayed()
    }

    /**
     * The same recomposition the app performs when the reader picks a language in
     * Settings: the provided locale changes and the tree must re-resolve, not restart.
     */
    @Test
    fun switching_at_runtime_retranslates() {
        val language = mutableStateOf(AppLanguage.SYSTEM)
        composeRule.setContent { LocalizedApp(language.value) { Sample() } }
        composeRule.onNodeWithText("تاریخچه").assertIsDisplayed()

        composeRule.runOnIdle { language.value = AppLanguage.ENGLISH }

        composeRule.onNodeWithText("History").assertIsDisplayed()
        composeRule.onNodeWithText("direction:Ltr").assertIsDisplayed()
    }

    @Test
    fun language_choice_maps_to_the_expected_direction() {
        assert(LayoutDirection.Rtl.name == "Rtl")
        assert(AppLanguage.PERSIAN.isRtl == true)
        assert(AppLanguage.ENGLISH.isRtl == false)
        assert(AppLanguage.SYSTEM.isRtl == null)
        assert(AppLanguage.fromKey("fa") == AppLanguage.PERSIAN)
        assert(AppLanguage.fromKey("nonsense") == AppLanguage.DEFAULT)
    }
}
