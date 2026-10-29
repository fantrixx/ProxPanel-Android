package com.proxpanel.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.serverProfilesDataStore by preferencesDataStore(name = "proxpanel_server_profiles")

@Serializable
private data class ServerProfileDto(
    val id: String,
    val label: String,
    val host: String,
    val username: String,
    val realm: String = "pam",
    val verifyTls: Boolean = true,
)

class ServerProfileStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val profiles = stringPreferencesKey("profiles_json")
        val activeId = stringPreferencesKey("active_profile_id")
    }

    val profiles: Flow<List<ServerProfile>> = context.serverProfilesDataStore.data.map { prefs ->
        decode(prefs[Keys.profiles]).map { it.toModel() }
    }

    val activeProfileId: Flow<String?> = context.serverProfilesDataStore.data.map { it[Keys.activeId] }

    suspend fun list(): List<ServerProfile> = profiles.first()

    suspend fun activeId(): String? = activeProfileId.first()

    suspend fun upsertFromLogin(
        host: String,
        username: String,
        realm: String,
        verifyTls: Boolean,
        label: String? = null,
    ): ServerProfile {
        val normalizedHost = host.trim().trimEnd('/')
        val user = username.trim()
        val existing = list()
        val match = existing.firstOrNull {
            it.host.equals(normalizedHost, ignoreCase = true) &&
                it.username.equals(user, ignoreCase = true) &&
                it.realm == realm
        }
        val profile = ServerProfile(
            id = match?.id ?: UUID.randomUUID().toString(),
            label = label?.takeIf { it.isNotBlank() }
                ?: match?.label
                ?: defaultLabel(normalizedHost, user),
            host = normalizedHost,
            username = user,
            realm = realm,
            verifyTls = verifyTls,
        )
        val next = existing.filterNot { it.id == profile.id } + profile
        saveAll(next, activeId = profile.id)
        return profile
    }

    suspend fun setActive(id: String?) {
        context.serverProfilesDataStore.edit { prefs ->
            if (id.isNullOrBlank()) prefs.remove(Keys.activeId) else prefs[Keys.activeId] = id
        }
    }

    suspend fun delete(id: String) {
        val next = list().filterNot { it.id == id }
        val active = activeId()
        saveAll(next, activeId = if (active == id) next.firstOrNull()?.id else active)
    }

    suspend fun rename(id: String, label: String) {
        val trimmed = label.trim()
        if (trimmed.isBlank()) return
        val next = list().map { if (it.id == id) it.copy(label = trimmed) else it }
        saveAll(next, activeId = activeId())
    }

    private suspend fun saveAll(profiles: List<ServerProfile>, activeId: String?) {
        context.serverProfilesDataStore.edit { prefs ->
            prefs[Keys.profiles] = json.encodeToString(profiles.map { it.toDto() })
            if (activeId.isNullOrBlank()) prefs.remove(Keys.activeId) else prefs[Keys.activeId] = activeId
        }
    }

    private fun decode(raw: String?): List<ServerProfileDto> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<ServerProfileDto>>(raw) }.getOrDefault(emptyList())
    }

    private fun ServerProfileDto.toModel() = ServerProfile(
        id = id,
        label = label,
        host = host,
        username = username,
        realm = realm,
        verifyTls = verifyTls,
    )

    private fun ServerProfile.toDto() = ServerProfileDto(
        id = id,
        label = label,
        host = host,
        username = username,
        realm = realm,
        verifyTls = verifyTls,
    )

    companion object {
        fun defaultLabel(host: String, username: String): String {
            val short = host
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
            return "$username@$short"
        }
    }
}
