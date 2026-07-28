package dev.jdtech.jellyfin.localcontrol

import dev.jdtech.jellyfin.settings.domain.AppPreferences
import fi.iki.elonen.NanoHTTPD
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * The local control API's transport - a plain loopback HTTP server (127.0.0.1 only, never
 * 0.0.0.0), so `curl`/any HTTP client works directly from Termux with zero extra tooling. This is
 * the third transport tried for this feature, in order:
 * 1. `android.net.LocalServerSocket` (Linux abstract-namespace unix socket) - SELinux blocks
 *    arbitrary app-to-app connections outright on a real device (confirmed: `EACCES` under
 *    enforcing, works under permissive). No app-level fix could work around that.
 * 2. A `ContentProvider`'s `call()` method, invoked via Android's `content call` shell command -
 *    works, but that command's *external*-access path requires
 *    `android.permission.ACCESS_CONTENT_PROVIDERS_EXTERNALLY`, a signature-level permission only
 *    the `shell`/root uid holds (confirmed: `pm grant` refuses it even as root - "not a changeable
 *    permission type"). A real app's own uid (Termux's) can never hold it, so `local` commands
 *    only worked with root.
 * 3. **This one**: loopback TCP is ordinary BSD socket I/O, gated only by the `INTERNET`
 *    permission the app already has - no SELinux domain-separation wall, no signature permission,
 *    no root. [LocalControlAuth]'s bearer token (unchanged since design 2) is the entire auth
 *    boundary, same as before - loopback TCP is reachable by any process on the device, same
 *    caveat as design 2's Binder call, so there was never any caller-identity check to lose here.
 *
 * NanoHTTPD ([fi.iki.elonen.NanoHTTPD]) was already a dependency (`androidTestImplementation`,
 * used by `core/src/androidTest/.../LargeFileHttpServer.kt`) - promoted to a real
 * `implementation` dependency rather than hand-rolling HTTP request parsing.
 */
@Singleton
class LocalControlServer
@Inject
constructor(
    private val router: LocalControlRouter,
    private val auth: LocalControlAuth,
    private val appPreferences: AppPreferences,
) : NanoHTTPD(BIND_ADDRESS, PORT) {

    @Synchronized
    fun startIfEnabled() {
        if (appPreferences.getValue(appPreferences.localControlEnabled)) start()
    }

    /** `true` once actually listening (including if it already was) - `false` if binding failed
     * (e.g. the port is somehow already in use). Lets the Settings toggle stay honest about
     * whether anything is really listening instead of just claiming success. */
    fun startSafely(): Boolean {
        if (isAlive) return true
        return try {
            start(SOCKET_READ_TIMEOUT, false)
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to start local control server")
            false
        }
    }

    fun isRunning(): Boolean = isAlive

    override fun serve(session: IHTTPSession): Response {
        val token = session.headers["authorization"]?.removePrefix("Bearer ")?.trim()
        if (!auth.verifyToken(token)) {
            return jsonResponse(Response.Status.UNAUTHORIZED, errorBody("Invalid or missing token"))
        }

        val bodyText =
            if (session.method == Method.POST || session.method == Method.PUT || session.method == Method.PATCH) {
                val files = HashMap<String, String>()
                try {
                    session.parseBody(files)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse local control request body")
                    return jsonResponse(Response.Status.BAD_REQUEST, errorBody("Malformed request body"))
                }
                files["postData"]
            } else {
                null
            }
        val bodyElement = bodyText?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }

        val response =
            try {
                runBlocking { router.handle(session.method.name, session.uri, bodyElement) }
            } catch (e: Exception) {
                Timber.e(e, "LocalControlServer.serve failed")
                return jsonResponse(Response.Status.INTERNAL_ERROR, errorBody(e.message ?: "Internal error"))
            }

        return jsonResponse(statusFor(response.status), response.body?.toString() ?: "{}")
    }

    private fun jsonResponse(status: Response.IStatus, body: String): Response =
        newFixedLengthResponse(status, "application/json", body)

    /** NanoHTTPD's `Response.Status` is a fixed Java enum, but the debug proxy forwards whatever
     * raw HTTP status the proxied service returned (e.g. Sonarr's own 404) - not necessarily one
     * of the fixed constants. `Response.IStatus` is what [newFixedLengthResponse] actually accepts
     * (`Response.Status` is just its built-in implementation), so an arbitrary code that doesn't
     * match one of ours below is passed through as-is via a minimal custom implementation. */
    private fun statusFor(code: Int): Response.IStatus =
        when (code) {
            LocalControlStatus.OK -> Response.Status.OK
            LocalControlStatus.BAD_REQUEST -> Response.Status.BAD_REQUEST
            LocalControlStatus.UNAUTHORIZED -> Response.Status.UNAUTHORIZED
            LocalControlStatus.NOT_FOUND -> Response.Status.NOT_FOUND
            LocalControlStatus.CONFLICT -> Response.Status.CONFLICT
            LocalControlStatus.INTERNAL_ERROR -> Response.Status.INTERNAL_ERROR
            else -> RawStatus(code)
        }

    private class RawStatus(private val code: Int) : Response.IStatus {
        override fun getRequestStatus(): Int = code

        override fun getDescription(): String = code.toString()
    }

    private fun errorBody(message: String): String = """{"error":"${message.replace("\"", "'")}"}"""

    companion object {
        const val BIND_ADDRESS = "127.0.0.1"
        const val PORT = 48411
        private const val SOCKET_READ_TIMEOUT = 30_000
        private val json = Json { ignoreUnknownKeys = true }
    }
}
