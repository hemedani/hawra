package ir.hawraa.quran.domain.auth

/**
 * The signed-in reader.
 *
 * Deliberately tiny: the app renders a name and never a credential. `email` is the login handle
 * and doubles as the fallback greeting, because registration does not require a display name —
 * and a screen that greets you with an empty string is worse than one that greets you with your
 * address.
 *
 * [userId] is the Mongo `_id` string. It is kept because it is the only value that ties this
 * device to the server's row, so it is what a future "sign out and wipe" or "account switch"
 * needs in order to know whether the local history belongs to the reader who is here now.
 */
data class Account(
    val userId: String,
    val email: String,
    val displayName: String? = null,
) {
    /** What the UI calls the reader: their name if they gave one, otherwise the local part. */
    val greetingName: String
        get() = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: email.substringBefore('@')
}

/**
 * Where the app is in the sign-in lifecycle.
 *
 * [Restoring] exists so the gate does not flash. Reading the cached session is fast but it is
 * still a disk read, and a returning reader who sees the login form for 40 ms and then their
 * library has been told, wrongly, that they were signed out. The shell draws the ambient canvas
 * and nothing else until this resolves.
 */
sealed interface AuthState {
    data object Restoring : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val account: Account) : AuthState
}

/**
 * The rules the client is allowed to enforce before spending a round trip.
 *
 * These **mirror the server** (`back/utils/password.ts`), they do not replace it: the server
 * re-checks everything, because a client that can be bypassed is not a check. They exist so an
 * offline reader typing an eight-character password is told so immediately instead of watching a
 * spinner time out.
 */
object AuthRules {
    const val MIN_PASSWORD_LENGTH = 8

    /**
     * Loose on purpose — one `@` with something on each side.
     *
     * The strict RFC grammar rejects addresses that work, and the only authority on whether an
     * address is real is a message sent to it. The server applies the same loose test, so the
     * client and the server agree on what "obviously wrong" means.
     */
    fun looksLikeEmail(value: String): Boolean {
        val trimmed = value.trim()
        val at = trimmed.indexOf('@')
        return at > 0 && at < trimmed.lastIndex && !trimmed.contains(' ')
    }
}
