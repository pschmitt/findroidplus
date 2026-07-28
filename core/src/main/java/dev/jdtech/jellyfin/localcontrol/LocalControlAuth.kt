package dev.jdtech.jellyfin.localcontrol

import dev.jdtech.jellyfin.security.SecureCredentialStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A still-undecided pairing handshake, held in memory only - never worth persisting across a
 * process restart, and a lost-on-restart pending request just means the CLI has to retry `pair`. */
data class PendingPairingRequest(
    val requestId: String,
    val clientId: String,
    val uid: Int,
    val packageName: String,
    val createdAt: Long,
)

/** A CLI install the user has approved once - [tokenHash] is `SHA-256(token)`, never the raw
 * token itself, matching the "don't keep secrets in plaintext even in our own store" posture
 * [SecureCredentialStore] otherwise represents (this one just happens to be self-issued rather
 * than a Sonarr/Jellyfin credential). [uid] is re-checked on every authenticated call, not just at
 * pairing time - see [LocalControlAuth.verifyToken]. */
@Serializable
data class PairedClient(
    val clientId: String,
    val uid: Int,
    val packageName: String,
    val tokenHash: String,
    val pairedAt: Long,
)

/**
 * Pairing state machine + paired-client registry for the local control socket
 * ([LocalControlServer]). A pairing request only ever completes because a human tapped
 * Approve/Deny on the notification [PairingActionReceiver] handles - there is no auto-approve
 * path. [uid]/[packageName] come from [android.net.LocalSocket.peerCredentials] at request time
 * (kernel-verified, unspoofable), not anything the client declares about itself.
 */
@Singleton
class LocalControlAuth @Inject constructor(private val secureCredentialStore: SecureCredentialStore) {
    private val pending = ConcurrentHashMap<String, PendingPairingRequest>()
    private val storeMutex = Mutex()
    private val random = SecureRandom()

    fun beginPairing(clientId: String, uid: Int, packageName: String): PendingPairingRequest {
        pruneExpiredPending()
        val request =
            PendingPairingRequest(
                requestId = randomToken(16),
                clientId = clientId,
                uid = uid,
                packageName = packageName,
                createdAt = System.currentTimeMillis(),
            )
        pending[request.requestId] = request
        return request
    }

    fun pendingRequest(requestId: String): PendingPairingRequest? {
        pruneExpiredPending()
        return pending[requestId]
    }

    /** Issues and persists a new token for [requestId], returning the raw token to hand back to
     * the still-waiting CLI connection - `null` if the request is gone (already resolved, or
     * expired past [PENDING_TTL_MILLIS]). */
    suspend fun approve(requestId: String): String? {
        val request = pending.remove(requestId) ?: return null
        val token = randomToken(32)
        val hash = sha256(token)
        storeMutex.withLock {
            val clients = readClients().filterNot { it.clientId == request.clientId }
            val updated =
                clients +
                    PairedClient(
                        clientId = request.clientId,
                        uid = request.uid,
                        packageName = request.packageName,
                        tokenHash = hash,
                        pairedAt = System.currentTimeMillis(),
                    )
            writeClients(updated)
        }
        return token
    }

    fun deny(requestId: String) {
        pending.remove(requestId)
    }

    /** `true` only if [token] hashes to a stored, still-registered client **and** [uid] (the
     * live connection's own kernel-verified peer uid) still matches the uid recorded when that
     * client was paired - rejects a token that somehow ended up in a different app's hands, not
     * just a wrong/unknown token. */
    suspend fun verifyToken(token: String?, uid: Int): Boolean {
        if (token.isNullOrBlank()) return false
        val hash = sha256(token)
        val client = storeMutex.withLock { readClients().find { it.tokenHash == hash } } ?: return false
        return client.uid == uid
    }

    suspend fun listPairedClients(): List<PairedClient> = storeMutex.withLock { readClients() }

    suspend fun revoke(clientId: String) {
        storeMutex.withLock { writeClients(readClients().filterNot { it.clientId == clientId }) }
    }

    private fun pruneExpiredPending() {
        val now = System.currentTimeMillis()
        pending.entries.removeIf { now - it.value.createdAt > PENDING_TTL_MILLIS }
    }

    private fun readClients(): List<PairedClient> {
        val raw = secureCredentialStore.getString(STORE_KEY) ?: return emptyList()
        return runCatching { json.decodeFromString<List<PairedClient>>(raw) }.getOrDefault(emptyList())
    }

    private fun writeClients(clients: List<PairedClient>) {
        secureCredentialStore.putStringBlocking(STORE_KEY, json.encodeToString(clients))
    }

    private fun randomToken(byteLength: Int): String {
        val bytes = ByteArray(byteLength)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val STORE_KEY = "local_control_paired_clients"
        private const val PENDING_TTL_MILLIS = 2 * 60 * 1000L
        private val json = Json { ignoreUnknownKeys = true }
    }
}
