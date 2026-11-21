package ir.hawraa.quran

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import ir.hawraa.quran.data.remote.LesanException
import ir.hawraa.quran.data.repository.AuthRepository
import ir.hawraa.quran.data.repository.IdentityRepository
import ir.hawraa.quran.domain.auth.AuthState
import ir.hawraa.quran.ui.screens.auth.AuthError
import ir.hawraa.quran.ui.screens.auth.AuthMode
import ir.hawraa.quran.ui.screens.auth.AuthViewModel
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
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
 * The form's rules, and the translation from a server sentence to something a reader can act on.
 *
 * The mapping in [AuthViewModel] is the one piece of this feature that can rot without anything
 * failing to compile: it matches English fragments the backend is free to reword. These tests are
 * what will notice.
 *
 * `Dispatchers.Main` is replaced with an unconfined test dispatcher so `viewModelScope` runs on the
 * calling thread; the repository still touches a real DataStore on `Dispatchers.IO`, which no test
 * scheduler can advance, so [settle] waits for the form to stop being busy instead of pretending
 * the work is instantaneous.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AuthViewModelTest {

    private lateinit var api: FakeAuthApi
    private lateinit var repository: AuthRepository
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ) {
            File(context.cacheDir, "authvm-${System.nanoTime()}.preferences_pb")
        }
        api = FakeAuthApi()
        repository = AuthRepository(api, IdentityRepository(dataStore))
        viewModel = AuthViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun a_malformed_email_never_reaches_the_server() = runBlocking {
        viewModel.setEmail("not-an-email")
        viewModel.setPassword("password123")

        viewModel.submit()
        settle()

        assertEquals(AuthError.EMAIL_INVALID, viewModel.form.value.error)
        assertEquals(0, api.loginCalls + api.registerCalls)
    }

    @Test
    fun a_short_password_on_register_is_caught_locally() = runBlocking {
        viewModel.setMode(AuthMode.REGISTER)
        viewModel.setEmail("a@b.co")
        viewModel.setPassword("short")

        viewModel.submit()
        settle()

        assertEquals(AuthError.PASSWORD_TOO_SHORT, viewModel.form.value.error)
        assertEquals(0, api.registerCalls)
    }

    @Test
    fun the_same_short_password_is_allowed_on_sign_in() = runBlocking {
        // The rule belongs to registration. A password set before the rule existed must still be
        // able to sign in — refusing it here would lock the reader out of their own account.
        viewModel.setEmail("a@b.co")
        viewModel.setPassword("short")
        api.loginAnswer = Result.success(authSession("t", "u", "a@b.co"))

        viewModel.submit()
        settle()

        assertNull(viewModel.form.value.error)
        assertEquals(1, api.loginCalls)
    }

    @Test
    fun the_email_is_trimmed_before_it_is_sent() = runBlocking {
        viewModel.setEmail("  a@b.co  ")
        viewModel.setPassword("password123")
        api.loginAnswer = Result.success(authSession("t", "u", "a@b.co"))

        viewModel.submit()
        settle()

        assertEquals("a@b.co", api.lastLoginEmail)
    }

    @Test
    fun a_refused_credential_becomes_the_credentials_message() = runBlocking {
        api.loginAnswer = Result.failure(LesanException("email or password is incorrect"))

        submitValid()
        settle()

        assertEquals(AuthError.CREDENTIALS, viewModel.form.value.error)
        assertFalse("the spinner must stop on failure", viewModel.form.value.busy)
    }

    @Test
    fun a_duplicate_registration_becomes_the_duplicate_message() = runBlocking {
        viewModel.setMode(AuthMode.REGISTER)
        api.registerAnswer =
            Result.failure(LesanException("an account with this email already exists"))

        submitValid()
        settle()

        assertEquals(AuthError.DUPLICATE, viewModel.form.value.error)
    }

    @Test
    fun a_short_password_the_server_rejects_becomes_the_length_message() = runBlocking {
        // Only reachable if the two rules ever disagree, which is exactly why it is mapped.
        api.loginAnswer =
            Result.failure(LesanException("the password must be at least 8 characters"))

        submitValid()
        settle()

        assertEquals(AuthError.PASSWORD_TOO_SHORT, viewModel.form.value.error)
    }

    @Test
    fun an_unreachable_server_is_not_a_credential_problem() = runBlocking {
        api.loginAnswer = Result.failure(IOException("failed to connect"))

        submitValid()
        settle()

        assertEquals(AuthError.NETWORK, viewModel.form.value.error)
    }

    @Test
    fun an_unrecognised_server_sentence_falls_back_to_the_generic_message() = runBlocking {
        api.loginAnswer = Result.failure(LesanException("this account is suspended"))

        submitValid()
        settle()

        assertEquals(AuthError.GENERIC, viewModel.form.value.error)
    }

    @Test
    fun a_successful_sign_in_closes_the_gate_and_clears_the_error() = runBlocking {
        api.loginAnswer = Result.success(authSession("token-1", "user-1", "a@b.co", "Ada"))

        submitValid()
        settle()

        assertNull(viewModel.form.value.error)
        assertTrue(viewModel.authState.value is AuthState.SignedIn)
        assertEquals("Ada", (viewModel.authState.value as AuthState.SignedIn).account.displayName)
        assertFalse("the form must not be left spinning", viewModel.form.value.busy)
    }

    @Test
    fun switching_the_verb_drops_the_previous_error() = runBlocking {
        viewModel.setEmail("nope")
        viewModel.setPassword("password123")
        viewModel.submit()
        settle()
        assertEquals(AuthError.EMAIL_INVALID, viewModel.form.value.error)

        viewModel.setMode(AuthMode.REGISTER)

        assertNull(viewModel.form.value.error)
    }

    @Test
    fun editing_a_field_drops_the_error_that_was_about_it() = runBlocking {
        viewModel.setEmail("nope")
        viewModel.setPassword("password123")
        viewModel.submit()
        settle()

        viewModel.setEmail("nope@b.co")

        assertNull(viewModel.form.value.error)
    }

    @Test
    fun a_mismatched_confirmation_never_reaches_the_server() = runBlocking {
        viewModel.setMode(AuthMode.REGISTER)
        viewModel.setEmail("a@b.co")
        viewModel.setPassword("password123")
        viewModel.setConfirmPassword("password124")

        viewModel.submit()
        settle()

        assertEquals(AuthError.PASSWORDS_DO_NOT_MATCH, viewModel.form.value.error)
        assertEquals("a mismatch is knowable locally", 0, api.registerCalls)
    }

    @Test
    fun a_matching_confirmation_registers() = runBlocking {
        viewModel.setMode(AuthMode.REGISTER)
        api.registerAnswer = Result.success(authSession("t", "u", "a@b.co"))

        submitValid()
        settle()

        assertNull(viewModel.form.value.error)
        assertEquals(1, api.registerCalls)
    }

    @Test
    fun a_stale_confirmation_cannot_block_a_sign_in() = runBlocking {
        // The confirm field only exists on register, so its value can outlive the tab it was typed
        // in. Sign-in has nothing to compare against and must not start refusing to submit.
        viewModel.setPassword("password123")
        viewModel.setConfirmPassword("something-else")
        api.loginAnswer = Result.success(authSession("t", "u", "a@b.co"))

        submitValid()
        settle()

        assertNull(viewModel.form.value.error)
        assertEquals(1, api.loginCalls)
    }

    @Test
    fun an_empty_form_cannot_be_submitted() {
        assertFalse(viewModel.form.value.canSubmit)
        // An email without a password is still not submittable.
        assertFalse(viewModel.form.value.copy(email = "a@b.co").canSubmit)
        assertTrue(viewModel.form.value.copy(email = "a@b.co", password = "x").canSubmit)
    }

    /**
     * A form filled in correctly, in whichever verb is showing.
     *
     * The confirmation is always filled with the same value: sign-in ignores it, and a register
     * test that left it blank would be testing the mismatch rule by accident.
     */
    private fun submitValid() {
        viewModel.setEmail("a@b.co")
        viewModel.setPassword("password123")
        viewModel.setConfirmPassword("password123")
        viewModel.submit()
    }

    /**
     * Waits for the submitted form to stop being busy.
     *
     * `submit` sets `busy` before it launches, so a false reading after it returns means the work
     * either never started (a validation failure) or has already finished — both are settled
     * states, and neither can be confused with "still running".
     */
    private suspend fun settle() {
        withTimeout(5_000) {
            while (viewModel.form.value.busy) delay(5)
        }
    }
}
