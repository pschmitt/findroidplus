package dev.jdtech.jellyfin.localcontrol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire format for the local control socket (see [LocalControlServer]) - one JSON object per line,
 * both directions. Deliberately not real HTTP: this is a handful of RPCs over a single
 * [android.net.LocalSocket] connection per call, so a hand-rolled envelope is simpler than pulling
 * in an HTTP server for it. [auth] carries the paired-client bearer token (absent only for
 * [LocalControlMessageType.PAIR_REQUEST]); [body] is a free-form JSON payload, shape depends on
 * [path].
 */
@Serializable
data class LocalControlRequest(
    val type: String = LocalControlMessageType.REQUEST,
    val method: String = "GET",
    val path: String = "",
    val auth: String? = null,
    val clientId: String? = null,
    val body: JsonElement? = null,
)

@Serializable
data class LocalControlResponse(
    val status: Int,
    val body: JsonElement? = null,
)

object LocalControlMessageType {
    const val REQUEST = "request"
    const val PAIR_REQUEST = "pair_request"
}

object LocalControlStatus {
    const val OK = 200
    const val BAD_REQUEST = 400
    const val UNAUTHORIZED = 401
    const val NOT_FOUND = 404
    const val CONFLICT = 409
    const val INTERNAL_ERROR = 500
}
