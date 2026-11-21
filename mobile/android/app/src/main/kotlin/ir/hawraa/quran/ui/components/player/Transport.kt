package ir.hawraa.quran.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import ir.hawraa.quran.R
import ir.hawraa.quran.core.HapticIntent
import ir.hawraa.quran.core.rememberHaptics
import ir.hawraa.quran.ui.components.BubbleButton
import ir.hawraa.quran.ui.components.ICON_GLYPHS
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalHawraaTokens
import kotlinx.coroutines.delay

/**
 * The transport row (`docs/DESIGN.md` §10.6, D9).
 *
 * Hawraa has no ±30 s skip, no queue and no sleep timer, so the row is prev /
 * play-pause / next. The ±30 bubbles are still expressible — [onRewind] and
 * [onForward] render them only when non-null — but the player passes null.
 * Waiting replaces the play bubble with a pressable [SpinnerRing].
 */
@Composable
fun TransportControls(
    isPlaying: Boolean,
    waiting: Boolean,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    onRewind: (() -> Unit)? = null,
    onForward: (() -> Unit)? = null,
) {
    val geometry = LocalHawraaGeometry.current
    val transport = geometry.transport
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BubbleButton(
            icon = ICON_GLYPHS.getValue("previous"),
            contentDescription = stringResource(R.string.recitation_previous_ayah),
            onClick = onPrevious,
            size = transport.secondarySize,
        )
        if (onRewind != null) {
            BubbleButton(
                icon = ICON_GLYPHS.getValue("replay30"),
                contentDescription = stringResource(R.string.player_back_30),
                onClick = onRewind,
                size = transport.secondarySize,
            )
        }
        if (waiting) {
            SpinnerRing(onClick = onToggle, size = transport.playSize)
        } else {
            BubbleButton(
                icon = ICON_GLYPHS.getValue(if (isPlaying) "pause" else "play"),
                contentDescription = stringResource(
                    if (isPlaying) R.string.recitation_pause else R.string.recitation_play,
                ),
                onClick = onToggle,
                size = transport.playSize,
                filled = true,
            )
        }
        if (onForward != null) {
            BubbleButton(
                icon = ICON_GLYPHS.getValue("forward30"),
                contentDescription = stringResource(R.string.player_forward_30),
                onClick = onForward,
                size = transport.secondarySize,
            )
        }
        BubbleButton(
            icon = ICON_GLYPHS.getValue("next"),
            contentDescription = stringResource(R.string.recitation_next_ayah),
            onClick = onNext,
            size = transport.secondarySize,
        )
    }
}

/** The filled play bubble while a source is being resolved: same size, spinner inside, press kept. */
@Composable
fun SpinnerRing(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = LocalHawraaGeometry.current.transport.playSize,
) {
    val tokens = LocalHawraaTokens.current
    val geometry = LocalHawraaGeometry.current
    val transport = geometry.transport
    val haptics = rememberHaptics()
    val label = stringResource(R.string.player_stop_waiting)
    Box(
        modifier = modifier
            .size(maxOf(size, geometry.controls.lg))
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label) {
                haptics.trigger(HapticIntent.Tap)
                onClick()
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(tokens.accent),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = tokens.onAccent,
                strokeWidth = transport.spinnerStroke,
                modifier = Modifier.size(size * transport.spinnerScale),
            )
        }
    }
}

/**
 * The line under the transport. Hawraa has no Pejvak `PlaybackPhase`, so it takes the
 * two facts the player actually has: whether it is waiting and an optional error.
 */
@Composable
fun PlaybackStatusLine(
    waiting: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
    waitingSince: Long? = null,
) {
    if (!waiting && error == null) return
    val tokens = LocalHawraaTokens.current
    val transport = LocalHawraaGeometry.current.transport
    val start = waitingSince ?: remember(waiting) {
        if (waiting) System.currentTimeMillis() else null
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(waiting, start) {
        while (waiting) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val waitedLong = waiting && start != null && now - start > transport.statusLongWaitMs
    val color = if (error != null) tokens.danger else tokens.textSecondary
    val label = when {
        error != null -> error.ifBlank { stringResource(R.string.player_failed) }
        waitedLong -> stringResource(R.string.player_connecting_long)
        else -> stringResource(R.string.player_connecting)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (waiting && error == null) {
            CircularProgressIndicator(
                color = color,
                strokeWidth = transport.statusSpinnerStroke,
                modifier = Modifier.size(transport.statusSpinnerSize),
            )
            Spacer(Modifier.width(transport.statusSpacing))
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
