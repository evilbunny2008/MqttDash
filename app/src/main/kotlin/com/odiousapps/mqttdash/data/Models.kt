package com.odiousapps.mqttdash.data

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
        // Optional companion topic publishing {"min": x, "max": y} (retained).
        // When set, the tile flashes red below min / blue above max.
        val idealRangeTopic: String = "",
        override val clusterName: String = ""
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
        override val clusterName: String = ""
    ) : Panel()
}

@Serializable
data class PanelGroup(
    val id: String,
    val name: String,
    val panels: List<Panel> = emptyList(),
    val collapsed: Boolean = false
)

@Serializable
data class AppConfig(
    val brokers: List<Broker> = emptyList(),
    val groups: List<PanelGroup> = emptyList(),
    val backgroundWorkEnabled: Boolean = true
)
