package com.opensource.mqttdash

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.opensource.mqttdash.service.MqttForegroundService
import com.opensource.mqttdash.ui.navigation.AppNavHost
import com.opensource.mqttdash.ui.theme.MqttDashTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val app = application as MqttDashApplication
        if (app.configRepository.config.value.backgroundWorkEnabled) {
            ContextCompat.startForegroundService(this, Intent(this, MqttForegroundService::class.java))
        }

        setContent {
            MqttDashTheme {
                AppNavHost()
            }
        }
    }
}
