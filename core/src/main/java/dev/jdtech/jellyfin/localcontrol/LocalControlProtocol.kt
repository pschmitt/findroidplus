package dev.jdtech.jellyfin.localcontrol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Result of a [LocalControlRouter] call - [LocalControlProvider] maps this onto a `call()`
 * result `Bundle` (status as an int extra, body as a base64-encoded JSON string extra, since
 * `Bundle`/the `content call` shell tool don't handle arbitrary JSON text safely on their own). */
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
