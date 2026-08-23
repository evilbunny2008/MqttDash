package com.odiousapps.z2mdash

import android.app.Application
import com.odiousapps.z2mdash.data.ConfigRepository
import com.odiousapps.z2mdash.data.PayloadCacheRepository
import com.odiousapps.z2mdash.mqtt.DeviceAutoConfigManager
import com.odiousapps.z2mdash.mqtt.MqttConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class Z2mDashApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob())

    lateinit var configRepository: ConfigRepository
        private set
    lateinit var connectionManager: MqttConnectionManager
        private set
    lateinit var deviceAutoConfigManager: DeviceAutoConfigManager
        private set

    override fun onCreate() {
        super.onCreate()
        configRepository = ConfigRepository(this)
        val payloadCacheRepository = PayloadCacheRepository(this)
        connectionManager = MqttConnectionManager(appScope, payloadCacheRepository)
        deviceAutoConfigManager = DeviceAutoConfigManager(this, configRepository, connectionManager)

        connectionManager.applyConfig(configRepository.config.value)
        connectionManager.startPersistingCache()
        deviceAutoConfigManager.start(appScope)
        appScope.launch {
            configRepository.config.collect { config ->
                connectionManager.applyConfig(config)
            }
        }
    }
}
