package com.proxpanel.app.ui.marketplace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.proxpanel.app.R
import com.proxpanel.app.data.MarketplaceScript
import com.proxpanel.app.data.NodesViewMode
import com.proxpanel.app.data.PrefsStore
import com.proxpanel.app.util.L10n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class MarketplaceUiState(
    val scripts: List<MarketplaceScript> = emptyList(),
    val query: String = "",
    val viewMode: NodesViewMode = NodesViewMode.Cards,
    val loading: Boolean = true,
    val error: String? = null,
)

class MarketplaceViewModel(
    private val l10n: L10n,
    private val prefsStore: PrefsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(MarketplaceUiState())
    val state: StateFlow<MarketplaceUiState> = _state.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    init {
        viewModelScope.launch {
            prefsStore.nodesViewMode.collect { mode ->
                _state.update { it.copy(viewMode = mode) }
            }
        }
        refresh()
    }

    fun setViewMode(mode: NodesViewMode) {
        viewModelScope.launch { prefsStore.setNodesViewMode(mode) }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun filtered(): List<MarketplaceScript> {
        val q = _state.value.query.trim().lowercase()
        val all = _state.value.scripts
        if (q.isEmpty()) return all
        return all.filter {
            it.name.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.slug.contains(q) ||
                it.categories.any { c -> c.lowercase().contains(q) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val scripts = withContext(Dispatchers.IO) { fetchCatalog() }
                _state.update { it.copy(scripts = scripts, loading = false, error = null) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = e.message ?: l10n.get(R.string.error_marketplace_load))
                }
            }
        }
    }

    /**
     * Same PocketBase catalog the ProxPanel web app uses
     * (`https://db.community-scripts.org/api/collections/script_scripts/records`).
     */
    private fun fetchCatalog(): List<MarketplaceScript> {
        val scripts = mutableListOf<MarketplaceScript>()
        var page = 1
        var totalPages = 1
        while (page <= totalPages) {
            val url = "https://db.community-scripts.org/api/collections/script_scripts/records".toHttpUrl()
                .newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("perPage", "200")
                .addQueryParameter("filter", "is_deleted=false&&is_disabled=false")
                .addQueryParameter("expand", "type,categories")
                .addQueryParameter("sort", "name")
                .build()
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "ProxPanel-Android")
                .build()
            http.newCall(req).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Marketplace HTTP ${response.code}")
                }
                val root = json.parseToJsonElement(text).jsonObject
                totalPages = root["totalPages"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
                val items = root["items"]?.jsonArray ?: JsonArray(emptyList())
                items.forEach { el ->
                    mapScript(el as? JsonObject ?: return@forEach)?.let { scripts += it }
                }
            }
            page++
            if (page > 30) break // safety
        }
        return scripts.sortedBy { it.name.lowercase() }
    }

    private fun mapScript(rec: JsonObject): MarketplaceScript? {
        if (rec.bool("is_deleted") || rec.bool("is_disabled")) return null
        val slug = rec.string("slug")?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        val expand = rec["expand"] as? JsonObject
        val typeRec = expand?.get("type") as? JsonObject
        val kindRaw = typeRec?.string("type")?.lowercase().orEmpty()
        val kind = when (kindRaw) {
            "vm" -> "vm"
            "pve" -> "pve"
            "addon" -> "addon"
            "turnkey" -> "turnkey"
            "ct", "lxc" -> "lxc"
            else -> "lxc"
        }

        val methods = rec["install_methods"] as? JsonArray ?: JsonArray(emptyList())
        var scriptPath: String? = null
        for (method in methods) {
            val m = method as? JsonObject ?: continue
            val t = m.string("type")?.lowercase().orEmpty()
            if (t == "alpine") continue
            val provided = m.string("script").orEmpty()
            scriptPath = provided.ifBlank { defaultScriptPath(kind, slug) }
            break
        }
        if (scriptPath.isNullOrBlank()) {
            scriptPath = defaultScriptPath(kind, slug)
        }
        if (!isAllowedScriptPath(scriptPath)) return null

        val categories = mutableListOf<String>()
        val catRaw = expand?.get("categories")
        val catList = catRaw as? JsonArray ?: JsonArray(emptyList())
        catList.forEach { cat ->
            val c = cat as? JsonObject ?: return@forEach
            c.string("name")?.let { categories += it }
        }

        val isDev = rec.bool("is_dev")
        val rawBase = if (isDev) {
            "https://raw.githubusercontent.com/community-scripts/ProxmoxVED/main"
        } else {
            "https://raw.githubusercontent.com/community-scripts/ProxmoxVE/main"
        }
        val command = "bash -c \"\$(curl -fsSL $rawBase/$scriptPath)\""

        return MarketplaceScript(
            slug = slug,
            name = rec.string("name") ?: slug,
            description = rec.string("description").orEmpty(),
            kind = kind,
            categories = categories,
            command = command,
            pageUrl = "https://community-scripts.org/scripts/$slug",
            installs = 0,
        )
    }

    private fun defaultScriptPath(kind: String, slug: String): String {
        val dir = when (kind) {
            "vm" -> "vm"
            "pve" -> "tools/pve"
            "addon" -> "tools/addon"
            "turnkey" -> "turnkey"
            else -> "ct"
        }
        return "$dir/$slug.sh"
    }

    private fun isAllowedScriptPath(path: String): Boolean {
        if (path.isBlank() || path.contains("..") || path.startsWith("/")) return false
        return path.endsWith(".sh") && (
            path.startsWith("ct/") ||
                path.startsWith("vm/") ||
                path.startsWith("tools/") ||
                path.startsWith("turnkey/")
            )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.bool(key: String): Boolean {
        val p = this[key] as? JsonPrimitive ?: return false
        return p.content.equals("true", ignoreCase = true)
    }

    companion object {
        fun factory(l10n: L10n, prefsStore: PrefsStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MarketplaceViewModel(l10n, prefsStore) as T
        }
    }
}
