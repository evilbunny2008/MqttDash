package com.odiousapps.mqttdash.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Turns a raw bag of "topic -> last payload" (as gathered by subscribing a
 * broker to "#") into a list of candidate sensors: topics whose payload is a
 * JSON object with at least one numeric field. Topics that are commands,
 * availability pings, bridge/system chatter, or plain non-JSON strings are
 * skipped automatically because they simply won't parse into any numeric
 * fields - no topic allow/deny list to maintain by hand.
 */
object SensorDiscovery {

    data class DiscoveredField(val key: String, val sampleValue: Double)

    data class DiscoveredSensor(
        val topic: String,
        val fields: List<DiscoveredField>,
        // Present when "<topic>/ideal" also exists among the observed topics.
        val idealRangeTopic: String?
    )

    // Topics matching these patterns are structural, not sensor data - skip them
    // even if they happen to contain a stray number (defence in depth; in
    // practice the "must have a numeric field" check below already excludes
    // almost all of these).
    private val ignoredSuffixes = listOf("/set", "/get", "/availability", "/ideal", "/config")
    private val ignoredSubstrings = listOf("/bridge/")

    fun discoverSensors(payloadsForBroker: Map<String, String>): List<DiscoveredSensor> {
        val allTopics = payloadsForBroker.keys
        return payloadsForBroker.mapNotNull { (topic, payload) ->
            if (isIgnorable(topic)) return@mapNotNull null
            val fields = numericFieldsOf(payload)
            if (fields.isEmpty()) return@mapNotNull null
            val idealTopic = "$topic/ideal".takeIf { it in allTopics }
            DiscoveredSensor(topic, fields, idealTopic)
        }.sortedBy { it.topic }
    }

    private fun isIgnorable(topic: String): Boolean {
        if (ignoredSubstrings.any { topic.contains(it) }) return true
        if (ignoredSuffixes.any { topic.endsWith(it) }) return true
        return false
    }

    private fun numericFieldsOf(payload: String): List<DiscoveredField> = try {
        val element = Json.parseToJsonElement(payload)
        val obj = element as? JsonObject
        if (obj == null) {
            emptyList()
        } else {
            obj.entries.mapNotNull { (key, value) ->
                val num = (value as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                DiscoveredField(key, num)
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** A readable default label for a raw JSON key, e.g. "soil_moisture" -> "Soil Moisture". */
    fun suggestedLabel(key: String): String = when (key.lowercase()) {
        "linkquality" -> "Link Quality"
        else -> key.split("_").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }

    fun suggestedIcon(key: String): TileIcon = when {
        key.contains("moisture", ignoreCase = true) -> TileIcon.MOISTURE
        key.contains("humidity", ignoreCase = true) -> TileIcon.HUMIDITY
        key.contains("temperature", ignoreCase = true) -> TileIcon.TEMPERATURE
        key.contains("battery", ignoreCase = true) -> TileIcon.BATTERY
        key.contains("linkquality", ignoreCase = true) -> TileIcon.SIGNAL
        else -> TileIcon.GAUGE
    }

    fun suggestedUnit(key: String): String = when {
        key.contains("temperature", ignoreCase = true) -> "\u00b0C"
        key.contains("humidity", ignoreCase = true) -> "%"
        key.contains("moisture", ignoreCase = true) -> "%"
        key.contains("battery", ignoreCase = true) -> "%"
        else -> ""
    }
}
