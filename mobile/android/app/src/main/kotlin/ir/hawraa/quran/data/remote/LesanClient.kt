package ir.hawraa.quran.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Raised when the server answers with success = false.
 *
 * [serverMessage] is the sentence the backend chose (`"email or password is incorrect"`). It is
 * carried rather than flattened into a generic failure because the login form has to say
 * something specific, and guessing which failure happened from a status code is not possible —
 * Lesan reports everything as `success: false`. It is developer-facing English; the UI maps the
 * handful it cares about to localised copy and treats the rest as "something went wrong".
 */
class LesanException(val serverMessage: String?) :
    Exception(serverMessage ?: "the server refused the request")

/**
 * Thin Ktor wrapper over the Lesan wire format. POSTs { model, act, details }
 * to the server root; the JWT goes in the `token` header (no Bearer prefix).
 *
 * Returns [Result] so callers (the sync engine) can classify failures without
 * throwing into the UI. Sync failures must never surface as UI errors.
 */
class LesanClient(
    private val http: HttpClient,
    private val baseUrl: String,
) {
    suspend fun callAct(
        model: String,
        act: String,
        set: JsonObject,
        get: JsonObject? = null,
        token: String? = null,
    ): Result<JsonElement> = runCatching {
        val response = http.post(baseUrl) {
            contentType(ContentType.Application.Json)
            if (token != null) header("token", token)
            setBody(LesanRequest(model, act, LesanDetails(set, get)))
        }
        val envelope = response.body<LesanResponse>()
        if (!envelope.success) {
            throw LesanException(envelope.body.errorMessage())
        }
        envelope.body ?: JsonNull
    }
}

/**
 * Pulls `message` out of an error envelope.
 *
 * A failed Lesan call answers `{ success: false, body: { message } }`, while a successful one puts
 * the payload where the message would be. Reading the field defensively is what lets one exception
 * type serve both, instead of the client inventing a second parse of the same response.
 *
 * `internal` so the extraction can be tested without an HTTP engine: the interesting cases are a
 * body that is not an object, a `message` that is not a string, and an absent body — none of which
 * a real server would send, and all of which a proxy or a version skew can.
 */
internal fun JsonElement?.errorMessage(): String? =
    ((this as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull