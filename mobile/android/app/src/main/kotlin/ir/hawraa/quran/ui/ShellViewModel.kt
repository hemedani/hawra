package ir.hawraa.quran.ui

import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.hawraa.quran.data.repository.AuthRepository
import ir.hawraa.quran.domain.auth.AuthState
import ir.hawraa.quran.ui.recitation.NowPlayingStore
import ir.hawraa.quran.ui.recitation.PlayerUi
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * The shell's only job is to hand the dock what it draws, forward what it presses, and say whether
 * the gate is open.
 *
 * It holds no state of its own: the player half is published by the reader, the transport commands
 * are bound by the reader, and the session belongs to [AuthRepository]. This is a thin, disposable
 * adapter — not a second owner of playback, and not a second owner of identity.
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val store: NowPlayingStore,
    authRepository: AuthRepository,
) : ViewModel() {

    val player: StateFlow<PlayerUi?> = store.state

    /** Whether the full-screen player is open; the shell hides the dock while it is true. */
    val playerExpanded: StateFlow<Boolean> = store.expanded

    /** The mini-player's measured rect, published by the dock and consumed by the player morph. */
    val miniPlayerRect: StateFlow<Rect?> = store.miniPlayerRect

    /**
     * The dock's wave, separated from [player] so a once-a-second position tick never recomposes
     * the title row: only the wave leaf collects this flow.
     *
     * The position is a pull (the engine exposes no reactive clock), so this samples the store's
     * provider while subscribed. `WhileSubscribed` stops it as soon as the wave leaves composition.
     */
    val miniProgress: StateFlow<Float> = flow {
        while (true) {
            val (positionMs, durationMs) = store.positionProvider()
            emit(
                if (durationMs > 0) {
                    (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                },
            )
            delay(PROGRESS_TICK_MS)
        }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0f)

    /** `Restoring` until the cached session is read, so the gate never flashes for a known user. */
    val authState: StateFlow<AuthState> = authRepository.state

    fun togglePlayPause() = store.toggle()

    fun nextAyah() = store.next()

    /** Tapping the mini-player asks the reader to show its full controls. */
    fun openControls() = store.openControls()

    /** The dock publishes its measured player half here so the sheet can morph out of it. */
    fun onMiniPlayerRect(rect: Rect) = store.setMiniPlayerRect(rect)

    private companion object {
        const val PROGRESS_TICK_MS = 1_000L
    }
}
