package dev.pschmitt.jellyfin.localcontrol

import dev.pschmitt.jellyfin.security.SecureCredentialStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single bearer token for the local control API ([LocalControlProvider]) - shown once in
 * Settings > Local CLI access for the user to copy into `findroid-cli`'s config, and
 * regeneratable at will (invalidating whatever was copied out before). Deliberately not a
 * per-client pairing scheme: this app has no reliable way to identify *which* process is calling
 * it (a `ContentProvider.call()` from a shell command reports the shell's own uid, not something
 * meaningfully tied to "Termux" specifically), so a single shared secret the user explicitly
 * copies out is the simpler, equally honest security model.
 */
@Singleton
class LocalControlAuth @Inject constructor(private val secureCredentialStore: SecureCredentialStore) {
    private val random = SecureRandom()

    fun getOrCreateToken(): String {
        secureCredentialStore.getString(TOKEN_KEY)?.let { return it }
        return regenerateToken()
    }

    fun regenerateToken(): String {
        val token = randomToken()
        secureCredentialStore.putStringBlocking(TOKEN_KEY, token)
        return token
    }

    fun verifyToken(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val stored = secureCredentialStore.getString(TOKEN_KEY) ?: return false
        // Constant-time comparison - this is a bearer secret, no reason to leak timing info
        // about how much of it matched.
        return MessageDigest.isEqual(token.toByteArray(Charsets.UTF_8), stored.toByteArray(Charsets.UTF_8))
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        private const val TOKEN_KEY = "local_control_token"
    }
}
