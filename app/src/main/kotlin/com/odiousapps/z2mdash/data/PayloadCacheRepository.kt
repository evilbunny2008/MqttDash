package com.odiousapps.z2mdash.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PayloadCacheEntry(val payload: String, val timestamp: Long)

/**
 * Persists the last-known payload (and when it arrived) for every topic, so
 * the dashboard can show correct "updated N ago" ages - including each
 * payload's own "last_seen" field where present - the instant the app opens,
 * from whatever was last seen, rather than showing nothing (or a misleadingly
 * uniform "just now") until fresh MQTT messages repopulate everything from
 * scratch after every restart.
 */
class PayloadCacheRepository(context: Context) {
    private val file = File(context.filesDir, "payload_cache.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): Map<String, PayloadCacheEntry> = try {
        if (file.exists()) json.decodeFromString(file.readText()) else emptyMap()
    } catch (_: Exception) {
        emptyMap()
    }

    fun save(entries: Map<String, PayloadCacheEntry>) {
        try {
            file.writeText(json.encodeToString(entries))
        } catch (_: Exception) {
            // Best-effort cache - fine to silently skip a write if it fails.
        }
    }
}
