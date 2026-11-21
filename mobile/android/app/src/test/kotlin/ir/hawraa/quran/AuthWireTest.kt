package ir.hawraa.quran

import ir.hawraa.quran.data.remote.AuthCodec
import ir.hawraa.quran.data.remote.AuthMeDto
import ir.hawraa.quran.data.remote.AuthSessionDto
import ir.hawraa.quran.data.remote.AuthUserDto
import ir.hawraa.quran.data.remote.errorMessage
import ir.hawraa.quran.domain.auth.AuthRules
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The account wire contract, pinned to payloads the server actually returned.
 *
 * The JSON below is copied from live `user/register`, `user/login` and `user/me` responses (only
 * the token is truncated). Pinning real payloads rather than inventing tidy ones is the point: a
 * DTO that decodes a shape nobody sends proves nothing, and `_id` is exactly the kind of field a
 * hand-written fixture gets wrong.
 */
class AuthWireTest {

    private val json = AuthCodec.json

    private val registerBody = """
        {"token":"eyJhbGciOiJIUzUxMiJ9.eyJfaWQiOiI2YWY5YjAzMiJ9.c2ln","user":{"_id":"6af9b032bc4d0777a81ac1af","email":"probe1@example.com","displayName":"Probe One"}}
    """.trimIndent()

    @Test
    fun a_register_answer_decodes_to_a_session() {
        val dto = json.decodeFromString(AuthSessionDto.serializer(), registerBody)

        assertEquals("eyJhbGciOiJIUzUxMiJ9.eyJfaWQiOiI2YWY5YjAzMiJ9.c2ln", dto.token)
        assertEquals("6af9b032bc4d0777a81ac1af", dto.user?.id)
        assertEquals("probe1@example.com", dto.user?.email)
        assertEquals("Probe One", dto.user?.displayName)
    }

    @Test
    fun the_underscore_id_maps_through_serial_name() {
        // The field is `_id` on the wire and `id` in Kotlin; a rename that lost the annotation
        // would silently produce a null id and an account that cannot be persisted.
        val dto = json.decodeFromString(AuthUserDto.serializer(), """{"_id":"abc"}""")
        assertEquals("abc", dto.id)
    }

    @Test
    fun a_registration_without_a_display_name_leaves_it_null() {
        val dto = json.decodeFromString(
            AuthSessionDto.serializer(),
            """{"token":"t","user":{"_id":"abc","email":"a@b.co"}}""",
        )
        assertNull(dto.user?.displayName)
    }

    @Test
    fun the_me_answer_wraps_the_user() {
        val dto = json.decodeFromString(
            AuthMeDto.serializer(),
            """{"user":{"_id":"abc","email":"a@b.co","displayName":"A"}}""",
        )
        assertEquals("abc", dto.user?.id)
    }

    @Test
    fun unknown_fields_do_not_break_decoding() {
        // The server may add `role`, `lastLoginAt`, `status` to a projection at any time.
        val dto = json.decodeFromString(
            AuthUserDto.serializer(),
            """{"_id":"abc","email":"a@b.co","role":"user","lastLoginAt":"2026-01-01T00:00:00Z"}""",
        )
        assertEquals("abc", dto.id)
    }

    @Test
    fun an_account_needs_both_an_id_and_an_email() {
        val complete = AuthCodec.accountOf(AuthUserDto("abc", "a@b.co", "A"))
        assertEquals("abc", complete?.userId)
        assertEquals("A", complete?.displayName)

        // A token row with no email is the legacy anonymous registration; it is not an account.
        assertNull(AuthCodec.accountOf(AuthUserDto("abc", null, null)))
        assertNull(AuthCodec.accountOf(AuthUserDto(null, "a@b.co", null)))
        assertNull(AuthCodec.accountOf(AuthUserDto("", "a@b.co", null)))
        assertNull(AuthCodec.accountOf(AuthUserDto("abc", "   ", null)))
    }

    @Test
    fun a_blank_display_name_is_not_a_display_name() {
        val account = AuthCodec.accountOf(AuthUserDto("abc", "a@b.co", "  "))
        assertNull(account?.displayName)
        // …and the greeting falls back to the local part rather than to nothing.
        assertEquals("a", account?.greetingName)
    }

    @Test
    fun the_cached_account_round_trips_through_the_same_shape() {
        val dto = AuthUserDto("abc", "a@b.co", "A")
        val stored = json.encodeToString(AuthUserDto.serializer(), dto)
        val back = json.decodeFromString(AuthUserDto.serializer(), stored)
        assertEquals(dto, back)
    }

    @Test
    fun a_null_display_name_is_omitted_from_the_encoded_form() {
        // `optional(string())` on the server accepts an absent key and rejects an explicit null.
        val encoded = json.encodeToString(
            AuthUserDto.serializer(),
            AuthUserDto("abc", "a@b.co", null),
        )
        assertFalse(encoded.contains("displayName"))
    }

    @Test
    fun the_error_message_survives_the_envelope() {
        val body = Json.parseToJsonElement("""{"message":"email or password is incorrect"}""")
        assertEquals("email or password is incorrect", body.errorMessage())
    }

    @Test
    fun an_error_body_of_the_wrong_shape_yields_null_rather_than_throwing() {
        assertNull(Json.parseToJsonElement("\"just a string\"").errorMessage())
        assertNull(Json.parseToJsonElement("""{"message":{"nested":1}}""").errorMessage())
        assertNull(Json.parseToJsonElement("""{"other":1}""").errorMessage())
        assertNull(JsonNull.errorMessage())
        assertNull((null as JsonElement?).errorMessage())
    }

    @Test
    fun email_shape_matches_what_the_server_accepts() {
        assertTrue(AuthRules.looksLikeEmail("a@b.co"))
        assertTrue(AuthRules.looksLikeEmail("  syd@example.com  "))
        assertFalse(AuthRules.looksLikeEmail(""))
        assertFalse(AuthRules.looksLikeEmail("nope"))
        assertFalse(AuthRules.looksLikeEmail("@b.co"))
        assertFalse(AuthRules.looksLikeEmail("a@"))
        assertFalse(AuthRules.looksLikeEmail("a b@c.co"))
    }

    @Test
    fun the_minimum_password_length_matches_the_backend() {
        // `back/utils/password.ts` — a client that lets 6 characters through only produces a
        // round trip whose answer is already known.
        assertEquals(8, AuthRules.MIN_PASSWORD_LENGTH)
    }
}
