package dev.jdtech.jellyfin.localcontrol

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

/**
 * Accept loop for the local control socket - an `android.net.LocalServerSocket` bound in the
 * Linux abstract namespace, so any process on the device (Termux included) can connect regardless
 * of app-private filesystem sandboxing, while [LocalSocket.getPeerCredentials] still gives a
 * kernel-verified uid for every connection - the actual authorization boundary (see
 * [LocalControlAuth]), not just the bearer token each authenticated request also carries. One
 * request per connection except a pairing handshake, which is held open until
 * [PairingActionReceiver] resolves it or [PENDING_TIMEOUT_MILLIS] elapses.
 */
@Singleton
class LocalControlServer
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val localControlAuth: LocalControlAuth,
    private val router: LocalControlRouter,
) {
    private var serverSocket: LocalServerSocket? = null
    private var acceptJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val waitingPairings = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    @Synchronized
    fun startIfEnabled() {
        if (!appPreferences.getValue(appPreferences.localControlEnabled)) return
        start()
    }

    @Synchronized
    fun isRunning(): Boolean = serverSocket != null

    /** `true` once the socket is actually bound and accepting connections (including if it
     * already was) - `false` if binding just failed. The caller (currently
     * `LocalAccessViewModel`) uses this to keep the Settings toggle honest instead of showing
     * "enabled" while nothing is actually listening. */
    @Synchronized
    fun start(): Boolean {
        if (serverSocket != null) return true
        val socket =
            try {
                LocalServerSocket(SOCKET_NAME)
            } catch (e: Exception) {
                Timber.e(e, "Failed to bind local control socket")
                return false
            }
        serverSocket = socket
        acceptJob =
            scope.launch {
                while (isActive) {
                    val client =
                        try {
                            socket.accept()
                        } catch (e: Exception) {
                            if (isActive) Timber.w(e, "Local control accept() failed")
                            continue
                        }
                    launch { handleConnection(client) }
                }
            }
        return true
    }

    @Synchronized
    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    /** Called by [PairingActionReceiver] when the user taps Approve/Deny - resolves the
     * still-waiting connection for [requestId], if any (it may already have timed out, in which
     * case this is a no-op). */
    fun resolvePairing(requestId: String, approved: Boolean) {
        waitingPairings.remove(requestId)?.complete(approved)
    }

    private suspend fun handleConnection(client: LocalSocket) {
        client.use { socket ->
            try {
                val uid = socket.peerCredentials.uid
                val line = BufferedReader(InputStreamReader(socket.inputStream)).readLine() ?: return
                val request = runCatching { json.decodeFromString<LocalControlRequest>(line) }.getOrNull()
                if (request == null) {
                    respond(socket, LocalControlResponse(LocalControlStatus.BAD_REQUEST))
                    return
                }

                if (request.type == LocalControlMessageType.PAIR_REQUEST) {
                    handlePairing(socket, request, uid)
                    return
                }

                if (!localControlAuth.verifyToken(request.auth, uid)) {
                    respond(socket, LocalControlResponse(LocalControlStatus.UNAUTHORIZED))
                    return
                }
                val response = router.handle(request.method, request.path, request.body)
                respond(socket, response)
            } catch (e: Exception) {
                Timber.e(e, "Local control connection failed")
            }
        }
    }

    private suspend fun handlePairing(socket: LocalSocket, request: LocalControlRequest, uid: Int) {
        val clientId = request.clientId
        if (clientId.isNullOrBlank()) {
            respond(socket, LocalControlResponse(LocalControlStatus.BAD_REQUEST))
            return
        }
        val packageName = context.packageManager.getPackagesForUid(uid)?.firstOrNull() ?: "uid:$uid"
        val pending = localControlAuth.beginPairing(clientId, uid, packageName)

        val deferred = CompletableDeferred<Boolean>()
        waitingPairings[pending.requestId] = deferred
        PairingNotifier.show(context, pending.requestId, packageName)

        val approved = withTimeoutOrNull(PENDING_TIMEOUT_MILLIS) { deferred.await() }
        waitingPairings.remove(pending.requestId)
        PairingNotifier.cancel(context, pending.requestId)

        if (approved == true) {
            val token = localControlAuth.approve(pending.requestId)
            if (token != null) {
                respond(socket, LocalControlResponse(LocalControlStatus.OK, tokenBody(token)))
                return
            }
        } else {
            localControlAuth.deny(pending.requestId)
        }
        respond(socket, LocalControlResponse(LocalControlStatus.UNAUTHORIZED))
    }

    private fun respond(socket: LocalSocket, response: LocalControlResponse) {
        runCatching {
            val line = json.encodeToString(response) + "\n"
            socket.outputStream.write(line.toByteArray(Charsets.UTF_8))
            socket.outputStream.flush()
        }
    }

    private fun tokenBody(token: String) = buildJsonObject { put("token", JsonPrimitive(token)) }

    companion object {
        const val SOCKET_NAME = "findroidplus_control"
        private const val PENDING_TIMEOUT_MILLIS = 2 * 60 * 1000L
        private val json = Json { ignoreUnknownKeys = true }
    }
}
