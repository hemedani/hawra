package ir.hawraa.quran.ui

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import ir.hawraa.quran.domain.AppLanguage
import java.util.Locale

/**
 * Applies [language] to the whole Compose tree.
 *
 * The app has no appcompat dependency, so this does the job the framework's per-app
 * language backport would: derive a context whose resources carry the chosen locale,
 * and provide it together with the matching configuration and layout direction. Every
 * `stringResource` below this point then reads the chosen locale, and Persian gets RTL
 * layout for free.
 *
 * Two rules this must not break:
 *
 *  - **The base context has to stay the Activity.** `hiltViewModel()` walks the
 *    `baseContext` chain to find one and throws `IllegalStateException` when it finds a
 *    bare `ContextImpl` instead — which is exactly what `createConfigurationContext`
 *    returns, and it made the app die on every launch once Persian was persisted. A
 *    [ContextThemeWrapper] keeps the Activity as its base and applies the locale as an
 *    override on top, so resources are translated *and* the chain still resolves.
 *  - **The process default locale is left alone.** Mutating it from composition changed
 *    how unrelated code formats numbers (`"%013d".format(...)` produced Persian digits
 *    in a primary key). The locale belongs to the resources, not to the JVM.
 *
 * [AppLanguage.SYSTEM] provides nothing at all, so the device decides.
 */
@Composable
fun LocalizedApp(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    val tag = language.tag
    if (tag == null) {
        content()
        return
    }

    val base = LocalContext.current
    val baseConfiguration = LocalConfiguration.current

    val localized = remember(base, baseConfiguration, tag) {
        val locale = Locale.forLanguageTag(tag)
        val override = Configuration(baseConfiguration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        ContextThemeWrapper(base, 0).apply { applyOverrideConfiguration(override) }
    }

    val direction = if (language.isRtl == true) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        LocalLayoutDirection provides direction,
        content = content,
    )
}

/**
 * The locale the UI is currently shown in, for formatting that does not go through
 * `stringResource` (dates, numbers). Falls back to the device locale when the app
 * follows the system.
 */
fun Configuration.appLocale(): Locale = locales[0] ?: Locale.getDefault()
