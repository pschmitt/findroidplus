package dev.pschmitt.jellyfin.localcontrol

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * The local control API's transport - a plain loopback HTTP server (127.0.0.1 only, never 0.0.0.0),
 * so `curl`/any HTTP client works directly from Termux with zero extra tooling. This is the third
 * transport tried for this feature, in order:
 * 1. `android.net.LocalServerSocket` (Linux abstract-namespace unix socket) - SELinux blocks
 *    arbitrary app-to-app connections outright on a real device (confirmed: `EACCES` under
 *    enforcing, works under permissive). No app-level fix could work around that.
 * 2. A `ContentProvider`'s `call()` method, invoked via Android's `content call` shell command -
 *    works, but that command's *external*-access path requires
 *    `android.permission.ACCESS_CONTENT_PROVIDERS_EXTERNALLY`, a signature-level permission only
 *    the `shell`/root uid holds (confirmed: `pm grant` refuses it even as root - "not a changeable
 *    permission type"). A real app's own uid (Termux's) can never hold it, so `local` commands only
 *    worked with root.
 * 3. **This one**: loopback TCP is ordinary BSD socket I/O, gated only by the `INTERNET` permission
 *    the app already has - no SELinux domain-separation wall, no signature permission, no root.
 *    [LocalControlAuth]'s bearer token (unchanged since design 2) is the entire auth boundary, same
 *    as before - loopback TCP is reachable by any process on the device, same caveat as design 2's
 *    Binder call, so there was never any caller-identity check to lose here.
 *
 * NanoHTTPD ([fi.iki.elonen.NanoHTTPD]) was already a dependency (`androidTestImplementation`, used
 * by `core/src/androidTest/.../LargeFileHttpServer.kt`) - promoted to a real `implementation`
 * dependency rather than hand-rolling HTTP request parsing.
 */
@Singleton
class LocalControlServer
@Inject
constructor(
    private val router: LocalControlRouter,
    private val auth: LocalControlAuth,
    private val appPreferences: AppPreferences,
    @ApplicationContext private val context: Context,
) : NanoHTTPD(BIND_ADDRESS, portFor(context.packageName)) {

    /** The port this instance actually bound to - see [portFor]. */
    val port: Int = portFor(context.packageName)

    @Synchronized
    fun startIfEnabled() {
        if (appPreferences.getValue(appPreferences.localControlEnabled)) start()
    }

    /**
     * `true` once actually listening (including if it already was) - `false` if binding failed
     * (e.g. the port is somehow already in use). Lets the Settings toggle stay honest about whether
     * anything is really listening instead of just claiming success.
     */
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
        // The one unauthenticated route: `findroid-cli` itself is a public script, not user data
        // (same script anyone could get from the repo), so gating it behind the bearer token would
        // only block the bootstrap step of getting the token-consuming client onto the device in
        // the first place - checked before the token check below, same as a health-check route
        // would be.
        if (session.method == Method.GET && session.uri == CLI_PATH) {
            return serveCliScript()
        }

        val token = session.headers["authorization"]?.removePrefix("Bearer ")?.trim()
        if (!auth.verifyToken(token)) {
            return jsonResponse(Response.Status.UNAUTHORIZED, errorBody("Invalid or missing token"))
        }

        val bodyText =
            try {
                readBody(session)
            } catch (e: Exception) {
                Timber.e(e, "Failed to read local control request body")
                return jsonResponse(
                    Response.Status.BAD_REQUEST,
                    errorBody("Malformed request body"),
                )
            }
        val bodyElement = bodyText?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }

        val response =
            try {
                // `session.parms` is already populated from the query string by the time `serve()`
                // runs (NanoHTTPD decodes it before dispatch) - no explicit parseBody-style call
                // needed, unlike the request body above.
                runBlocking {
                    router.handle(session.method.name, session.uri, session.parms, bodyElement)
                }
            } catch (e: Exception) {
                Timber.e(e, "LocalControlServer.serve failed")
                return jsonResponse(
                    Response.Status.INTERNAL_ERROR,
                    errorBody(e.message ?: "Internal error"),
                )
            }

        return jsonResponse(statusFor(response.status), response.body?.toString() ?: "{}")
    }

    /**
     * Reads the raw request body directly off the socket via `Content-Length`, rather than
     * [IHTTPSession.parseBody] - that method only special-cases `POST` (buffers into a `"postData"`
     * map entry) and `PUT` (streams to a temp file under `"content"`); `PATCH` matches neither case
     * and silently yields no body at all, which broke `PATCH /settings/downloads` (always "expected
     * a JSON object body") until this was caught during on-device verification.
     */
    private fun readBody(session: IHTTPSession): String? {
        val length = session.headers["content-length"]?.toIntOrNull() ?: return null
        if (length <= 0) return null
        val buffer = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = session.inputStream.read(buffer, read, length - read)
            if (n < 0) break
            read += n
        }
        return String(buffer, 0, read, Charsets.UTF_8)
    }

    private fun jsonResponse(status: Response.IStatus, body: String): Response =
        newFixedLengthResponse(status, "application/json", body)

    /**
     * Serves the bundled `findroid-cli` script (an asset copied from `cli/findroid-cli` at build
     * time - see `core/build.gradle.kts` - not read from a live git checkout) so a plain `curl
     * http://127.0.0.1:<port>/cli -o findroid-cli` from Termux works with zero setup, the same way
     * Shizuku's `rish` client is downloadable straight from the Shizuku app. `<port>` is
     * [BASE_PORT] for a release install, [portFor] for debug/staging - see the Settings > Local CLI
     * access screen for the exact command for whichever variant is actually installed.
     */
    private fun serveCliScript(): Response =
        try {
            val bytes = context.assets.open(CLI_ASSET_NAME).use { it.readBytes() }
            newFixedLengthResponse(
                    Response.Status.OK,
                    "text/plain; charset=utf-8",
                    ByteArrayInputStream(bytes),
                    bytes.size.toLong(),
                )
                .apply {
                    addHeader("Content-Disposition", "attachment; filename=\"$CLI_ASSET_NAME\"")
                }
        } catch (e: IOException) {
            Timber.e(e, "Failed to read bundled findroid-cli asset")
            jsonResponse(Response.Status.INTERNAL_ERROR, errorBody("findroid-cli asset missing"))
        }

    /**
     * NanoHTTPD's `Response.Status` is a fixed Java enum, but the debug proxy forwards whatever raw
     * HTTP status the proxied service returned (e.g. Sonarr's own 404) - not necessarily one of the
     * fixed constants. `Response.IStatus` is what [newFixedLengthResponse] actually accepts
     * (`Response.Status` is just its built-in implementation), so an arbitrary code that doesn't
     * match one of ours below is passed through as-is via a minimal custom implementation.
     */
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
        const val BASE_PORT = 48411
        const val CLI_PATH = "/cli"
        const val CLI_ASSET_NAME = "findroid-cli"
        private const val SOCKET_READ_TIMEOUT = 30_000
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Debug/staging installs get their own port, offset from [BASE_PORT], so they can run side
         * by side with a release install (or each other) - e.g. verifying a rename on a debug build
         * without force-stopping an already-installed release, or the CI/local signature-mismatch
         * workflow in AGENTS.md - without one variant's `startIfEnabled()` failing to bind because
         * another variant already grabbed the port. Found the hard way: a debug build launched
         * right after a release build was still running crashed at `BaseApplication.onCreate()`
         * with `BindException: EADDRINUSE` on the shared port - see TODO.md FINDROID-68's
         * follow-up.
         *
         * Keyed off the actual runtime `applicationId` suffix (".debug"/".staging"), not a Gradle
         * `BuildConfig` flag - `core` has no per-flavor `BuildConfig` of its own, and this needs no
         * plumbing through both `app/phone` and `app/tv`'s separate flavor setups. `findroid-cli`'s
         * `JOLLYFIN_LOCAL_URL` override exists for exactly this: point it at whichever of these
         * three ports the variant you're scripting against actually bound.
         */
        fun portFor(packageName: String): Int =
            when {
                packageName.endsWith(".debug") -> BASE_PORT + 1
                packageName.endsWith(".staging") -> BASE_PORT + 2
                else -> BASE_PORT
            }
    }
}
