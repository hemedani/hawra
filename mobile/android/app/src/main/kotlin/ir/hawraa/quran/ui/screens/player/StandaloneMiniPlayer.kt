package ir.hawraa.quran.ui.screens.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ir.hawraa.quran.ui.components.FrostedSurface
import ir.hawraa.quran.ui.components.MiniPlayerRow
import ir.hawraa.quran.ui.components.NowPlayingUi
import ir.hawraa.quran.ui.components.dockElevation
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import kotlinx.coroutines.flow.StateFlow

/**
 * The mini-player on pushed screens, where the four-tab dock is not shown.
 *
 * Same heavy-frost material, pill-shaped instead of the dock's rounded panel, and the
 * same [MiniPlayerRow] inside — one row, so the two can never drift apart.
 */
@Composable
fun StandaloneMiniPlayer(
    nowPlaying: NowPlayingUi?,
    miniProgress: StateFlow<Float>? = null,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (nowPlaying == null) return
    val geometry = LocalHawraaGeometry.current
    FrostedSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(geometry.radius.pill),
        softElevation = dockElevation(),
    ) {
        MiniPlayerRow(
            nowPlaying = nowPlaying,
            miniProgress = miniProgress,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onExpand = onExpand,
            modifier = Modifier.height(geometry.dock.playerHeight),
        )
    }
}
