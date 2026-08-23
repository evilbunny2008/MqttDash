package com.odiousapps.mqttdash.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

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
        val idealRangeTopic: String?,
        // Present when "<topic>/app" also exists among the observed topics -
        // a device-published dashboard config (name, field list, thresholds).
        val appConfigTopic: String?
    )

    /**
     * A device-published dashboard config, e.g.
     * {"name":"Alpinia - 01","moisture_min":60,"moisture_max":75,
     *  "panels":["linkquality","humidity","temperature","soil_moisture"]}
     *
     * [rangePairs] maps a base concept name (e.g. "moisture") to its
     * (minKey, maxKey) pair, discovered generically by looking for any two
     * numeric fields named "<base>_min" and "<base>_max" in the same
     * payload - not hardcoded to "moisture" specifically, so a future device
     * publishing "temperature_min"/"temperature_max" works the same way.
     */
    data class DeviceAppConfig(
        val name: String,
        // Optional. When set, panels go into a dashboard group with this exact
        // name (created if it doesn't exist yet), bypassing whatever group is
        // picked in the UI - the device fully self-configures.
        val group: String?,
        // Optional. Position of this device's cluster/panels within the group
        // (lower sorts first) - the order panels appear *inside* [group], not
        // the group's own position among other dashboard groups.
        val groupOrder: Int?,
        val panelFields: List<String>,
        // Optional. Parallel to panelFields by index - custom label per field.
        // Falls back to suggestedLabel() for any field with no matching entry.
        val labels: List<String>,
        val rangePairs: Map<String, Pair<String, String>>
    )

    // Topics matching these patterns are structural, not sensor data - skip them
    // even if they happen to contain a stray number (defence in depth; in
    // practice the "must have a numeric field" check below already excludes
    // almost all of these).
    private val ignoredSuffixes = listOf("/set", "/get", "/availability", "/ideal", "/app", "/config")
    private val ignoredSubstrings = listOf("/bridge/")

    fun discoverSensors(payloadsForBroker: Map<String, String>): List<DiscoveredSensor> {
        val allTopics = payloadsForBroker.keys
        return payloadsForBroker.mapNotNull { (topic, payload) ->
            if (isIgnorable(topic)) return@mapNotNull null
            val fields = numericFieldsOf(payload)
            if (fields.isEmpty()) return@mapNotNull null
            val idealTopic = "$topic/ideal".takeIf { it in allTopics }
            val appTopic = "$topic/app".takeIf { it in allTopics }
            DiscoveredSensor(topic, fields, idealTopic, appTopic)
        }.sortedWith(compareByDescending<DiscoveredSensor> { it.appConfigTopic != null }.thenBy { it.topic })
    }

    /** Parses a "<topic>/app" payload into a DeviceAppConfig, or null if it doesn't look like one. */
    fun parseDeviceAppConfig(payload: String): DeviceAppConfig? = try {
        val obj = Json.parseToJsonElement(payload) as? JsonObject
        val panelsArray = obj?.get("panels") as? JsonArray
        val panelFields = panelsArray?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        if (obj == null || panelFields.isNullOrEmpty()) {
            null
        } else {
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: ""
            val group = (obj["group"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            val groupOrder = (obj["group_order"] as? JsonPrimitive)?.intOrNull
            val labelsArray = obj["labels"] as? JsonArray
            val labels = labelsArray?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
            val numericKeys = obj.entries
                .filter { (_, v) -> (v as? JsonPrimitive)?.doubleOrNull != null }
                .map { it.key }
            val rangePairs = mutableMapOf<String, Pair<String, String>>()
            numericKeys.filter { it.endsWith("_min") }.forEach { minKey ->
                val base = minKey.removeSuffix("_min")
                val maxKey = "${base}_max"
                if (maxKey in numericKeys) rangePairs[base] = minKey to maxKey
            }
            DeviceAppConfig(name, group, groupOrder, panelFields, labels, rangePairs)
        }
    } catch (e: Exception) {
        null
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
