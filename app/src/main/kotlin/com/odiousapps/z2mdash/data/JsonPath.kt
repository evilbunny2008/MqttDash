package com.odiousapps.z2mdash.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Extracts a value from a JSON MQTT payload using a simple dot path,
 * e.g. "temperature" or "state.battery" for nested objects.
 *
 * If [path] is blank the raw payload is returned as-is (trimmed) - useful for
 * topics that publish a bare value rather than a JSON object.
 * Returns null if the payload isn't valid JSON or the path doesn't resolve,
 * so the UI can show a "--" placeholder instead of crashing.
 */
object JsonPath {
    fun extract(rawPayload: String, path: String): String? {
        if (path.isBlank()) return rawPayload.trim()
        return try {
            var element: JsonElement = Json.parseToJsonElement(rawPayload)
            for (segment in path.split(".")) {
                val obj = element as? JsonObject ?: return null
                element = obj[segment] ?: return null
            }
            when (element) {
                is JsonPrimitive -> element.contentOrNull ?: element.toString()
                else -> element.toString()
            }
        } catch (e: Exception) {
            null
        }
    }
}
