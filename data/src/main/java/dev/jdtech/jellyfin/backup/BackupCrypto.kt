package dev.jdtech.jellyfin.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Optional-password encryption for backup files, used both for manual "Back up now" exports (the
 * password is typed fresh each time) and scheduled auto-backups (the password comes from
 * AppPreferences.autoBackupPassword, if set). AES-256-GCM with a PBKDF2-derived key; format is
 * `[MAGIC][flag byte][salt(16)+iv(12)+ciphertext] or [MAGIC][flag byte][plain bytes]`, so restore
 * can tell up-front whether a password is needed instead of guessing from a failed parse.
 */
object BackupCrypto {
    private val MAGIC = byteArrayOf('F'.code.toByte(), 'R'.code.toByte(), 'B'.code.toByte(), '1'.code.toByte())
    private const val FLAG_PLAIN: Byte = 0
    private const val FLAG_ENCRYPTED: Byte = 1
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 210_000
    private const val KEY_LENGTH_BITS = 256

    class WrongPasswordException : Exception("Incorrect backup password")

    class PasswordRequiredException : Exception("This backup is password-protected")

    class CorruptBackupException : Exception("Not a valid Findroid backup file")

    fun encode(data: ByteArray, password: String?): ByteArray {
        if (password.isNullOrEmpty()) {
            return MAGIC + byteArrayOf(FLAG_PLAIN) + data
        }
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(data)
        return MAGIC + byteArrayOf(FLAG_ENCRYPTED) + salt + iv + ciphertext
    }

    /** @throws PasswordRequiredException if the file is encrypted but [password] is null/blank. */
    fun decode(bytes: ByteArray, password: String?): ByteArray {
        if (bytes.size < MAGIC.size + 1 || !bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw CorruptBackupException()
        }
        val flag = bytes[MAGIC.size]
        val rest = bytes.copyOfRange(MAGIC.size + 1, bytes.size)
        if (flag == FLAG_PLAIN) return rest
        if (password.isNullOrEmpty()) throw PasswordRequiredException()

        if (rest.size < SALT_SIZE + IV_SIZE) throw CorruptBackupException()
        val salt = rest.copyOfRange(0, SALT_SIZE)
        val iv = rest.copyOfRange(SALT_SIZE, SALT_SIZE + IV_SIZE)
        val ciphertext = rest.copyOfRange(SALT_SIZE + IV_SIZE, rest.size)
        val key = deriveKey(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw WrongPasswordException()
        }
    }

    fun isEncrypted(bytes: ByteArray): Boolean {
        if (bytes.size < MAGIC.size + 1 || !bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw CorruptBackupException()
        }
        return bytes[MAGIC.size] == FLAG_ENCRYPTED
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
