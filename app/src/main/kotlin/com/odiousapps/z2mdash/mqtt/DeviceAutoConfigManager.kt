package com.odiousapps.z2mdash.mqtt

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.odiousapps.z2mdash.data.AutoConfiguredDevice
import com.odiousapps.z2mdash.data.ConfigRepository
import com.odiousapps.z2mdash.data.PanelGroup
import com.odiousapps.z2mdash.data.PendingAutoConfigDevice
import com.odiousapps.z2mdash.data.SensorDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Keeps every device that was configured via its own "<topic>/app" payload
 * (see SensorDiscovery/DiscoverScreen) up to date - whenever that topic's
 * retained payload changes, this regenerates the device's panels to match,
 * live, without needing go to Discover screen.
 *
 * Also watches every broker's full topic stream (MqttConnectionManager now
 * subscribes every broker to "#" continuously) for brand-new "<topic>/app"
 * topics it hasn't seen before, adds them to a pending list, and notifies the
 * user.
 */
class DeviceAutoConfigManager(
    private val context: Context,
    private val configRepository: ConfigRepository,
    private val connectionManager: MqttConnectionManager
) {
    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            connectionManager.latestPayloads.collect { payloads ->
                reconcileKnownDevices(payloads)
                detectNewDevices(payloads)
            }
        }
    }

    private fun reconcileKnownDevices(payloads: Map<String, String>) {
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
            configRepository.applyDeviceAutoConfig(
                oldPanelIds = device.createdPanelIds.toSet(),
                updatedDevice = updatedDevice,
                targetGroupId = targetGroupId,
                newPanels = newPanels
            )
        }
    }

    /** Scans every topic ending in "/app" for ones not yet tracked, pending, or dismissed. */
    private fun detectNewDevices(payloads: Map<String, String>) {
        val config = configRepository.config.value
        val trackedKeys = config.autoConfiguredDevices.map { "${it.brokerId}|${it.appConfigTopic}" }.toSet()
        val pendingKeys = config.pendingAutoConfigDevices.map { "${it.brokerId}|${it.appConfigTopic}" }.toSet()
        val ignoredKeys = config.ignoredAppConfigTopics.toSet()
        val brokerIds = config.brokers.map { it.id }.toSet()

        payloads.forEach { (compositeKey, payload) ->
            val separatorIndex = compositeKey.indexOf('|')
            if (separatorIndex < 0) return@forEach
            val brokerId = compositeKey.substring(0, separatorIndex)
            val topic = compositeKey.substring(separatorIndex + 1)
            if (brokerId !in brokerIds || !topic.endsWith("/app")) return@forEach

            val key = "$brokerId|$topic"
            if (key in trackedKeys || key in pendingKeys || key in ignoredKeys) return@forEach

            val deviceConfig = SensorDiscovery.parseDeviceAppConfig(payload) ?: return@forEach
            val sensorTopic = topic.removeSuffix("/app")
            val deviceName = deviceConfig.name.ifBlank { sensorTopic.substringAfterLast("/") }

            configRepository.addPendingAutoConfigDevice(
                PendingAutoConfigDevice(
                    brokerId = brokerId,
                    sensorTopic = sensorTopic,
                    appConfigTopic = topic,
                    deviceName = deviceName
                )
            )
            notifyNewDeviceFound(deviceName)
        }
    }

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

    private fun notifyNewDeviceFound(deviceName: String) {
        val channel = NotificationChannel(
            CHANNEL_ID, "New device found", NotificationManager.IMPORTANCE_DEFAULT
        )
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val pendingIntent = PendingIntent.getActivity(
            context, deviceName.hashCode(), launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("New device found")
            .setContentText("$deviceName published its own dashboard config \u2013 open Z2M Dash to add it")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        try {
            NotificationManagerCompat.from(context).notify(deviceName.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check above and this call - safe to ignore,
            // the pending device still shows up as a Home screen banner regardless.
        }
    }

    companion object {
        private const val CHANNEL_ID = "new_device_found"
    }
}
