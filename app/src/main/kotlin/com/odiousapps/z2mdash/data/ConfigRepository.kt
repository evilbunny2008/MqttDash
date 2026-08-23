package com.odiousapps.z2mdash.data

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
    } catch (_: Exception) {
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
        // Pending/ignored device prompts are scoped to a broker - once it's gone,
        // clear both so the Home screen doesn't keep showing stale "add/ignore"
        // banners (or silently remembering a dismissal) for a broker that no longer exists.
        val prefix = "$id|"
        cfg.copy(
            brokers = cfg.brokers.filterNot { it.id == id },
            pendingAutoConfigDevices = cfg.pendingAutoConfigDevices.filterNot { it.brokerId == id },
            ignoredAppConfigTopics = cfg.ignoredAppConfigTopics.filterNot { it.startsWith(prefix) }
        )
    }

    fun upsertGroup(group: PanelGroup) = update { cfg ->
        val exists = cfg.groups.any { it.id == group.id }
        val newList = if (exists) cfg.groups.map { if (it.id == group.id) group else it }
        else cfg.groups + group
        cfg.copy(groups = newList)
    }

    fun deleteGroup(id: String) = update { cfg ->
        val remainingGroups = cfg.groups.filterNot { it.id == id }
        // A deleted group takes its panels with it. Any autoconfigured device
        // whose panels all lived in that group has nothing left - drop its
        // tracking record too, or the Discover screen would keep that topic
        // hidden forever even though it has no panels any more.
        val remainingPanelIds = remainingGroups.flatMap { it.panels }.map { it.id }.toSet()
        val remainingDevices = cfg.autoConfiguredDevices.filter { device ->
            device.createdPanelIds.any { it in remainingPanelIds }
        }
        cfg.copy(groups = remainingGroups, autoConfiguredDevices = remainingDevices)
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

    /** Repositions a group to the given 1-based index, clamped to the valid range. */
    fun moveGroupToIndex(groupId: String, oneBasedIndex: Int) = update { cfg ->
        val currentIndex = cfg.groups.indexOfFirst { it.id == groupId }
        if (currentIndex < 0) return@update cfg
        val targetIndex = (oneBasedIndex - 1).coerceIn(0, cfg.groups.lastIndex)
        if (targetIndex == currentIndex) return@update cfg
        val reordered = cfg.groups.toMutableList()
        val moved = reordered.removeAt(currentIndex)
        reordered.add(targetIndex, moved)
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
        val updatedGroups = cfg.groups.map { g ->
            if (g.id == groupId) g.copy(panels = g.panels.filterNot { it.id == panelId }) else g
        }
        // Same reasoning as deleteGroup: if that was the last panel an
        // autoconfigured device owned, drop its tracking record too so the
        // Discover screen stops hiding a topic with nothing left to show for it.
        val remainingPanelIds = updatedGroups.flatMap { it.panels }.map { it.id }.toSet()
        val remainingDevices = cfg.autoConfiguredDevices.filter { device ->
            device.createdPanelIds.any { it in remainingPanelIds }
        }
        cfg.copy(groups = updatedGroups, autoConfiguredDevices = remainingDevices)
    }

    /** Removes a whole cluster's panels (e.g. every tile for one device) in a single atomic update. */
    fun removePanels(groupId: String, panelIds: List<String>) = update { cfg ->
        val idsToRemove = panelIds.toSet()
        val updatedGroups = cfg.groups.map { g ->
            if (g.id == groupId) g.copy(panels = g.panels.filterNot { it.id in idsToRemove }) else g
        }
        val remainingPanelIds = updatedGroups.flatMap { it.panels }.map { it.id }.toSet()
        val remainingDevices = cfg.autoConfiguredDevices.filter { device ->
            device.createdPanelIds.any { it in remainingPanelIds }
        }
        cfg.copy(groups = updatedGroups, autoConfiguredDevices = remainingDevices)
    }

    /**
     * Atomically replaces everything owned by an auto-configured device: strips
     * its previous panels ([oldPanelIds] - wherever they currently live, in case
     * the device's declared group changed) out of every group, adds the
     * freshly-built [newPanels] into [targetGroupId], and records/updates the
     * tracking entry ([updatedDevice], whose own createdPanelIds should be the
     * *new* panels' IDs) in one state transition so there's no intermediate
     * inconsistent state.
     */
    fun applyDeviceAutoConfig(
        oldPanelIds: Set<String>,
        updatedDevice: AutoConfiguredDevice,
        targetGroupId: String,
        newPanels: List<Panel>
    ) = update { cfg ->
        val strippedGroups = cfg.groups.map { g ->
            if (oldPanelIds.isEmpty()) g else g.copy(panels = g.panels.filterNot { it.id in oldPanelIds })
        }
        val groupsWithNewPanels = if (strippedGroups.any { it.id == targetGroupId }) {
            strippedGroups.map { g -> if (g.id == targetGroupId) g.copy(panels = g.panels + newPanels) else g }
        } else {
            strippedGroups
        }

        val existingIndex = cfg.autoConfiguredDevices.indexOfFirst {
            it.brokerId == updatedDevice.brokerId && it.appConfigTopic == updatedDevice.appConfigTopic
        }
        val updatedDevices = if (existingIndex >= 0) {
            cfg.autoConfiguredDevices.toMutableList().also { it[existingIndex] = updatedDevice }
        } else {
            cfg.autoConfiguredDevices + updatedDevice
        }

        cfg.copy(groups = groupsWithNewPanels, autoConfiguredDevices = updatedDevices)
    }

    /** Adds a newly-seen "<topic>/app" to the pending list, if not already there. */
    fun addPendingAutoConfigDevice(device: PendingAutoConfigDevice) = update { cfg ->
        val exists = cfg.pendingAutoConfigDevices.any {
            it.brokerId == device.brokerId && it.appConfigTopic == device.appConfigTopic
        }
        if (exists) cfg else cfg.copy(pendingAutoConfigDevices = cfg.pendingAutoConfigDevices + device)
    }

    fun removePendingAutoConfigDevice(brokerId: String, appConfigTopic: String) = update { cfg ->
        cfg.copy(
            pendingAutoConfigDevices = cfg.pendingAutoConfigDevices.filterNot {
                it.brokerId == brokerId && it.appConfigTopic == appConfigTopic
            }
        )
    }

    /** User declined a pending device - drop it from the pending list and remember not to re-prompt. */
    fun ignoreAppConfigTopic(brokerId: String, appConfigTopic: String) = update { cfg ->
        val key = "$brokerId|$appConfigTopic"
        cfg.copy(
            ignoredAppConfigTopics = if (key in cfg.ignoredAppConfigTopics) {
                cfg.ignoredAppConfigTopics
            } else {
                cfg.ignoredAppConfigTopics + key
            },
            pendingAutoConfigDevices = cfg.pendingAutoConfigDevices.filterNot {
                it.brokerId == brokerId && it.appConfigTopic == appConfigTopic
            }
        )
    }

    fun exportJson(): String = json.encodeToString(AppConfig.serializer(), _config.value)

    fun importJson(text: String) {
        val imported = json.decodeFromString(AppConfig.serializer(), text)
        persist(imported)
        _config.value = imported
    }
}
