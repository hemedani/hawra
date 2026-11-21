package ir.hawraa.quran.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.hawraa.quran.data.remote.LesanException
import ir.hawraa.quran.data.repository.AuthRepository
import ir.hawraa.quran.domain.auth.AuthRules
import ir.hawraa.quran.domain.auth.AuthState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which half of the form is showing. One screen, two verbs — see [AuthScreen]. */
enum class AuthMode { SIGN_IN, REGISTER }

/**
 * What the form has to say, as a closed set.
 *
 * The view model does not produce strings: it produces *reasons*, and the screen turns a reason
 * into a localised sentence. That is what lets the same failure read correctly in Persian and in
 * English without the view model knowing which one is on screen.
 */
enum class AuthError {
    EMAIL_INVALID,
    PASSWORD_TOO_SHORT,
    /** Register only: the two password fields disagree. */
    PASSWORDS_DO_NOT_MATCH,
    CREDENTIALS,
    DUPLICATE,
    NETWORK,
    GENERIC,
}

data class AuthFormState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    /**
     * The second password field, register only.
     *
     * Kept in the form rather than in the composable because it is *validated*, and validation
     * lives next to the code that spends the round trip. A field the view model cannot see would
     * have to be checked in the UI, which is where the "press submit and nothing happens" bug
     * lives.
     */
    val confirmPassword: String = "",
    val displayName: String = "",
    val busy: Boolean = false,
    val error: AuthError? = null,
) {
    /** The two passwords agree. Only meaningful while registering. */
    val passwordsMatch: Boolean get() = password == confirmPassword

    /**
     * Whether the primary action may be pressed.
     *
     * Deliberately loose — "not empty" rather than "valid". A disabled button that never explains
     * itself is the worst of both worlds: the reader cannot tell whether the form is incomplete or
     * the app is broken. The real checks run on press, where there is room to say what is wrong.
     */
    val canSubmit: Boolean
        get() = !busy && email.isNotBlank() && password.isNotEmpty()
}

/**
 * The gate's view model.
 *
 * It owns the form and nothing else: the session itself lives in [AuthRepository], whose state
 * this class only *observes*. That split is why signing in makes the shell swap screens without
 * the form having to navigate anywhere — the state changes, and the shell is already listening.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = auth.state

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    fun setMode(mode: AuthMode) = _form.update {
        // The error belongs to the verb that produced it: carrying "this email is taken" over to
        // the sign-in tab would be a lie.
        it.copy(mode = mode, error = null)
    }

    fun setEmail(value: String) = _form.update { it.copy(email = value, error = null) }

    fun setPassword(value: String) = _form.update { it.copy(password = value, error = null) }

    fun setConfirmPassword(value: String) = _form.update {
        it.copy(confirmPassword = value, error = null)
    }

    fun setDisplayName(value: String) = _form.update { it.copy(displayName = value) }

    /**
     * Submits whichever verb is showing.
     *
     * Validation happens here rather than in the composable so that a configuration change cannot
     * lose a half-typed attempt, and so the rules sit next to the code that spends the round trip.
     */
    fun submit() {
        val form = _form.value
        if (form.busy) return

        val email = form.email.trim()
        if (!AuthRules.looksLikeEmail(email)) {
            _form.update { it.copy(error = AuthError.EMAIL_INVALID) }
            return
        }
        if (form.mode == AuthMode.REGISTER && form.password.length < AuthRules.MIN_PASSWORD_LENGTH) {
            _form.update { it.copy(error = AuthError.PASSWORD_TOO_SHORT) }
            return
        }
        // Checked before the round trip on purpose: a server that accepted the password would
        // still leave the reader locked out on their next attempt, and a mismatch is knowable
        // locally. Sign-in has no second field — there is nothing to compare against.
        if (form.mode == AuthMode.REGISTER && !form.passwordsMatch) {
            _form.update { it.copy(error = AuthError.PASSWORDS_DO_NOT_MATCH) }
            return
        }

        _form.update { it.copy(busy = true, error = null) }

        viewModelScope.launch {
            val result = when (form.mode) {
                AuthMode.SIGN_IN -> auth.signIn(email, form.password)
                AuthMode.REGISTER -> auth.signUp(email, form.password, form.displayName)
            }
            // Success needs no navigation: the repository's state flips to SignedIn and the shell
            // replaces this screen. `busy` is still cleared, because a screen that is replaced one
            // frame later must not be left showing a spinner if it is composed again.
            _form.update { it.copy(busy = false, error = result.exceptionOrNull()?.let(::errorOf)) }
        }
    }
}

/**
 * Maps a failure to something the reader can act on.
 *
 * A [LesanException] means the server answered and refused, and its sentence is the only
 * description of *why* that exists — so the messages are matched by their distinctive fragments.
 * That is brittle by nature: it breaks silently if the backend rewords a message. The durable fix
 * is a machine-readable `code` alongside `message` in Lesan's error envelope; until the backend
 * emits one, matching the sentence is the only option that does not guess.
 *
 * Anything that is not a [LesanException] never reached the server: no route, DNS, timeout. That
 * is a network failure and must say so, because "wrong password" would send the reader to reset a
 * password that was never the problem.
 */
private fun errorOf(error: Throwable): AuthError {
    if (error !is LesanException) return AuthError.NETWORK
    val message = error.serverMessage?.lowercase() ?: return AuthError.GENERIC
    return when {
        "already exists" in message -> AuthError.DUPLICATE
        "incorrect" in message -> AuthError.CREDENTIALS
        "at least" in message && "character" in message -> AuthError.PASSWORD_TOO_SHORT
        "look valid" in message -> AuthError.EMAIL_INVALID
        else -> AuthError.GENERIC
    }
}
