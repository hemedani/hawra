package ir.hawraa.quran

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import ir.hawraa.quran.data.remote.AuthApi
import ir.hawraa.quran.data.remote.AuthSession
import ir.hawraa.quran.data.remote.AuthUserDto
import ir.hawraa.quran.data.remote.LesanException
import ir.hawraa.quran.data.repository.AuthRepository
import ir.hawraa.quran.data.repository.IdentityRepository
import ir.hawraa.quran.domain.auth.AuthState
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The launch state machine — the part of the gate that is easy to get subtly wrong.
 *
 * The rule under test is the one the app depends on: **only a server answer may sign someone out.**
 * Every other failure is "we could not ask", and signing out on it would eject a reader from a
 * working app because they opened it without a connection.
 *
 * The token store is a real DataStore (a temp file per test) rather than a fake: `restore` writes
 * and reads two keys in a specific order, and a fake would let a wrong order pass.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AuthRepositoryTest {

    private lateinit var identity: IdentityRepository
    private lateinit var api: FakeAuthApi
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ) {
            File(context.cacheDir, "auth-${System.nanoTime()}.preferences_pb")
        }
        identity = IdentityRepository(dataStore)
        api = FakeAuthApi()
        repository = AuthRepository(api, identity)
    }

    // ---- the gate opens only for a real answer ------------------------------------------------

    @Test
    fun a_fresh_device_is_signed_out_and_asks_nobody() = runBlocking {
        repository.restore()

        assertEquals(AuthState.SignedOut, repository.state.value)
        assertEquals(0, api.meCalls)
    }

    @Test
    fun a_cached_session_opens_the_app_while_offline() = runBlocking {
        api.loginAnswer = Result.success(authSession("token-1", "user-1", "syd@example.com"))
        repository.signIn("syd@example.com", "password123")

        api.meAnswer = Result.failure(IOException("no route to host"))
        repository.restore()

        val state = repository.state.value
        assertTrue("a network failure must not close the gate", state is AuthState.SignedIn)
        assertEquals("syd@example.com", (state as AuthState.SignedIn).account.email)
        assertEquals("token-1", repository.token())
    }

    @Test
    fun a_refusal_from_the_server_signs_out_and_forgets_the_token() = runBlocking {
        identity.storeSession("token-1", "user-1")
        api.loginAnswer = Result.success(authSession("token-1", "user-1", "syd@example.com"))
        repository.signIn("syd@example.com", "password123")

        api.meAnswer = Result.failure(LesanException("Invalid or expired token"))
        repository.restore()

        assertEquals(AuthState.SignedOut, repository.state.value)
        assertNull(identity.token())
        assertNull("a signed-out device must not hand out a token", repository.token())
    }

    @Test
    fun a_token_without_a_cached_account_asks_the_server_who_it_is() = runBlocking {
        identity.storeSession("token-1", null)
        api.meAnswer = Result.success(AuthUserDto("user-1", "syd@example.com", "Syd"))

        repository.restore()

        val state = repository.state.value
        assertTrue(state is AuthState.SignedIn)
        assertEquals("Syd", (state as AuthState.SignedIn).account.displayName)
    }

    @Test
    fun a_token_without_a_cached_account_and_no_network_cannot_open_the_gate() = runBlocking {
        // The legacy anonymous registration, or a torn write. There is no account to show, and
        // the reader can sign in again with their server history intact.
        identity.storeSession("token-1", null)
        api.meAnswer = Result.failure(IOException("offline"))

        repository.restore()

        assertEquals(AuthState.SignedOut, repository.state.value)
    }

    @Test
    fun a_token_whose_row_has_no_email_is_not_an_account() = runBlocking {
        identity.storeSession("token-1", null)
        api.meAnswer = Result.success(AuthUserDto("user-1", null, null))

        repository.restore()

        assertEquals(AuthState.SignedOut, repository.state.value)
    }

    // ---- signing in ---------------------------------------------------------------------------

    @Test
    fun signing_in_stores_the_session_and_the_account() = runBlocking {
        api.loginAnswer = Result.success(authSession("token-9", "user-9", "a@b.co", "Ada"))

        val result = repository.signIn("  a@b.co  ", "password123")

        assertEquals("a@b.co", result.getOrThrow().email)
        assertEquals("token-9", identity.token())
        assertEquals("user-9", identity.userId())
        assertEquals("a@b.co", api.lastLoginEmail)
        assertEquals("Ada", (repository.state.value as AuthState.SignedIn).account.displayName)
    }

    @Test
    fun a_rejected_sign_in_leaves_the_gate_closed_and_carries_the_sentence() = runBlocking {
        // The real sequence: the gate resolves first (no token → signed out), then a reader tries.
        repository.restore()
        api.loginAnswer = Result.failure(LesanException("email or password is incorrect"))

        val result = repository.signIn("a@b.co", "wrong")

        assertTrue(result.isFailure)
        assertEquals(
            "email or password is incorrect",
            (result.exceptionOrNull() as LesanException).serverMessage,
        )
        assertEquals(AuthState.SignedOut, repository.state.value)
        assertNull(identity.token())
    }

    @Test
    fun signing_up_sends_a_trimmed_email_and_no_blank_name() = runBlocking {
        api.registerAnswer = Result.success(authSession("token-2", "user-2", "a@b.co"))

        repository.signUp("  a@b.co ", "password123", "   ")

        assertEquals("a@b.co", api.lastRegisterEmail)
        // Absent, not empty: `optional(string())` accepts an absent key and rejects a blank one.
        assertNull(api.lastRegisterName)
    }

    @Test
    fun signing_up_keeps_a_real_name() = runBlocking {
        api.registerAnswer = Result.success(authSession("token-2", "user-2", "a@b.co", "Ada"))

        repository.signUp("a@b.co", "password123", " Ada ")

        assertEquals("Ada", api.lastRegisterName)
    }

    @Test
    fun an_answer_without_a_token_is_a_failure_not_a_session() = runBlocking {
        api.loginAnswer = Result.failure(IllegalStateException("the server answered without a token"))

        val result = repository.signIn("a@b.co", "password123")

        assertTrue(result.isFailure)
        assertNull(identity.token())
    }

    // ---- the token is gated on the state ------------------------------------------------------

    @Test
    fun no_token_is_handed_out_before_the_gate_is_resolved() = runBlocking {
        identity.storeSession("token-1", "user-1")
        // Deliberately no restore(): the state is still Restoring.
        assertNull(repository.token())
    }

    // ---- a slow launch check must not undo a newer decision -----------------------------------

    @Test
    fun a_slow_launch_check_does_not_undo_a_fresh_sign_in() = runBlocking {
        identity.storeSession("old-token", null)
        val gate = CompletableDeferred<Result<AuthUserDto>>()
        api.meGate = gate

        val restoring = launch(Dispatchers.Unconfined) { repository.restore() }
        // `restore` suspends on the DataStore read before it reaches the network, so the test has
        // to wait for the call to actually be in flight rather than assume it.
        api.meStarted.await()
        assertEquals(1, api.meCalls)

        api.loginAnswer = Result.success(authSession("new-token", "user-2", "new@b.co"))
        repository.signIn("new@b.co", "password123")

        // The old token's answer arrives late and says the session is dead.
        gate.complete(Result.failure(LesanException("Invalid or expired token")))
        restoring.join()

        val state = repository.state.value
        assertTrue("a stale answer must not sign out a newer session", state is AuthState.SignedIn)
        assertEquals("new@b.co", (state as AuthState.SignedIn).account.email)
        assertEquals("new-token", identity.token())
        assertFalse(repository.token().isNullOrBlank())
    }
}
