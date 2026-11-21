package ir.hawraa.quran

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import java.util.Collections
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The real app — Hilt graph, Room, DataStore, navigation, `LocalizedApp` — launches into the gate in
 * the language it was configured for, and nothing thrown off the main thread kills the process.
 *
 * This replaces the old "switch to Persian from Settings" test, which the gate made unreachable:
 * Settings now lives behind a session, and a session cannot be faked without either a server or a
 * Hilt test binding, neither of which a JVM test has. What the old test actually guarded was the
 * launch crash — `hiltViewModel()` throwing once `LocalContext` became a bare configuration context
 * — and that crash surface is the gate itself: it is the first `hiltViewModel()` screen under
 * [ir.hawraa.quran.ui.LocalizedApp]. Re-translation on a *live* switch is covered by
 * `LocalizedAppTest`, which drives the same recomposition without needing a signed-in app.
 *
 * The device locale is pinned per test rather than persisted, so the launch is deterministic and
 * needs no server: with no session, the gate is the only thing that can appear.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HawraaApp::class, qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", sdk = [34])
class AuthGateLaunchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun the_gate_opens_in_english_and_the_app_does_not_crash() {
        val errors = collectUncaught()
        try {
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Welcome back").assertIsDisplayed()
            composeRule.onNodeWithTag("auth-email").assertIsDisplayed()
            // No dock: there is nowhere to go until the gate is answered.
            assertTrue(
                "the gate must not show navigation",
                composeRule.onAllNodesWithText("History").fetchSemanticsNodes().isEmpty(),
            )
        } finally {
            restoreHandler()
        }
        assertTrue("the app threw: ${errors.firstOrNull()}", errors.isEmpty())
    }

    @Test
    @Config(qualifiers = "fa-rIR-w411dp-h891dp-xxhdpi")
    fun the_gate_opens_in_persian_and_the_app_does_not_crash() {
        val errors = collectUncaught()
        try {
            composeRule.waitForIdle()
            composeRule.onNodeWithText("خوش آمدید").assertIsDisplayed()
            composeRule.onNodeWithText("ثبت‌نام").assertIsDisplayed()
        } finally {
            restoreHandler()
        }
        assertTrue("the app threw: ${errors.firstOrNull()}", errors.isEmpty())
    }

    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    /**
     * Catches what would close the app a moment after it opens.
     *
     * Startup work runs on background coroutines; without this the test would pass while the
     * process was already dying — which is exactly how the original launch bug hid.
     */
    private fun collectUncaught(): List<Throwable> {
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> errors += error }
        return errors
    }

    private fun restoreHandler() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }
}
