package ir.hawraa.quran.data.remote

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The three account acts, and nothing else.
 *
 * An interface, not a class, for the same reason [ir.hawraa.quran.data.download.FileFetcher] is
 * one: [ir.hawraa.quran.data.repository.AuthRepository] has rules worth testing — what a *network
 * failure* means versus what a *refusal* means — and those rules can only be tested by controlling
 * the answer. Depending on a concrete HTTP client would make the repository's tests need a server.
 *
 * No persistence and no state: the repository owns both.
 */
interface AuthApi {
    suspend fun register(
        email: String,
        password: String,
        displayName: String?,
    ): Result<AuthSession>

    suspend fun login(email: String, password: String): Result<AuthSession>

    /**
     * "Is this token still good, and whose is it?"
     *
     * The one call at launch that may sign a reader out — but only by *answering*. A network
     * failure comes back as a plain exception, and the repository reads that distinction.
     */
    suspend fun me(token: String): Result<AuthUserDto>
}

/**
 * [AuthApi] over Lesan.
 *
 * Every call asks for an explicit projection. Lesan falls back to the model's `Pure` projection
 * when `get` is absent, and the app renders exactly three fields; asking for them is the
 * difference between a response the app understands and a response it hopes to understand.
 */
@Singleton
class LesanAuthApi @Inject constructor(
    private val client: LesanClient,
) : AuthApi {

    override suspend fun register(
        email: String,
        password: String,
        displayName: String?,
    ): Result<AuthSession> = session(
        act = "register",
        set = buildJsonObject {
            put("email", email)
            put("password", password)
            if (displayName != null) put("displayName", displayName)
        },
    )

    override suspend fun login(email: String, password: String): Result<AuthSession> = session(
        act = "login",
        set = buildJsonObject {
            put("email", email)
            put("password", password)
        },
    )

    override suspend fun me(token: String): Result<AuthUserDto> = client.callAct(
        model = "user",
        act = "me",
        set = buildJsonObject { },
        get = buildJsonObject { put("user", USER_FIELDS) },
        token = token,
    ).mapCatching { body ->
        AuthCodec.json.decodeFromJsonElement(AuthMeDto.serializer(), body).user
            ?: error("the server answered without a user")
    }

    private suspend fun session(act: String, set: JsonObject): Result<AuthSession> =
        client.callAct(
            model = "user",
            act = act,
            set = set,
            get = buildJsonObject {
                put("token", 1)
                put("user", USER_FIELDS)
            },
        ).mapCatching { body ->
            val dto = AuthCodec.json.decodeFromJsonElement(AuthSessionDto.serializer(), body)
            AuthSession(
                token = dto.token?.takeIf { it.isNotBlank() }
                    ?: error("the server answered without a token"),
                user = dto.user ?: error("the server answered without a user"),
            )
        }

    private companion object {
        /** The only fields the app renders. A new one is a change here, not a wildcard. */
        val USER_FIELDS: JsonObject = buildJsonObject {
            put("_id", 1)
            put("email", 1)
            put("displayName", 1)
        }
    }
}
