package dev.pschmitt.jellyfin.localcontrol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Result of a [LocalControlRouter] call - [LocalControlServer] maps this onto an HTTP response
 * (the JSON body verbatim, `status` as the real HTTP status code). */
@Serializable
data class LocalControlResponse(val status: Int, val body: JsonElement? = null)

object LocalControlStatus {
    const val OK = 200
    const val BAD_REQUEST = 400
    const val UNAUTHORIZED = 401
    const val NOT_FOUND = 404
    const val CONFLICT = 409
    const val INTERNAL_ERROR = 500
}
