package com.odiousapps.z2mdash.data

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gzip compression for the exported config JSON, used by both the file-based
 * backup (Settings > Configuration Backup) and the MQTT-based one. Config
 * JSON is highly repetitive (the same field names over and over across many
 * panels), so gzip typically shrinks it 70-90%. Also handles optional
 * AES-256-GCM encryption of the compressed bytes, keyed by a password
 * (typically a broker's own password, so there's no separate encryption
 * password to remember) via PBKDF2.
 */
object BackupCodec {

    // yyyy-MM-dd_HH-mm-ss - zero-padded and most-significant-first, so plain
    // string sorting already puts backups in chronological order (no need to
    // parse it back to compare two of these). Avoids ":" since it's an MQTT
    // wildcard-adjacent character some brokers/tools are fussy about in topics,
    // and colons aren't valid in filenames on some platforms either.
    private val TIMESTAMP_FORMAT = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    private fun currentTimestamp(): String = java.time.LocalDateTime.now().format(TIMESTAMP_FORMAT)

    /** A new "<baseTopic>/<timestamp>" topic for a fresh backup, e.g. "z2mdash/backup/2026-08-24_01-45-30". */
    fun newBackupTopic(baseTopic: String): String = "${baseTopic.trim().trim('/')}/${currentTimestamp()}"

    /** A timestamped filename for a fresh file backup, e.g. "z2mdash-config-2026-08-24_01-45-30.json.gz". */
    fun newBackupFileName(): String = "z2mdash-config-${currentTimestamp()}.json.gz"

    /** Parses the trailing "<timestamp>" segment of a backup topic back into a display-friendly string, or null if it doesn't match. */
    fun displayTimestamp(backupTopic: String): String? {
        val stamp = backupTopic.substringAfterLast('/')
        return try {
            val parsed = java.time.LocalDateTime.parse(stamp, TIMESTAMP_FORMAT)
            parsed.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, h:mm:ss a"))
        } catch (_: Exception) {
            null
        }
    }

    fun compress(text: String): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return output.toByteArray()
    }

    fun decompress(bytes: ByteArray): String =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().toString(Charsets.UTF_8) }

    /** For MQTT payloads, which this app's publish/subscribe plumbing carries as plain strings. */
    fun compressToBase64(text: String): String =
        Base64.encodeToString(compress(text), Base64.NO_WRAP)

    fun decompressFromBase64(base64: String): String =
        decompress(Base64.decode(base64, Base64.NO_WRAP))

    // Magic bytes identifying an encrypted backup - distinct from gzip's own
    // 0x1F 0x8B header, so a file's format can be auto-detected from its
    // content alone (no separate extension/flag needed) before deciding
    // whether to prompt for a password on restore.
    private val ENCRYPTED_MAGIC = byteArrayOf('Z'.code.toByte(), '2'.code.toByte(), 'M'.code.toByte(), 'E'.code.toByte())
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size >= ENCRYPTED_MAGIC.size && bytes.copyOfRange(0, ENCRYPTED_MAGIC.size).contentEquals(ENCRYPTED_MAGIC)

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts already-gzip-compressed bytes with AES-256-GCM, keyed by
     * [password] via PBKDF2. Prepends a magic header plus a fresh random
     * salt/IV needed to decrypt later - neither is secret, they just need to
     * be unique per encryption, which is why they travel alongside the
     * ciphertext rather than needing to be remembered separately.
     */
    fun encrypt(compressedBytes: ByteArray, password: String): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(compressedBytes)
        return ENCRYPTED_MAGIC + salt + iv + ciphertext
    }

    /**
     * Reverses [encrypt]. Returns null if the password is wrong or the data
     * is corrupted - GCM's own authentication tag catches both cases the
     * same way, so there's no way to tell them apart, only that decryption
     * didn't succeed.
     */
    fun decrypt(data: ByteArray, password: String): ByteArray? {
        if (!isEncrypted(data)) return null
        return try {
            var offset = ENCRYPTED_MAGIC.size
            val salt = data.copyOfRange(offset, offset + SALT_LENGTH)
            offset += SALT_LENGTH
            val iv = data.copyOfRange(offset, offset + IV_LENGTH)
            offset += IV_LENGTH
            val ciphertext = data.copyOfRange(offset, data.size)
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        }
    }
}
