package com.odiousapps.z2mdash.data

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Gzip compression for the exported config JSON, used by both the file-based
 * backup (Settings > Configuration Backup) and the MQTT-based one. Config
 * JSON is highly repetitive (the same field names over and over across many
 * panels), so gzip typically shrinks it 70-90%.
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

    /** A timestamped filename for a fresh file backup, e.g. "z2mdash-config-2026-08-24_01-45-30.txt". */
    // Plain ".txt", and the file content is base64 text (see SettingsScreen's
    // use of compressToBase64/decompressFromBase64), not raw gzip bytes -
    // some file managers sniff a file's actual byte content for the gzip
    // magic number (0x1f 0x8b) and offer to "extract" it instead of letting
    // it be selected/returned, regardless of what extension or MIME type the
    // file was declared with. Base64 text has no such signature to trip on.
    fun newBackupFileName(): String = "z2mdash-config-${currentTimestamp()}.txt"

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
}
