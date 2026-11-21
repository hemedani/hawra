package ir.hawraa.quran.data.repository

import ir.hawraa.quran.data.remote.AuthApi
import ir.hawraa.quran.data.remote.AuthCodec
import ir.hawraa.quran.data.remote.AuthSession
import ir.hawraa.quran.data.remote.AuthUserDto
import ir.hawraa.quran.data.remote.LesanException
import ir.hawraa.quran.domain.auth.Account
import ir.hawraa.quran.domain.auth.AuthState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Who is signed in, and the one place that answers it.
 *
 * The app is offline-first, and that rule applies here too: **the cache is authoritative for
 * drawing, the server is authoritative only for invalidating.** A stored token is enough to open
 * the app; it is not enough to keep it open, because an account can be deleted or suspended
 * between launches. So the launch sequence is
 *
 *   1. read the cached session and publish it immediately (a disk read, not a round trip);
 *   2. ask `user/me` in the background;
 *   3. sign out **only** if the server actually answered.
 *
 * Step 3 is the subtle one. [ir.hawraa.quran.data.remote.LesanClient] wraps every call in
 * `runCatching`, so a `LesanException` means *the server replied and refused* while anything else
 * (no route, DNS, timeout) means *we could not ask*. Treating the second as a refusal would sign a
 * reader out of a working app because they opened it in a lift.
 *
 * The token itself stays in [IdentityRepository], which every sync path already reads. This class
 * only decides whether a token may be *used*: [token] returns null unless the state is
 * [AuthState.SignedIn], so a signed-out device can never push history under a stale credential.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: AuthApi,
    private val identity: IdentityRepository,
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Restoring)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** Guards the state transition and the writes that must agree with it. */
    private val mutex = Mutex()

    /**
     * Resolves the launch state. Safe to call more than once; the second call costs one request.
     *
     * Deliberately **not** wrapped in the mutex around the network call: holding a lock across a
     * round trip would make the login button unresponsive for as long as the launch check takes,
     * and the check is exactly the call most likely to be slow on a bad connection.
     */
    suspend fun restore() {
        val token = identity.token()
        if (token == null) {
            mutex.withLock { publishSignedOutLocked() }
            return
        }

        val cached = readAccount()
        if (cached != null) {
            // Publish before validating: a returning reader sees their library, not a spinner.
            _state.value = AuthState.SignedIn(cached)
        }

        val answer = api.me(token)

        mutex.withLock {
            // Somebody signed in (or out) while the check was in flight; that decision is newer
            // than this answer, so this answer loses.
            if (identity.token() != token) return@withLock

            answer.fold(
                onSuccess = { dto ->
                    val account = AuthCodec.accountOf(dto)
                    if (account == null) {
                        // A token whose row has no email is the legacy anonymous device
                        // registration from before accounts existed. It is not an account, so
                        // the gate stays closed and the reader registers properly.
                        publishSignedOutLocked()
                    } else {
                        writeAccount(dto)
                        _state.value = AuthState.SignedIn(account)
                    }
                },
                onFailure = { error ->
                    if (error is LesanException) {
                        // The server answered: the token is dead, the account is gone, or it was
                        // suspended. All three mean "not signed in".
                        publishSignedOutLocked()
                    } else if (cached == null) {
                        // Unreachable server *and* nothing cached: there is no account to show.
                        // Signing out is the only honest state — and the reader can sign in
                        // again once there is a connection, with their server history intact.
                        publishSignedOutLocked()
                    }
                    // Otherwise: keep the cached session. Offline is not a credential problem.
                },
            )
        }
    }

    /** Signs an existing account in. Failures carry the server's own sentence for the form. */
    suspend fun signIn(email: String, password: String): Result<Account> =
        adopt(api.login(email = email.trim(), password = password))

    /** Creates an account and signs it in. [displayName] is optional by contract. */
    suspend fun signUp(
        email: String,
        password: String,
        displayName: String?,
    ): Result<Account> = adopt(
        api.register(
            email = email.trim(),
            password = password,
            displayName = displayName?.trim()?.takeIf { it.isNotEmpty() },
        ),
    )

    /**
     * The credential to send, or null when this device must not send one.
     *
     * Gated on the state rather than on the stored value so that "signed out" and "no usable
     * token" cannot drift apart: a sync that runs in the window after the server rejects a token
     * gets null and does nothing, instead of pushing rows under a credential the server has
     * already refused.
     */
    suspend fun token(): String? =
        if (_state.value is AuthState.SignedIn) identity.token() else null

    /**
     * Turns a session response into state, or hands the failure back untouched.
     *
     * Both acts land here so a registered reader and a returning one cannot end up in different
     * states — the same class of bug the backend's `sessionResponse` exists to prevent.
     */
    private suspend fun adopt(result: Result<AuthSession>): Result<Account> {
        val session = result.getOrElse { return Result.failure(it) }
        val account = AuthCodec.accountOf(session.user)
            ?: return Result.failure(IllegalStateException("the server answered without an account"))

        mutex.withLock {
            identity.storeSession(session.token, account.userId)
            writeAccount(session.user)
            _state.value = AuthState.SignedIn(account)
        }
        return Result.success(account)
    }

    /**
     * Clears everything that says "signed in", in the order that keeps [token] honest.
     *
     * The identity is cleared **first**: [token] reads the state and then the store, so removing
     * the credential before flipping the state means the worst case is a null token, never a live
     * one handed out after a sign-out.
     */
    private suspend fun publishSignedOutLocked() {
        identity.clearSession()
        _state.value = AuthState.SignedOut
    }

    private suspend fun readAccount(): Account? {
        val stored = identity.account() ?: return null
        val dto = runCatching {
            AuthCodec.json.decodeFromString(AuthUserDto.serializer(), stored)
        }.getOrNull() ?: return null
        return AuthCodec.accountOf(dto)
    }

    private suspend fun writeAccount(dto: AuthUserDto) {
        identity.storeAccount(AuthCodec.json.encodeToString(AuthUserDto.serializer(), dto))
    }
}
