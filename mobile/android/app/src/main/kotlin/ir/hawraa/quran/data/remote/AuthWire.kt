package ir.hawraa.quran.data.remote

import ir.hawraa.quran.domain.auth.Account
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire shapes for `user/register`, `user/login` and `user/me`.
 *
 * The three acts answer two shapes: the first two return `{ token, user }`, the third `{ user }`.
 * One [AuthUserDto] serves all three — the server builds them from the same document with
 * different projections, and a DTO per act would be three places to forget a field.
 *
 * Every field has a default so an older client survives a newer server, and `_id` keeps its
 * underscore through [SerialName] rather than renaming the property: the field name is the
 * contract, and a Kotlin-friendly name would mean the mapping lives in two places.
 */
@Serializable
data class AuthUserDto(
    @SerialName("_id") val id: String? = null,
    val email: String? = null,
    val displayName: String? = null,
)

@Serializable
data class AuthSessionDto(
    val token: String? = null,
    val user: AuthUserDto? = null,
)

@Serializable
data class AuthMeDto(
    val user: AuthUserDto? = null,
)

/**
 * A decoded session: the credential plus the account it belongs to.
 *
 * Not a DTO — [user] is nullable on the wire because a projection can omit it, but a session
 * without an account is unusable to this app, so the null is resolved once, in [AuthApi], rather
 * than being re-checked at every call site.
 */
data class AuthSession(
    val token: String,
    val user: AuthUserDto,
)

/**
 * Decoding plus the mapping to [Account].
 *
 * `explicitNulls = false` matters for the *write* side: a reader who registered without a display
 * name must not have `"displayName": null` sent to the server, because `optional(string())`
 * accepts an absent key and rejects an explicit null.
 */
internal object AuthCodec {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    /** Null when the server answered without a usable identity; the caller decides what that means. */
    fun accountOf(dto: AuthUserDto): Account? {
        val id = dto.id?.takeIf { it.isNotBlank() } ?: return null
        val email = dto.email?.takeIf { it.isNotBlank() } ?: return null
        return Account(
            userId = id,
            email = email,
            displayName = dto.displayName?.takeIf { it.isNotBlank() },
        )
    }
}
