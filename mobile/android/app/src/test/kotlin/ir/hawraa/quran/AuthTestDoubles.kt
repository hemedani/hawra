package ir.hawraa.quran

import ir.hawraa.quran.data.remote.AuthApi
import ir.hawraa.quran.data.remote.AuthSession
import ir.hawraa.quran.data.remote.AuthUserDto
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred

/**
 * The account acts, answering whatever the test needs.
 *
 * Shared rather than nested in one test class because two suites need it for different reasons:
 * [AuthRepositoryTest] controls *when* the launch check answers, and [AuthViewModelTest] controls
 * *what* the answer is. Two copies would drift the moment one of them gained a field.
 */
internal class FakeAuthApi : AuthApi {
    var meAnswer: Result<AuthUserDto> = Result.failure(IOException("not configured"))
    var loginAnswer: Result<AuthSession> = Result.failure(IOException("not configured"))
    var registerAnswer: Result<AuthSession> = Result.failure(IOException("not configured"))

    /** When set, `me` waits for it instead of answering immediately. */
    var meGate: CompletableDeferred<Result<AuthUserDto>>? = null

    /** Completes as soon as `me` is entered, so a test can wait for the call to be in flight. */
    val meStarted = CompletableDeferred<Unit>()

    var meCalls = 0
    var loginCalls = 0
    var registerCalls = 0
    var lastLoginEmail: String? = null
    var lastLoginPassword: String? = null
    var lastRegisterEmail: String? = null
    var lastRegisterPassword: String? = null
    var lastRegisterName: String? = null

    override suspend fun register(
        email: String,
        password: String,
        displayName: String?,
    ): Result<AuthSession> {
        registerCalls++
        lastRegisterEmail = email
        lastRegisterPassword = password
        lastRegisterName = displayName
        return registerAnswer
    }

    override suspend fun login(email: String, password: String): Result<AuthSession> {
        loginCalls++
        lastLoginEmail = email
        lastLoginPassword = password
        return loginAnswer
    }

    override suspend fun me(token: String): Result<AuthUserDto> {
        meCalls++
        meStarted.complete(Unit)
        return meGate?.await() ?: meAnswer
    }
}

internal fun authSession(
    token: String,
    id: String,
    email: String,
    name: String? = null,
): AuthSession = AuthSession(token, AuthUserDto(id, email, name))
