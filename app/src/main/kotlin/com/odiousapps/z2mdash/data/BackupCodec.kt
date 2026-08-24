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
