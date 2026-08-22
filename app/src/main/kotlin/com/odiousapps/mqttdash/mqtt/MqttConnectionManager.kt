package com.odiousapps.mqttdash.mqtt

import com.odiousapps.mqttdash.data.AppConfig
import com.odiousapps.mqttdash.data.Broker
import com.odiousapps.mqttdash.data.Panel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * App-wide singleton (held by MqttDashApplication) that owns one MqttConnection
 * per configured broker, keeps subscriptions in sync with whatever panels exist,
 * and aggregates incoming payloads/connection state into simple StateFlows the
 * Compose UI can collect directly.
 */
class MqttConnectionManager(private val scope: CoroutineScope) {

    private val connections = mutableMapOf<String, MqttConnection>()

    private val _latestPayloads = MutableStateFlow<Map<String, String>>(emptyMap())
    val latestPayloads: StateFlow<Map<String, String>> = _latestPayloads

    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates

    private fun keyFor(brokerId: String, topic: String) = "$brokerId|$topic"

    /** Call whenever the persisted config changes (brokers added/removed, panels added/removed). */
    fun applyConfig(config: AppConfig) {
        val brokerIds = config.brokers.map { it.id }.toSet()
        connections.keys.filterNot { it in brokerIds }.toList().forEach { id ->
            connections.remove(id)?.disconnect()
        }

        config.brokers.forEach { broker ->
            val conn = connections.getOrPut(broker.id) { createConnection(broker) }
            if (broker.autoConnect) conn.connect()
        }

        config.groups.flatMap { it.panels }.forEach { panel ->
            when (panel) {
                is Panel.Sensor -> connections[panel.brokerId]?.subscribe(panel.topic)
                is Panel.Toggle -> if (panel.stateTopic.isNotBlank()) {
                    connections[panel.brokerId]?.subscribe(panel.stateTopic)
                }
            }
        }
    }

    private fun createConnection(broker: Broker): MqttConnection {
        val conn = MqttConnection(broker)
        scope.launch(Dispatchers.Default) {
            conn.connectionState.collect { state ->
                _connectionStates.update { it + (broker.id to state) }
            }
        }
        scope.launch(Dispatchers.Default) {
            conn.messages.collect { msg ->
                _latestPayloads.update { it + (keyFor(broker.id, msg.topic) to msg.payload) }
            }
        }
        return conn
    }

    fun publish(brokerId: String, topic: String, payload: String) {
        connections[brokerId]?.publish(topic, payload)
    }

    fun reconnect(brokerId: String) {
        connections[brokerId]?.apply {
            disconnect()
            connect()
        }
    }

    fun shutdown() {
        connections.values.forEach { it.disconnect() }
        connections.clear()
    }
}
