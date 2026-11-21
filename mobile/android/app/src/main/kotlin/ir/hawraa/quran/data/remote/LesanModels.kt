package ir.hawraa.quran.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Lesan wire format. Every call is POST { model, act, details: { set, get } }.
 * The JWT travels in the `token` header (no Bearer prefix). Responses are
 * enveloped as { success, body }.
 */
@Serializable
data class LesanRequest(
    val model: String,
    val act: String,
    val details: LesanDetails,
)

@Serializable
data class LesanDetails(
    val set: JsonObject,
    val get: JsonObject? = null,
)

@Serializable
data class LesanResponse(
    val success: Boolean,
    val body: JsonElement? = null,
)