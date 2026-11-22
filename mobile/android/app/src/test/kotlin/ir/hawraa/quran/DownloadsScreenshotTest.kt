package ir.hawraa.quran

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import ir.hawraa.quran.data.local.DownloadBatch
import ir.hawraa.quran.domain.AppLanguage
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.domain.download.DownloadScope
import ir.hawraa.quran.domain.download.EditionGranularity
import ir.hawraa.quran.domain.download.EditionInfo
import ir.hawraa.quran.domain.download.ReciterInfo
import ir.hawraa.quran.ui.LocalizedApp
import ir.hawraa.quran.ui.screens.downloads.DownloadNotice
import ir.hawraa.quran.ui.screens.downloads.DownloadsContent
import ir.hawraa.quran.ui.screens.downloads.DownloadsUiState
import ir.hawraa.quran.ui.screens.downloads.ScopeMode
import ir.hawraa.quran.ui.theme.HawraaTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the downloads screen on the JVM into `app/build/screenshots/`.
 *
 * Two captures, because this screen is the one most likely to break in translation: it puts
 * Persian digits, a byte-size unit and a reciter name in the same line, and it is the only screen
 * with a `Slider` whose label is built from a formatted string.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "fa-rIR-w411dp-h891dp-xxhdpi")
class DownloadsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val reciters = listOf(
        ReciterInfo(
            key = "shakernejad",
            nameFa = "حامد شاکرنژاد",
            nameEn = "Hamed Shakernejad",
            sortOrder = 10,
            editions = listOf(
                edition("shakernejad-tartil-juz-24", "tartil", "juz", 24, "ترتیل — جزءبهجزء", "Tartil — juz by juz"),
            ),
        ),
        ReciterInfo(
            key = "parhizgar",
            nameFa = "شهریار پرهیزگار",
            nameEn = "Shahriar Parhizgar",
            sortOrder = 20,
            editions = listOf(
                edition("parhizgar-murattal-ayah-48", "murattal", "ayah", 48, "ترتیل — آیهبهآیه", "Murattal — verse by verse"),
            ),
        ),
        ReciterInfo(
            key = "minshawi",
            nameFa = "محمد صدیق منشاوی",
            nameEn = "Mohamed Siddiq al-Minshawi",
            sortOrder = 30,
            editions = listOf(
                edition("minshawi-murattal-ayah-128", "murattal", "ayah", 128, "ترتیل — آیهبهآیه", "Murattal — verse by verse"),
                edition("minshawi-murattal-surah-128", "murattal", "surah", 128, "ترتیل — سورهبهسوره", "Murattal — surah by surah"),
            ),
        ),
    )

    private val batches = listOf(
        DownloadBatch(
            scopeKey = "surah-2",
            editionKey = "parhizgar-murattal-ayah-48",
            reciterKey = "parhizgar",
            total = 286,
            done = 286,
            failed = 0,
            bytes = 12L * 1024 * 1024,
        ),
        DownloadBatch(
            scopeKey = "juz-01",
            editionKey = "shakernejad-tartil-juz-24",
            reciterKey = "shakernejad",
            total = 1,
            done = 0,
            failed = 1,
            bytes = 0,
        ),
        DownloadBatch(
            scopeKey = "surah-18",
            editionKey = "minshawi-murattal-ayah-128",
            reciterKey = "minshawi",
            total = 110,
            done = 74,
            failed = 0,
            bytes = 9L * 1024 * 1024,
        ),
    )

    /**
     * A hand-built state, with the scope fields the view model would have resolved.
     *
     * Those fields are computed by [ir.hawraa.quran.ui.screens.downloads.DownloadsViewModel] from
     * the bundled mushaf, which a screenshot test has no reason to load — but they are what the
     * summary line and the size estimate read, so leaving them at zero would render a screen with
     * a disabled button and "0 ayahs", which is not the screen under test.
     */
    private fun state(mode: ScopeMode = ScopeMode.PAGE, notice: DownloadNotice? = null): DownloadsUiState {
        val base = DownloadsUiState(
            loading = false,
            reciters = reciters,
            selectedReciterKey = "minshawi",
            selectedEditionKey = "minshawi-murattal-ayah-128",
            mode = mode,
            surah = 2,
            ayah = 255,
            juz = 1,
            fromPage = 1,
            toPage = 5,
            pageCount = 604,
            batches = batches,
            totalBytes = 21L * 1024 * 1024,
            pendingFiles = 36,
            notice = notice,
        )
        return when (mode) {
            ScopeMode.PAGE -> base.copy(
                scope = DownloadScope.Range(1, 1, 2, 29),
                scopeAyahs = 36,
                scopePages = 1 to 5,
            )

            ScopeMode.SURAH -> base.copy(scope = DownloadScope.Surah(2), scopeAyahs = 286)
            ScopeMode.JUZ -> base.copy(scope = DownloadScope.Juz(1), scopeAyahs = 148)
            ScopeMode.AYAH -> base.copy(scope = DownloadScope.Ayah(2, 255), scopeAyahs = 1)
        }
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun stage(
        language: AppLanguage,
        theme: ThemeMode,
        name: String,
        notice: DownloadNotice? = null,
    ) {
        composeRule.setContent {
            LocalizedApp(language) {
                HawraaTheme(themeMode = theme) {
                    TestShell(selected = "settings") {
                        DownloadsContent(state = state(notice = notice), onBack = {})
                    }
                }
            }
        }
        capture(name)
    }

    @Test
    fun downloads_light_fa() = stage(
        language = AppLanguage.PERSIAN,
        theme = ThemeMode.LIGHT,
        name = "downloads-light-fa.png",
    )

    @Test
    fun downloads_dark_fa() = stage(
        language = AppLanguage.PERSIAN,
        theme = ThemeMode.DARK,
        name = "downloads-dark-fa.png",
        notice = DownloadNotice.Queued(files = 286, alreadyHave = 0),
    )

    @Test
    fun downloads_light_en() = stage(
        language = AppLanguage.ENGLISH,
        theme = ThemeMode.LIGHT,
        name = "downloads-light-en.png",
    )

    @Test
    fun downloads_dark_en() = stage(
        language = AppLanguage.ENGLISH,
        theme = ThemeMode.DARK,
        name = "downloads-dark-en.png",
        notice = DownloadNotice.Queued(files = 286, alreadyHave = 0),
    )

    /**
     * A juz-granularity edition offers exactly one scope.
     *
     * The assertion that matters is the *absence* of the other three: offering "surah" for a
     * reciter who publishes only whole juzes would promise a precision that does not exist, and a
     * disabled chip would be a worse answer than no chip.
     */
    @Test
    fun a_juz_only_edition_offers_only_the_juz_scope() {
        val juzOnly = state(mode = ScopeMode.JUZ).copy(
            selectedReciterKey = "shakernejad",
            selectedEditionKey = "shakernejad-tartil-juz-24",
        )
        composeRule.setContent {
            LocalizedApp(AppLanguage.PERSIAN) {
                HawraaTheme(themeMode = ThemeMode.LIGHT) {
                    TestShell(selected = "settings") {
                        DownloadsContent(state = juzOnly, onBack = {})
                    }
                }
            }
        }

        composeRule.onNodeWithTag("download_mode_juz").assertIsDisplayed()
        composeRule.onAllNodesWithTag("download_mode_page").assertCountEquals(0)
        composeRule.onAllNodesWithTag("download_mode_surah").assertCountEquals(0)
        composeRule.onAllNodesWithTag("download_mode_ayah").assertCountEquals(0)
        composeRule.onNodeWithTag("download_granularity_note").assertIsDisplayed()
        capture("downloads-juz-only-fa.png")
    }

    /** The saved list sits below the fold; scroll to it so the progress rows can be inspected. */
    @Test
    fun downloads_saved_list() {
        composeRule.setContent {
            LocalizedApp(AppLanguage.PERSIAN) {
                HawraaTheme(themeMode = ThemeMode.LIGHT) {
                    TestShell(selected = "settings") {
                        DownloadsContent(state = state(mode = ScopeMode.JUZ), onBack = {})
                    }
                }
            }
        }
        composeRule.onNodeWithTag("download_group_parhizgar-murattal-ayah-48").performScrollTo()
        capture("downloads-scope-juz.png")
    }
}

/** A verified edition as the server would describe it. */
private fun edition(
    key: String,
    style: String,
    granularity: String,
    bitrate: Int,
    labelFa: String,
    labelEn: String,
) = EditionInfo(
    key = key,
    reciterKey = key.substringBefore('-'),
    labelFa = labelFa,
    labelEn = labelEn,
    style = style,
    granularity = EditionGranularity.fromWire(granularity),
    bitrateKbps = bitrate,
    totalFiles = if (granularity == "ayah") 6236 else 30,
    availability = EditionInfo.VERIFIED,
    sourceName = "test source",
    verifiedAt = "2026-11-15",
)
