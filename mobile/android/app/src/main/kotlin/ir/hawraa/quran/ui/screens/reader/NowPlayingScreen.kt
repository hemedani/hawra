package ir.hawraa.quran.ui.screens.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.hawraa.quran.ui.recitation.NowPlayingStore
import ir.hawraa.quran.ui.screens.player.PlayerContent

/**
 * The route-level full-screen player.
 *
 * The reader still mounts this while its `playerOpen` flag is set; the shell-owned morphing
 * sheet arrives in the next task. The body is [PlayerContent], so the visual contract lives in
 * one place and the route wrapper only supplies the store's current snapshot.
 */
@Composable
fun NowPlayingScreen(
    store: NowPlayingStore,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by store.state.collectAsStateWithLifecycle()
    ui?.let { snapshot ->
        PlayerContent(
            ui = snapshot,
            store = store,
            onClose = onClose,
            modifier = modifier,
        )
    }
}
