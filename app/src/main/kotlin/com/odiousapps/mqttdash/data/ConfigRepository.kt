package com.odiousapps.mqttdash.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists the whole app configuration (brokers, groups, panels) as a single
 * plain-JSON file under the app's private storage. Also doubles as the export
 * format for "Configuration Backup" / "Configuration Recovery" in Settings -
 * it's just JSON you own, no proprietary/encrypted format, no lock-in.
 */
class ConfigRepository(private val context: Context) {

    private val file: File get() = File(context.filesDir, "config.json")
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val _config = MutableStateFlow(load())
    val config: StateFlow<AppConfig> = _config

    private fun load(): AppConfig = try {
        if (file.exists()) json.decodeFromString(AppConfig.serializer(), file.readText())
        else AppConfig()
    } catch (e: Exception) {
        AppConfig()
    }

    private fun persist(config: AppConfig) {
        file.writeText(json.encodeToString(AppConfig.serializer(), config))
    }

    fun update(transform: (AppConfig) -> AppConfig) {
        _config.update { current ->
            val next = transform(current)
            persist(next)
            next
        }
    }

    fun upsertBroker(broker: Broker) = update { cfg ->
        val exists = cfg.brokers.any { it.id == broker.id }
        val newList = if (exists) cfg.brokers.map { if (it.id == broker.id) broker else it }
        else cfg.brokers + broker
        cfg.copy(brokers = newList)
    }

    fun deleteBroker(id: String) = update { cfg ->
        cfg.copy(brokers = cfg.brokers.filterNot { it.id == id })
    }

    fun upsertGroup(group: PanelGroup) = update { cfg ->
        val exists = cfg.groups.any { it.id == group.id }
        val newList = if (exists) cfg.groups.map { if (it.id == group.id) group else it }
        else cfg.groups + group
        cfg.copy(groups = newList)
    }

    fun deleteGroup(id: String) = update { cfg ->
        cfg.copy(groups = cfg.groups.filterNot { it.id == id })
    }

    /** Moves a group earlier (offset -1) or later (offset +1) in the display order. */
    fun moveGroup(groupId: String, offset: Int) = update { cfg ->
        val index = cfg.groups.indexOfFirst { it.id == groupId }
        if (index < 0) return@update cfg
        val newIndex = (index + offset).coerceIn(0, cfg.groups.lastIndex)
        if (newIndex == index) return@update cfg
        val reordered = cfg.groups.toMutableList()
        val moved = reordered.removeAt(index)
        reordered.add(newIndex, moved)
        cfg.copy(groups = reordered)
    }

    fun setGroupCollapsed(groupId: String, collapsed: Boolean) = update { cfg ->
        cfg.copy(groups = cfg.groups.map { if (it.id == groupId) it.copy(collapsed = collapsed) else it })
    }

    fun addPanelToGroup(groupId: String, panel: Panel) = update { cfg ->
        cfg.copy(groups = cfg.groups.map { g ->
            if (g.id == groupId) g.copy(panels = g.panels + panel) else g
        })
    }

    fun updatePanel(groupId: String, panel: Panel) = update { cfg ->
        cfg.copy(groups = cfg.groups.map { g ->
            if (g.id == groupId) g.copy(panels = g.panels.map { if (it.id == panel.id) panel else it }) else g
        })
    }

    fun removePanel(groupId: String, panelId: String) = update { cfg ->
        cfg.copy(groups = cfg.groups.map { g ->
            if (g.id == groupId) g.copy(panels = g.panels.filterNot { it.id == panelId }) else g
        })
    }

    fun exportJson(): String = json.encodeToString(AppConfig.serializer(), _config.value)

    fun importJson(text: String) {
        val imported = json.decodeFromString(AppConfig.serializer(), text)
        persist(imported)
        _config.value = imported
    }
}
