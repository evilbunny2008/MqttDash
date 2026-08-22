package com.opensource.mqttdash

import android.app.Application
import com.opensource.mqttdash.data.ConfigRepository
import com.opensource.mqttdash.mqtt.MqttConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MqttDashApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob())

    lateinit var configRepository: ConfigRepository
        private set
    lateinit var connectionManager: MqttConnectionManager
        private set

    override fun onCreate() {
        super.onCreate()
        configRepository = ConfigRepository(this)
        connectionManager = MqttConnectionManager(appScope)

        connectionManager.applyConfig(configRepository.config.value)
        appScope.launch {
            configRepository.config.collect { config ->
                connectionManager.applyConfig(config)
            }
        }
    }
}
