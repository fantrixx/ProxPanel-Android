package com.proxpanel.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.prefsDataStore by preferencesDataStore(name = "proxpanel_prefs")

enum class AccentChoice(val id: String) {
    Orange("orange"),
    Blue("blue"),
    Teal("teal"),
    Magenta("magenta"),
    ;

    companion object {
        fun fromId(id: String?): AccentChoice =
            entries.firstOrNull { it.id == id } ?: Orange
    }
}

enum class NodesViewMode(val id: String) {
    Cards("cards"),
    Grid("grid"),
    Compact("compact"),
    ;

    companion object {
        fun fromId(id: String?): NodesViewMode =
            entries.firstOrNull { it.id == id } ?: Cards
    }
}

/** How often the Overview nodes list auto-refreshes. 0 = manual only. */
enum class RefreshInterval(val id: String, val millis: Long) {
    Fast("2s", 2_000L),
    Default("3s", 3_000L),
    Relaxed("5s", 5_000L),
    Calm("10s", 10_000L),
    Slow("30s", 30_000L),
    Manual("off", 0L),
    ;

    companion object {
        fun fromId(id: String?): RefreshInterval =
            entries.firstOrNull { it.id == id } ?: Default
    }
}

class PrefsStore(context: Context) {
    private val ds = context.applicationContext.prefsDataStore
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val pinnedGuests = stringSetPreferencesKey("pinned_guests")
        val lastBackupStorage = stringPreferencesKey("last_backup_storage")
        val accent = stringPreferencesKey("accent_choice")
        val nodesView = stringPreferencesKey("nodes_view_mode")
        val refreshInterval = stringPreferencesKey("refresh_interval")
        val guestIcons = stringPreferencesKey("guest_icons")
        val clusterDashboardExpanded = booleanPreferencesKey("cluster_dashboard_expanded")
    }

    val pinnedGuestKeys: Flow<Set<String>> = ds.data.map { it[Keys.pinnedGuests].orEmpty() }

    val lastBackupStorage: Flow<String?> = ds.data.map { it[Keys.lastBackupStorage] }

    val accentChoice: Flow<AccentChoice> = ds.data.map { AccentChoice.fromId(it[Keys.accent]) }

    val nodesViewMode: Flow<NodesViewMode> = ds.data.map { NodesViewMode.fromId(it[Keys.nodesView]) }

    val refreshInterval: Flow<RefreshInterval> =
        ds.data.map { RefreshInterval.fromId(it[Keys.refreshInterval]) }

    /** Cluster overview panel on Guests; default collapsed to keep the list visible. */
    val clusterDashboardExpanded: Flow<Boolean> =
        ds.data.map { it[Keys.clusterDashboardExpanded] ?: false }

    val guestIcons: Flow<Map<String, GuestIconRecord>> = ds.data.map { prefs ->
        val raw = prefs[Keys.guestIcons] ?: return@map emptyMap()
        runCatching { json.decodeFromString<Map<String, GuestIconRecord>>(raw) }.getOrDefault(emptyMap())
    }

    suspend fun togglePinned(guestKey: String) {
        ds.edit { prefs ->
            val cur = prefs[Keys.pinnedGuests].orEmpty().toMutableSet()
            if (!cur.add(guestKey)) cur.remove(guestKey)
            prefs[Keys.pinnedGuests] = cur
        }
    }

    suspend fun setLastBackupStorage(storage: String) {
        if (storage.isBlank()) return
        ds.edit { it[Keys.lastBackupStorage] = storage }
    }

    suspend fun setAccentChoice(choice: AccentChoice) {
        ds.edit { it[Keys.accent] = choice.id }
    }

    suspend fun setNodesViewMode(mode: NodesViewMode) {
        ds.edit { it[Keys.nodesView] = mode.id }
    }

    suspend fun setRefreshInterval(interval: RefreshInterval) {
        ds.edit { it[Keys.refreshInterval] = interval.id }
    }

    suspend fun setClusterDashboardExpanded(expanded: Boolean) {
        ds.edit { it[Keys.clusterDashboardExpanded] = expanded }
    }

    suspend fun setGuestIcon(key: String, record: GuestIconRecord?) {
        ds.edit { prefs ->
            val cur = prefs[Keys.guestIcons]?.let { raw ->
                runCatching { json.decodeFromString<MutableMap<String, GuestIconRecord>>(raw) }
                    .getOrDefault(mutableMapOf())
            } ?: mutableMapOf()
            if (record == null) {
                cur.remove(key)
            } else {
                cur[key] = record
            }
            if (cur.isEmpty()) {
                prefs.remove(Keys.guestIcons)
            } else {
                prefs[Keys.guestIcons] = json.encodeToString(cur)
            }
        }
    }
}
