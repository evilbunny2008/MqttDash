package com.odiousapps.mqttdash.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.odiousapps.mqttdash.MqttDashApplication

/**
 * Keeps broker connections alive while the app is backgrounded. Toggled by
 * "Background Work" in Settings; MainActivity starts this when that setting
 * is enabled.
 */
class MqttForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        val app = application as MqttDashApplication
        app.connectionManager.applyConfig(app.configRepository.config.value)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "MQTT connection", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MQTT Dash")
            .setContentText("Maintaining broker connections")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "mqtt_connection"
        private const val NOTIFICATION_ID = 1
    }
}
