package ir.hawraa.quran.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.hawraa.quran.R
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalHawraaTokens
import ir.hawraa.quran.ui.theme.OceanSwellEasing
import ir.hawraa.quran.ui.theme.Vazirmatn
import ir.hawraa.quran.ui.theme.useMotionEnabled
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * Tracks how many modal sheets are composed so the app shell can blur the content
 * behind them. A sheet renders in its own dialog window, so blurring the nav
 * content frosts the activity window behind the sheet while the sheet itself stays
 * crisp — the glass backdrop.
 */
class SheetPresence {
    var count by mutableStateOf(0)
        internal set
}

val LocalSheetPresence = staticCompositionLocalOf<SheetPresence?> { null }

/**
 * A glass bottom sheet (DESIGN.md §9.1). Wraps Material3's ModalBottomSheet with the
 * Pejvak surface treatment: a floating xl-rounded canvas panel with a white glass
 * stroke and margins on the sides and bottom, plus a glass drag handle. Content
 * composes glass rows/fields. Owns its insets. While composed it raises
 * [LocalSheetPresence] so the shell frosts the content behind it.
 *
 * A titled sheet always carries an [icon] bubble (falling back to a neutral glyph),
 * an optional [subtitle], a close bubble and a hairline wave. The content glides in
 * on the current — a small rise with a sinusoidal lateral weave and a crest that
 * travels across the rule — and ebbs away on dismiss. Reduced motion substitutes the
 * settled state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    icon: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalHawraaTokens.current
    val geo = LocalHawraaGeometry.current
    val presence = LocalSheetPresence.current
    val motion = useMotionEnabled()
    val scope = rememberCoroutineScope()
    DisposableEffect(presence) {
        presence?.let { it.count++ }
        onDispose { presence?.let { it.count-- } }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shape = RoundedCornerShape(geo.radius.xl)
    val swell = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(motion) {
        if (motion) {
            swell.animateTo(1f, tween(durationMillis = 460, easing = OceanSwellEasing))
        } else {
            swell.snapTo(1f)
        }
    }

    // Ebb before leaving: the surface sinks and the crest runs out, then the sheet's
    // owner removes it. Guards against a double tap (close + scrim) firing twice.
    fun requestDismiss() {
        if (closing) return
        closing = true
        if (!motion) {
            onDismiss()
            return
        }
        scope.launch {
            swell.animateTo(0f, tween(durationMillis = 190, easing = OceanSwellEasing))
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { requestDismiss() },
        modifier = modifier.padding(
            start = geo.spacing.sm,
            end = geo.spacing.sm,
            bottom = geo.spacing.sm,
        ),
        sheetState = sheetState,
        containerColor = tokens.canvasBase,
        tonalElevation = 0.dp,
        scrimColor = tokens.scrim,
        shape = shape,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(BorderStroke(geo.glass.borderWidth, tokens.glassBorder), shape)
                .graphicsLayer {
                    // Fish glide: the content rises out of the water with a slow
                    // lateral weave; on exit it reverses back under the surface.
                    val p = swell.value
                    val rest = 1f - p
                    translationY = geo.sheet.enterRise.toPx() * rest
                    translationX = geo.sheet.enterSway.toPx() * sin(p * PI.toFloat() * 1.55f) * rest
                    val s = 1f - geo.sheet.enterScale * rest
                    scaleX = s
                    scaleY = s
                    alpha = 0.42f + 0.58f * p
                }
                .padding(top = geo.spacing.md, bottom = geo.spacing.lg)
                .padding(horizontal = geo.spacing.xxl),
        ) {
            Box(Modifier.fillMaxWidth().padding(bottom = geo.spacing.md), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(width = geo.sheet.handleWidth, height = geo.sheet.handleHeight)
                        .clip(RoundedCornerShape(geo.radius.pill))
                        .background(tokens.glassBorder),
                )
            }
            if (title != null) {
                SheetHeader(
                    title = title,
                    subtitle = subtitle,
                    icon = icon ?: "info",
                    progress = swell.value,
                    onClose = { requestDismiss() },
                )
            }
            content()
        }
    }
}

/** The icon bubble, headline, close bubble and hairline crest that open a sheet. */
@Composable
private fun SheetHeader(
    title: String,
    subtitle: String?,
    icon: String,
    progress: Float,
    onClose: () -> Unit,
) {
    val tokens = LocalHawraaTokens.current
    val geo = LocalHawraaGeometry.current
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(geo.spacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(geo.controls.md)
                    .clip(CircleShape)
                    .background(tokens.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    name = icon,
                    contentDescription = null,
                    tint = tokens.accent,
                    size = geo.icons.md,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Vazirmatn),
                    color = tokens.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Vazirmatn),
                        color = tokens.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            BubbleButton(
                icon = ICON_GLYPHS.getValue("close"),
                contentDescription = stringResource(R.string.action_close),
                onClick = onClose,
                size = geo.controls.sm,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = geo.spacing.md, bottom = geo.spacing.lg),
        ) {
            // The crest's phase travels as the sheet rises, then settles into the
            // same hairline the dock uses once the water is still.
            HairlineWave(
                phase = 0.6f - (1f - progress) * geo.sheet.crestTravel,
            )
            SheetWake(progress = progress)
        }
    }
}

/** The short bright wake a sheet leaves while it enters or leaves, like a fish. */
@Composable
internal fun SheetWake(progress: Float, modifier: Modifier = Modifier) {
    if (progress <= 0.02f || progress >= 0.98f) return
    val tokens = LocalHawraaTokens.current
    val geo = LocalHawraaGeometry.current
    Canvas(modifier.fillMaxWidth().height(geo.wave.wakeHeight)) {
        val fade = sin(progress * PI).toFloat()
        val head = size.width * (-0.15f + 1.3f * progress)
        drawWaveWake(
            headX = head,
            cy = size.height / 2f,
            spread = geo.wave.wakeSpread.toPx(),
            phaseShift = 1.1f,
            amplitude = geo.wave.wakeAmplitude.toPx(),
            phase = progress * geo.wave.wakePhaseSpan,
            color = tokens.accentTeal,
            scale = 1f,
            lengthDp = geo.wave.wakeLength.value,
            wavelength = geo.wave.wakeWavelength.toPx(),
            alphaBase = 0.5f * fade,
            geometry = geo.wave,
        )
    }
}
