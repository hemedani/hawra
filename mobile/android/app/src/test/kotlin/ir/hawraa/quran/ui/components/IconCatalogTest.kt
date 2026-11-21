package ir.hawraa.quran.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the semantic vocabulary screens rely on to real glyphs (DESING.md §7). */
class IconCatalogTest {

    private val required = listOf(
        "play", "pause", "next", "previous", "back", "close", "more", "search",
        "settings", "add", "delete", "check", "download", "history", "speed",
    )

    @Test
    fun requiredVocabularyExists() {
        val missing = required.filter { it !in ICON_GLYPHS }
        assertTrue("missing glyphs: $missing", missing.isEmpty())
    }

    @Test
    fun fallbackGlyphExists() {
        assertTrue("info" in ICON_GLYPHS)
    }
}
