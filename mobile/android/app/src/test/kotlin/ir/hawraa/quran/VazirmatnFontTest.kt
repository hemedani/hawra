package ir.hawraa.quran

import android.app.Application
import android.content.Context
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import ir.hawraa.quran.ui.theme.Vazirmatn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The UI face is bundled, so a missing `res/font` file is a compile-time `R` reference that only
 * fails when a screen renders. This pins both halves: the family declares four weights, and every
 * declared resource actually resolves through the Android resource loader.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VazirmatnFontTest {

    private val family = Vazirmatn as FontListFontFamily

    @Test
    fun family_declares_four_weights() {
        assertEquals(4, family.fonts.size)
        assertEquals(
            setOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold),
            family.fonts.map { it.weight }.toSet(),
        )
    }

    @Test
    fun every_font_resource_loads() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        family.fonts.forEach { font ->
            val resource = font as ResourceFont
            assertNotNull(
                "R.font resource #${resource.resId} failed to load",
                ResourcesCompat.getFont(context, resource.resId),
            )
        }
    }
}
