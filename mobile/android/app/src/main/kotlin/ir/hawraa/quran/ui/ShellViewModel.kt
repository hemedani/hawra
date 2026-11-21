package ir.hawraa.quran.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.hawraa.quran.data.repository.AuthRepository
import ir.hawraa.quran.domain.auth.AuthState
import ir.hawraa.quran.ui.recitation.NowPlaying
import ir.hawraa.quran.ui.recitation.NowPlayingStore
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

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

    val nowPlaying: StateFlow<NowPlaying?> = store.state

    /** Whether the full-screen player is open; the shell hides the dock while it is true. */
    val playerExpanded: StateFlow<Boolean> = store.expanded

    /** `Restoring` until the cached session is read, so the gate never flashes for a known user. */
    val authState: StateFlow<AuthState> = authRepository.state

    fun togglePlayPause() = store.togglePlayPause()

    fun nextAyah() = store.next()

    /** Tapping the mini-player asks the reader to show its full controls. */
    fun openControls() = store.openControls()
}
