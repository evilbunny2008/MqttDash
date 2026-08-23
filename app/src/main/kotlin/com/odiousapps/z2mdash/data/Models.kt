package com.odiousapps.z2mdash.data

import kotlinx.serialization.Serializable

@Serializable
enum class MqttProtocol { TCP, SSL, WS, WSS }

@Serializable
data class Broker(
    val id: String,
    val name: String,
    val host: String,
    val protocol: MqttProtocol = MqttProtocol.TCP,
    val port: Int = 1883,
    val authEnabled: Boolean = false,
    val username: String = "",
    val password: String = "",
    val selfSignedCert: Boolean = false,
    // Raw certificate bytes (PEM or DER), Base64-encoded so it round-trips through
    // the plain-JSON config file cleanly.
    val selfSignedCertBase64: String? = null,
    val webSocketPath: String = "/mqtt",
    val clientId: String = "android_dashboard_${(10000..99999).random()}",
    val cleanSession: Boolean = false,
    val keepAliveSeconds: Int = 60,
    val connectionTimeoutSeconds: Int = 30,
    val autoConnect: Boolean = true,
    val showReconnectionStatus: Boolean = true
)

@Serializable
enum class TileIcon { HUMIDITY, MOISTURE, TEMPERATURE, SIGNAL, POWER, GAUGE, BATTERY, LIGHT }

@Serializable
sealed class Panel {
    abstract val id: String
    abstract val label: String
    abstract val brokerId: String
    // Optional. Panels sharing the same non-blank clusterName within a group
    // are rendered together in one bordered card with this name shown
    // underneath - e.g. all the tiles for one physical sensor device.
    abstract val clusterName: String
    // Controls left-to-right/top-to-bottom position within a group (lower
    // sorts first). Panels/clusters without an explicit order default to
    // Int.MAX_VALUE, so they fall in after anything explicitly ordered.
    abstract val displayOrder: Int

    @Serializable
    data class Sensor(
        override val id: String,
        override val label: String,
        override val brokerId: String,
        val topic: String,
        // Dot path into a JSON payload, e.g. "temperature" or "state.battery".
        // Leave blank to display the raw payload as-is.
        val jsonPath: String = "",
        val unit: String = "",
        val icon: TileIcon = TileIcon.GAUGE,
        val decimals: Int = 1,
        // Optional companion topic publishing the ideal range (retained). When set,
        // the tile flashes red below min / green within / blue above max. Defaults
        // to a topic shaped {"min":x,"max":y}; idealMinPath/idealMaxPath let it
        // instead point at differently-named sibling fields (e.g. "moisture_min" /
        // "moisture_max" inside a broader device config payload).
        val idealRangeTopic: String = "",
        val idealMinPath: String = "min",
        val idealMaxPath: String = "max",
        override val clusterName: String = "",
        override val displayOrder: Int = Int.MAX_VALUE
    ) : Panel()

    @Serializable
    data class Toggle(
        override val id: String,
        override val label: String,
        override val brokerId: String,
        val commandTopic: String,
        val onPayload: String = "ON",
        val offPayload: String = "OFF",
        // Optional feedback topic/path so the switch reflects the device's real state
        // rather than only the last command sent.
        val stateTopic: String = "",
        val stateJsonPath: String = "",
        val icon: TileIcon = TileIcon.POWER,
        override val clusterName: String = "",
        override val displayOrder: Int = Int.MAX_VALUE
    ) : Panel()
}

@Serializable
data class PanelGroup(
    val id: String,
    val name: String,
    val panels: List<Panel> = emptyList(),
    val collapsed: Boolean = false
)

/**
 * Tracks a device that was configured via its own "<topic>/app" payload, so the
 * app can keep watching that topic afterwards and automatically regenerate the
 * device's panels if the device republishes a changed config - not just a
 * one-off action.
 */
@Serializable
data class AutoConfiguredDevice(
    val brokerId: String,
    val sensorTopic: String,
    val appConfigTopic: String,
    // Raw payload last applied - compared against the live value to detect changes.
    val lastAppliedPayload: String,
    // IDs of the panels this device currently owns, so a reconfigure can
    // cleanly remove the old set before adding the new one.
    val createdPanelIds: List<String> = emptyList()
)

/**
 * A "<topic>/app" was seen for the first time (parses successfully, not
 * already autoconfigured, not previously dismissed) - waiting on the user to
 * accept or ignore it, surfaced as a Home screen banner and a notification.
 */
@Serializable
data class PendingAutoConfigDevice(
    val brokerId: String,
    val sensorTopic: String,
    val appConfigTopic: String,
    val deviceName: String
)

@Serializable
data class AppConfig(
    val brokers: List<Broker> = emptyList(),
    val groups: List<PanelGroup> = emptyList(),
    val backgroundWorkEnabled: Boolean = true,
    val autoConfiguredDevices: List<AutoConfiguredDevice> = emptyList(),
    val pendingAutoConfigDevices: List<PendingAutoConfigDevice> = emptyList(),
    // "brokerId|appConfigTopic" composite keys the user has explicitly dismissed,
    // so a declined device isn't re-prompted on every subsequent scan.
    val ignoredAppConfigTopics: List<String> = emptyList()
)
