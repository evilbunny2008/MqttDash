package com.odiousapps.z2mdash.mqtt

import com.odiousapps.z2mdash.data.AppConfig
import com.odiousapps.z2mdash.data.Broker
import com.odiousapps.z2mdash.data.Panel
import com.odiousapps.z2mdash.data.PayloadCacheEntry
import com.odiousapps.z2mdash.data.PayloadCacheRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * App-wide singleton (held by Z2mDashApplication) that owns one MqttConnection
 * per configured broker, keeps subscriptions in sync with whatever panels exist,
 * and aggregates incoming payloads/connection state into simple StateFlows the
 * Compose UI can collect directly.
 */
class MqttConnectionManager(
    private val scope: CoroutineScope,
    private val payloadCacheRepository: PayloadCacheRepository
) {

    private val connections = mutableMapOf<String, MqttConnection>()

    // Seeded from disk so the dashboard has correct, already-varied "updated N
    // ago" data to show immediately on launch, before any MQTT traffic (even
    // retained messages) has arrived this session.
    private val cachedOnStartup = payloadCacheRepository.load()

    private val _latestPayloads = MutableStateFlow(cachedOnStartup.mapValues { it.value.payload })
    val latestPayloads: StateFlow<Map<String, String>> = _latestPayloads

    // When (device time, i.e. System.currentTimeMillis() at receipt) each topic's
    // latest payload arrived - used to show "updated 2 hours ago" on the dashboard.
    private val _latestPayloadTimestamps = MutableStateFlow(cachedOnStartup.mapValues { it.value.timestamp })
    val latestPayloadTimestamps: StateFlow<Map<String, Long>> = _latestPayloadTimestamps

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
            // Subscribed to everything continuously (not just when the Discover
            // screen is open) so new "<topic>/app" devices can be detected and
            // prompted for in the background. Means every message the broker
            // publishes flows through the app now, not just ones panels ask for.
            conn.subscribe("#")
        }

        config.groups.flatMap { it.panels }.forEach { panel ->
            when (panel) {
                is Panel.Sensor -> {
                    connections[panel.brokerId]?.subscribe(panel.topic)
                    if (panel.idealRangeTopic.isNotBlank()) {
                        connections[panel.brokerId]?.subscribe(panel.idealRangeTopic)
                    }
                }
                is Panel.Toggle -> if (panel.stateTopic.isNotBlank()) {
                    connections[panel.brokerId]?.subscribe(panel.stateTopic)
                }
            }
        }

        // Keep watching every autoconfigured device's own topics explicitly,
        // independent of whether any current panel happens to reference them -
        // otherwise a device that later drops its ideal-range fields (for
        // example) could silently stop being watched for config changes.
        config.autoConfiguredDevices.forEach { device ->
            connections[device.brokerId]?.subscribe(device.appConfigTopic)
            connections[device.brokerId]?.subscribe(device.sensorTopic)
        }
    }

    /**
     * Subscribes a broker to every topic ("#") so its retained messages flow
     * into [latestPayloads] for the Discover Sensors screen to scan. Safe to
     * call more than once - subscriptions are idempotent.
     */
    fun discoverAll(brokerId: String) {
        connections[brokerId]?.subscribe("#")
    }

    /** Periodically writes the current payload/timestamp state to disk. Call once at startup. */
    fun startPersistingCache() {
        scope.launch(Dispatchers.Default) {
            while (true) {
                delay(30_000)
                persistCacheNow()
            }
        }
    }

    private fun persistCacheNow() {
        val payloads = _latestPayloads.value
        val timestamps = _latestPayloadTimestamps.value
        val merged = payloads.mapNotNull { (key, payload) ->
            val timestamp = timestamps[key] ?: return@mapNotNull null
            key to PayloadCacheEntry(payload, timestamp)
        }.toMap()
        payloadCacheRepository.save(merged)
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
                val key = keyFor(broker.id, msg.topic)
                _latestPayloads.update { it + (key to msg.payload) }
                _latestPayloadTimestamps.update { it + (key to System.currentTimeMillis()) }
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
