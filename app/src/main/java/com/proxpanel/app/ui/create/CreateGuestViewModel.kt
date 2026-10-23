package com.proxpanel.app.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.proxpanel.app.R
import com.proxpanel.app.data.ApiClient
import com.proxpanel.app.util.L10n
import com.proxpanel.app.data.CreateGuestResult
import com.proxpanel.app.data.DiskStorage
import com.proxpanel.app.data.MediaItem
import com.proxpanel.app.data.NodeBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CreateGuestType { Lxc, Qemu }

data class CreateGuestUiState(
    val type: CreateGuestType = CreateGuestType.Lxc,
    val name: String = "",
    val vmid: String = "",
    val node: String = "",
    val nodes: List<String> = emptyList(),
    val cores: String = "2",
    val memory: String = "2048",
    val swap: String = "512",
    val diskGiB: String = "8",
    val storage: String = "",
    val storages: List<DiskStorage> = emptyList(),
    val bridge: String = "vmbr0",
    val bridges: List<NodeBridge> = emptyList(),
    val ostemplate: String = "",
    val templates: List<MediaItem> = emptyList(),
    val iso: String = "",
    val isos: List<MediaItem> = emptyList(),
    val password: String = "",
    val showPassword: Boolean = false,
    val startAfter: Boolean = true,
    val unprivileged: Boolean = true,
    val showAdvanced: Boolean = false,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val created: CreateGuestResult? = null,
)

