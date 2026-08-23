package com.odiousapps.mqttdash.mqtt

import com.odiousapps.mqttdash.data.AutoConfiguredDevice
import com.odiousapps.mqttdash.data.ConfigRepository
import com.odiousapps.mqttdash.data.PanelGroup
import com.odiousapps.mqttdash.data.SensorDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Keeps every device that was configured via its own "<topic>/app" payload
 * (see SensorDiscovery/DiscoverScreen) up to date: whenever that topic's
 * retained payload changes - not just at the moment "Apply device config" was
 * pressed - this regenerates the device's panels to match, live, without the
 * user needing to revisit the Discover screen.
 */
class DeviceAutoConfigManager(
    private val configRepository: ConfigRepository,
    private val connectionManager: MqttConnectionManager
) {
    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            connectionManager.latestPayloads.collect { payloads ->
                reconcile(payloads)
            }
        }
    }

    private fun reconcile(payloads: Map<String, String>) {
        val config = configRepository.config.value
        config.autoConfiguredDevices.forEach { device ->
            val currentPayload = payloads["${device.brokerId}|${device.appConfigTopic}"] ?: return@forEach
            if (currentPayload == device.lastAppliedPayload) return@forEach

            val deviceConfig = SensorDiscovery.parseDeviceAppConfig(currentPayload) ?: return@forEach
            val sensorPayload = payloads["${device.brokerId}|${device.sensorTopic}"]
            val sensorFieldKeys = sensorPayload?.let { SensorDiscovery.fieldKeysOf(it) } ?: emptySet()

            val newPanels = SensorDiscovery.buildPanels(
                brokerId = device.brokerId,
                sensorTopic = device.sensorTopic,
                sensorFieldKeys = sensorFieldKeys,
                appConfigTopic = device.appConfigTopic,
                appConfigPayload = currentPayload,
                deviceConfig = deviceConfig
            )
            if (newPanels.isEmpty()) return@forEach

            val targetGroupId = resolveTargetGroupId(deviceConfig.group, device) ?: return@forEach

            val updatedDevice = device.copy(
                lastAppliedPayload = currentPayload,
                createdPanelIds = newPanels.map { it.id }
            )
            configRepository.applyDeviceAutoConfig(updatedDevice, targetGroupId, newPanels)
        }
    }

    /**
     * Finds (or creates) the group new panels should land in: by exact name if
     * the device declares one, otherwise wherever its previous panels already
     * live (so a device that drops the "group" field doesn't get relocated).
     */
    private fun resolveTargetGroupId(declaredGroupName: String?, device: AutoConfiguredDevice): String? {
        val config = configRepository.config.value
        if (!declaredGroupName.isNullOrBlank()) {
            val existing = config.groups.find { it.name.equals(declaredGroupName, ignoreCase = true) }
            if (existing != null) return existing.id
            val id = UUID.randomUUID().toString()
            configRepository.upsertGroup(PanelGroup(id = id, name = declaredGroupName))
            return id
        }
        val ownedPanelIds = device.createdPanelIds.toSet()
        return config.groups.find { g -> g.panels.any { it.id in ownedPanelIds } }?.id
    }
}
