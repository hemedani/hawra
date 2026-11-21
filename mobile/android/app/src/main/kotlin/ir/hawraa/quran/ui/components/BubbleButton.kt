package ir.hawraa.quran.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import ir.hawraa.quran.core.HapticIntent
import ir.hawraa.quran.core.rememberHaptics
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalHawraaTokens

/**
 * A circular glass icon button (DESIGN.md §9.3).
 *
 * Two variants, and the difference is meaning, not decoration:
 *
 *  - **filled** — a solid accent disc with the glyph knocked out. This is *the* primary action of
 *    whatever it sits in, and there is at most one per surface (the play bubble, the current dock
 *    destination).
 *  - **outlined** — a hairline circle with an `icon`-coloured glyph. Everything else.
 *
 * The visible circle can be smaller than the touch target: the outer box always pads out to at
 * least [HawraaGeometry.controls.lg], so shrinking the glyph never makes a control harder to hit.
 * A press fires [HapticIntent.Tap].
 */
@Composable
fun BubbleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = LocalHawraaGeometry.current.controls.lg,
    iconSize: Dp = Dp.Unspecified,
    filled: Boolean = false,
    enabled: Boolean = true,
    /** Shows a spinner in place of the glyph while keeping the press — "stop waiting". */
    busy: Boolean = false,
    tint: Color? = null,
    content: @Composable (() -> Unit)? = null,
    /** Mirrors the glyph in RTL for directional media icons (next/previous). */
    mirrorInRtl: Boolean = false,
) {
    val tokens = LocalHawraaTokens.current
    val geometry = LocalHawraaGeometry.current
    val layoutDirection = LocalLayoutDirection.current
    val glyphSize = if (iconSize != Dp.Unspecified) iconSize else size * 0.46f
    val glyph = when {
        filled -> tint ?: tokens.onAccent
        else -> tint ?: if (enabled) tokens.text else tokens.textTertiary
    }
    val touch = maxOf(size, geometry.controls.lg)
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()

    Box(
        modifier = modifier
            .size(touch)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true, color = tokens.text),
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = {
                    haptics.trigger(HapticIntent.Tap)
                    onClick()
                },
            )
            .semantics { this.contentDescription = contentDescription ?: "" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .then(
                    if (filled) {
                        Modifier.background(if (enabled) tokens.accent else tokens.glassFill)
                    } else {
                        Modifier
                            .background(tokens.glassFill)
                            .border(
                                BorderStroke(geometry.glass.borderWidth, tokens.glassBorder),
                                CircleShape,
                            )
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                busy -> CircularProgressIndicator(
                    modifier = Modifier.size(glyphSize),
                    color = glyph,
                    strokeWidth = geometry.controls.spinnerStroke,
                )

                content != null -> content()

                else -> M3Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = glyph,
                    modifier = Modifier
                        .size(glyphSize)
                        .then(
                            if (mirrorInRtl && layoutDirection == LayoutDirection.Rtl) {
                                Modifier.graphicsLayer { scaleX = -1f }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}