class CreateGuestViewModel(
    private val api: ApiClient,
    private val l10n: L10n,
    initialType: CreateGuestType = CreateGuestType.Lxc,
) : ViewModel() {
    private val _state = MutableStateFlow(
        CreateGuestUiState(
            type = initialType,
            memory = if (initialType == CreateGuestType.Lxc) "2048" else "4096",
            diskGiB = if (initialType == CreateGuestType.Lxc) "8" else "32",
        ),
    )
    val state: StateFlow<CreateGuestUiState> = _state.asStateFlow()

    init {
        bootstrap()
    }

    fun setType(type: CreateGuestType) {
        if (type == _state.value.type) return
        _state.update {
            it.copy(
                type = type,
                cores = "2",
                memory = if (type == CreateGuestType.Lxc) "2048" else "4096",
                diskGiB = if (type == CreateGuestType.Lxc) "8" else "32",
                storage = "",
                ostemplate = "",
                iso = "",
                password = "",
                error = null,
            )
        }
        loadTypeDependent()
    }

    fun patch(
        name: String? = null,
        vmid: String? = null,
        node: String? = null,
        cores: String? = null,
        memory: String? = null,
        swap: String? = null,
        diskGiB: String? = null,
        storage: String? = null,
        bridge: String? = null,
        ostemplate: String? = null,
        iso: String? = null,
        password: String? = null,
        showPassword: Boolean? = null,
        startAfter: Boolean? = null,
        unprivileged: Boolean? = null,
        showAdvanced: Boolean? = null,
    ) {
        val nodeChanged = node != null && node != _state.value.node
        _state.update {
            it.copy(
                name = name ?: it.name,
                vmid = vmid ?: it.vmid,
                node = node ?: it.node,
                cores = cores ?: it.cores,
                memory = memory ?: it.memory,
                swap = swap ?: it.swap,
                diskGiB = diskGiB ?: it.diskGiB,
                storage = storage ?: it.storage,
                bridge = bridge ?: it.bridge,
                ostemplate = ostemplate ?: it.ostemplate,
                iso = iso ?: it.iso,
                password = password ?: it.password,
                showPassword = showPassword ?: it.showPassword,
                startAfter = startAfter ?: it.startAfter,
                unprivileged = unprivileged ?: it.unprivileged,
                showAdvanced = showAdvanced ?: it.showAdvanced,
                error = null,
            )
        }
        if (nodeChanged) loadBridgesAndFilterStorage()
    }

    fun generatePassword() {
        val chars = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#$%"
        val pw = (1..16).map { chars.random() }.joinToString("")
        _state.update { it.copy(password = pw, showPassword = true) }
    }

    fun clearCreated() = _state.update { it.copy(created = null) }

    fun create() {
        val s = _state.value
        viewModelScope.launch {
            val vmid = s.vmid.toIntOrNull()
            val cores = s.cores.toIntOrNull()
            val memory = s.memory.toIntOrNull()
            val disk = s.diskGiB.toIntOrNull()
            when {
                s.node.isBlank() -> {
                    _state.update { it.copy(error = l10n.get(R.string.error_select_node)) }
                    return@launch
                }
                vmid == null || vmid < 100 -> {
                    _state.update { it.copy(error = l10n.get(R.string.error_vmid_min)) }
                    return@launch
                }
                s.name.isBlank() -> {
                    _state.update { it.copy(error = l10n.get(R.string.error_name_required)) }
                    return@launch
                }
                s.storage.isBlank() -> {
                    _state.update { it.copy(error = l10n.get(R.string.error_select_disk_storage)) }
                    return@launch
                }
                cores == null || memory == null || disk == null -> {
                    _state.update { it.copy(error = l10n.get(R.string.error_check_resources)) }
                    return@launch
                }
                s.type == CreateGuestType.Lxc && s.ostemplate.isBlank() -> {
                    _state.update { it.copy(error = l10n.get(R.string.error_select_os_template)) }
                    return@launch
                }
                s.type == CreateGuestType.Lxc && s.password.length < 5 -> {
                    _state.update { it.copy(error = l10n.get(R.string.error_password_min)) }
                    return@launch
                }
            }

            _state.update { it.copy(busy = true, error = null) }
            try {
                val result = if (s.type == CreateGuestType.Lxc) {
                    api.createLxc(
                        node = s.node,
                        vmid = vmid!!,
                        hostname = s.name.trim(),
                        ostemplate = s.ostemplate,
                        password = s.password,
                        storage = s.storage,
                        diskGiB = disk!!,
                        cores = cores!!,
                        memoryMb = memory!!,
                        swapMb = s.swap.toIntOrNull() ?: 512,
                        bridge = s.bridge.ifBlank { "vmbr0" },
                        unprivileged = s.unprivileged,
                        start = s.startAfter,
                    )
                } else {
                    api.createQemu(
                        node = s.node,
                        vmid = vmid!!,
                        name = s.name.trim(),
                        storage = s.storage,
                        diskGiB = disk!!,
                        cores = cores!!,
                        memoryMb = memory!!,
                        bridge = s.bridge.ifBlank { "vmbr0" },
                        iso = s.iso.ifBlank { null },
                        start = s.startAfter,
                    )
                }
                _state.update { it.copy(busy = false, created = result) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: l10n.get(R.string.error_create_failed)) }
            }
        }
    }

    private fun bootstrap() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val nodes = api.resources().resources
                    .mapNotNull { if (it.type == "node") it.node else null }
                    .distinct()
                    .sorted()
                val next = runCatching { api.nextId() }.getOrNull()
                val node = _state.value.node.ifBlank { nodes.firstOrNull().orEmpty() }
                _state.update {
                    it.copy(
                        nodes = nodes,
                        node = node,
                        vmid = it.vmid.ifBlank { next?.toString().orEmpty() },
                        loading = false,
                    )
                }
                loadTypeDependent()
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    private fun loadTypeDependent() {
        viewModelScope.launch {
            val s = _state.value
            val content = if (s.type == CreateGuestType.Lxc) "rootdir" else "images"
            try {
                val storages = api.diskStorages(content)
                val filtered = storages.filter { it.node == s.node || it.shared }
                val templates = if (s.type == CreateGuestType.Lxc) {
                    api.mediaItems("vztmpl")
                } else {
                    emptyList()
                }
                val isos = if (s.type == CreateGuestType.Qemu) {
                    api.mediaItems("iso")
                } else {
                    emptyList()
                }
                val bridges = if (s.node.isNotBlank()) {
                    runCatching { api.nodeBridges(s.node) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                _state.update {
                    it.copy(
                        storages = filtered.ifEmpty { storages },
                        storage = it.storage.ifBlank {
                            (filtered.firstOrNull() ?: storages.firstOrNull())?.storage.orEmpty()
                        },
                        templates = templates,
                        ostemplate = it.ostemplate.ifBlank { templates.firstOrNull()?.volid.orEmpty() },
                        isos = isos,
                        bridges = bridges,
                        bridge = it.bridge.ifBlank {
                            bridges.find { b -> b.iface == "vmbr0" }?.iface
                                ?: bridges.firstOrNull()?.iface
                                ?: "vmbr0"
                        },
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    private fun loadBridgesAndFilterStorage() {
        viewModelScope.launch {
            val s = _state.value
            val content = if (s.type == CreateGuestType.Lxc) "rootdir" else "images"
            val storages = runCatching { api.diskStorages(content) }.getOrDefault(emptyList())
            val filtered = storages.filter { it.node == s.node || it.shared }
            val bridges = runCatching { api.nodeBridges(s.node) }.getOrDefault(emptyList())
            _state.update {
                val storageOk = filtered.any { st -> st.storage == it.storage }
                it.copy(
                    storages = filtered.ifEmpty { storages },
                    storage = if (storageOk) {
                        it.storage
                    } else {
                        (filtered.firstOrNull() ?: storages.firstOrNull())?.storage.orEmpty()
                    },
                    bridges = bridges,
                    bridge = bridges.find { b -> b.iface == it.bridge }?.iface
                        ?: bridges.find { b -> b.iface == "vmbr0" }?.iface
                        ?: bridges.firstOrNull()?.iface
                        ?: it.bridge.ifBlank { "vmbr0" },
                )
            }
        }
    }

    companion object {
        fun factory(api: ApiClient, l10n: L10n, initialType: CreateGuestType = CreateGuestType.Lxc) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CreateGuestViewModel(api, l10n, initialType) as T
            }
    }
}
