package ir.hawraa.quran

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import ir.hawraa.quran.domain.AppLanguage
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.ui.LocalizedApp
import ir.hawraa.quran.ui.components.AmbientCanvas
import ir.hawraa.quran.ui.screens.auth.AuthContent
import ir.hawraa.quran.ui.screens.auth.AuthError
import ir.hawraa.quran.ui.screens.auth.AuthFormState
import ir.hawraa.quran.ui.screens.auth.AuthMode
import ir.hawraa.quran.ui.theme.HawraaTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The gate, rendered on the JVM in both locales and both themes.
 *
 * No dock here on purpose: while the gate is closed there is no navigation, and a screenshot that
 * included one would be a picture of a screen the app never shows. The ambient canvas stays,
 * because the form is a glass pane and glass needs something behind it.
 *
 * The assertions are the part that would survive a redesign: the name and confirm fields exist
 * only on register, and an error is actually rendered rather than only stored.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "fa-rIR-w411dp-h891dp-xxhdpi")
class AuthScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun register_form_in_persian() {
        composeRule.setContent {
            LocalizedApp(AppLanguage.SYSTEM) {
                HawraaTheme(themeMode = ThemeMode.LIGHT) {
                    Frame {
                        AuthContent(
                            form = AuthFormState(
                                mode = AuthMode.REGISTER,
                                email = "syd@example.com",
                                password = "password123",
                                confirmPassword = "password123",
                                displayName = "سید",
                            ),
                            onMode = {},
                            onEmail = {},
                            onPassword = {},
                            onConfirmPassword = {},
                            onDisplayName = {},
                            onSubmit = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("auth-name").assertIsDisplayed()
        composeRule.onNodeWithText("ساخت حساب تازه").assertIsDisplayed()
        capture("auth_register_fa.png")
    }

    @Test
    fun sign_in_showing_a_refused_credential() {
        composeRule.setContent {
            LocalizedApp(AppLanguage.SYSTEM) {
                HawraaTheme(themeMode = ThemeMode.DARK) {
                    Frame {
                        AuthContent(
                            form = AuthFormState(
                                mode = AuthMode.SIGN_IN,
                                email = "syd@example.com",
                                password = "wrongpass",
                                error = AuthError.CREDENTIALS,
                            ),
                            onMode = {},
                            onEmail = {},
                            onPassword = {},
                            onConfirmPassword = {},
                            onDisplayName = {},
                            onSubmit = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("auth-error").assertIsDisplayed()
        composeRule.onNodeWithText("ایمیل یا گذرواژه نادرست است.").assertIsDisplayed()
        capture("auth_signin_error_fa.png")
    }

    @Test
    fun register_form_in_english() {
        composeRule.setContent {
            LocalizedApp(AppLanguage.ENGLISH) {
                HawraaTheme(themeMode = ThemeMode.LIGHT) {
                    Frame {
                        AuthContent(
                            form = AuthFormState(
                                mode = AuthMode.REGISTER,
                                email = "syd@example.com",
                                password = "password123",
                                confirmPassword = "password123",
                                error = AuthError.DUPLICATE,
                            ),
                            onMode = {},
                            onEmail = {},
                            onPassword = {},
                            onConfirmPassword = {},
                            onDisplayName = {},
                            onSubmit = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Create an account").assertIsDisplayed()
        composeRule.onNodeWithText("An account with this email already exists.").assertIsDisplayed()
        capture("auth_register_en.png")
    }

    /**
     * The second password field and its refusal.
     *
     * Worth a picture of its own because it is the one error the *client* can produce — every
     * other failure on this screen comes back from the server — so it is the one that has to be
     * certain the two fields and the strip fit on one screen without the button being pushed out
     * of reach.
     */
    @Test
    fun register_showing_two_passwords_that_disagree() {
        composeRule.setContent {
            LocalizedApp(AppLanguage.SYSTEM) {
                HawraaTheme(themeMode = ThemeMode.LIGHT) {
                    Frame {
                        AuthContent(
                            form = AuthFormState(
                                mode = AuthMode.REGISTER,
                                email = "syd@example.com",
                                password = "password123",
                                confirmPassword = "password124",
                                error = AuthError.PASSWORDS_DO_NOT_MATCH,
                            ),
                            onMode = {},
                            onEmail = {},
                            onPassword = {},
                            onConfirmPassword = {},
                            onDisplayName = {},
                            onSubmit = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("auth-password-confirm").assertIsDisplayed()
        composeRule.onNodeWithTag("auth-submit").assertIsDisplayed()
        composeRule.onNodeWithText("دو گذرواژه یکسان نیستند.").assertIsDisplayed()
        capture("auth_register_mismatch_fa.png")
    }

    /** The app frame without the dock: canvas behind, form in front. */
    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        Box(modifier = Modifier.fillMaxSize()) {
            AmbientCanvas()
            content()
        }
    }
}
