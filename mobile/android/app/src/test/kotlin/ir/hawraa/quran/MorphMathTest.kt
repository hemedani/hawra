package ir.hawraa.quran

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.hawraa.quran.ui.screens.player.lerpRect
import ir.hawraa.quran.ui.screens.player.morphAlpha
import ir.hawraa.quran.ui.screens.player.morphContentAlpha
import ir.hawraa.quran.ui.screens.player.morphContentScale
import ir.hawraa.quran.ui.screens.player.morphCorner
import ir.hawraa.quran.ui.screens.player.morphMiniRowAlpha
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure math behind the mini-player ⇄ full-screen morph. These curves are what make the
 * transition read as one surface unfolding; pinning them here means a later refactor of the sheet
 * cannot silently change the timing (or the corner) without a failing test.
 */
class MorphMathTest {

    private val mini = 28.dp
    private val epsilon = 0.0001f

    private fun assertDp(expected: Float, actual: Dp) =
        assertEquals(expected, actual.value, epsilon)

    private fun assertRect(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, epsilon)
        assertEquals(expected.top, actual.top, epsilon)
        assertEquals(expected.right, actual.right, epsilon)
        assertEquals(expected.bottom, actual.bottom, epsilon)
    }

    @Test
    fun corner_is_the_capsule_when_collapsed() {
        assertDp(mini.value, morphCorner(mini, 0f))
    }

    @Test
    fun corner_is_square_when_expanded() {
        assertDp(0f, morphCorner(mini, 1f))
    }

    @Test
    fun corner_releases_monotonically_between_the_ends() {
        val collapsed = morphCorner(mini, 0f).value
        val mid = morphCorner(mini, 0.5f).value
        val expanded = morphCorner(mini, 1f).value

        assertTrue(mid < collapsed)
        assertTrue(mid > expanded)
        assertTrue(mid in 0f..mini.value)

        // The exponent is 1.6: half-way through, the capsule has released ~67% of its radius.
        assertDp(mini.value * (1f - 0.5f.pow(1.6f)), morphCorner(mini, 0.5f))
    }

    @Test
    fun corner_clamps_progress_outside_the_morph() {
        assertDp(mini.value, morphCorner(mini, -0.4f))
        assertDp(0f, morphCorner(mini, 1.7f))
    }

    @Test
    fun content_is_invisible_at_the_fade_start() {
        assertEquals(0f, morphContentAlpha(0.34f), epsilon)
    }

    @Test
    fun content_is_fully_opaque_at_the_fade_end() {
        assertEquals(1f, morphContentAlpha(0.90f), epsilon)
    }

    @Test
    fun content_fade_is_linear_between_its_thresholds() {
        assertEquals(0.5f, morphContentAlpha(0.62f), epsilon)
        assertEquals(0f, morphContentAlpha(0f), epsilon)
        assertEquals(1f, morphContentAlpha(1f), epsilon)
    }

    @Test
    fun mini_row_is_visible_on_the_dock() {
        assertEquals(1f, morphMiniRowAlpha(0f), epsilon)
    }

    @Test
    fun mini_row_is_gone_by_the_mid_morph() {
        assertEquals(0f, morphMiniRowAlpha(0.52f), epsilon)
        assertEquals(0.5f, morphMiniRowAlpha(0.32f), epsilon)
        assertEquals(0f, morphMiniRowAlpha(1f), epsilon)
    }

    @Test
    fun content_scale_grows_from_its_collapsed_value() {
        assertEquals(0.84f, morphContentScale(0f), epsilon)
        assertEquals(0.92f, morphContentScale(0.5f), epsilon)
        assertEquals(1f, morphContentScale(1f), epsilon)
    }

    @Test
    fun lerp_rect_hits_both_ends_and_the_midpoint() {
        val from = Rect(10f, 20f, 110f, 220f)
        val to = Rect(0f, 0f, 200f, 400f)

        assertRect(from, lerpRect(from, to, 0f))
        assertRect(to, lerpRect(from, to, 1f))
        assertRect(Rect(5f, 10f, 155f, 310f), lerpRect(from, to, 0.5f))
    }

    @Test
    fun lerp_rect_interpolates_each_edge_independently_for_reversed_rects() {
        val from = Rect(200f, 400f, 100f, 200f)
        val to = Rect(0f, 0f, 50f, 50f)

        assertRect(from, lerpRect(from, to, 0f))
        assertRect(to, lerpRect(from, to, 1f))
        assertRect(Rect(100f, 200f, 75f, 125f), lerpRect(from, to, 0.5f))

        // A rect whose edges cross while it morphs must not be reordered: each edge follows its
        // own straight line, even when left passes right.
        val crossing = lerpRect(Rect(0f, 0f, 100f, 100f), Rect(100f, 100f, 0f, 0f), 0.5f)
        assertRect(Rect(50f, 50f, 50f, 50f), crossing)
    }

    @Test
    fun alpha_clamps_outside_its_window() {
        assertEquals(0f, morphAlpha(-1f, 0.2f, 0.8f), epsilon)
        assertEquals(0f, morphAlpha(0.1f, 0.2f, 0.8f), epsilon)
        assertEquals(0.5f, morphAlpha(0.5f, 0.2f, 0.8f), epsilon)
        assertEquals(1f, morphAlpha(0.9f, 0.2f, 0.8f), epsilon)
        assertEquals(1f, morphAlpha(2f, 0.2f, 0.8f), epsilon)
    }

    @Test
    fun alpha_is_a_step_when_the_window_is_empty_or_inverted() {
        assertEquals(0f, morphAlpha(0.59f, 0.6f, 0.6f), epsilon)
        assertEquals(1f, morphAlpha(0.6f, 0.6f, 0.6f), epsilon)
        assertEquals(0f, morphAlpha(0.1f, 0.8f, 0.2f), epsilon)
        assertEquals(1f, morphAlpha(0.8f, 0.8f, 0.2f), epsilon)
    }
}
