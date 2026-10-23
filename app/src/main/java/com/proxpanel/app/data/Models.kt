package com.proxpanel.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AuthUser(
    val username: String,
    val host: String,
    val authKind: String? = null,
)

@Serializable
data class ClusterResource(
    val id: String,
    val type: String,
    val node: String? = null,
    val vmid: Int? = null,
    val name: String? = null,
    val status: String? = null,
    val cpu: Double? = null,
    val maxcpu: Double? = null,
    val mem: Long? = null,
    val maxmem: Long? = null,
    val disk: Long? = null,
    val maxdisk: Long? = null,
    val uptime: Long? = null,
    val netin: Long? = null,
    val netout: Long? = null,
    val diskread: Long? = null,
    val diskwrite: Long? = null,
    val template: Int? = null,
    val ips: List<String>? = null,
    val lock: String? = null,
    val qmpstatus: String? = null,
    val storage: String? = null,
    val content: String? = null,
    val shared: Int? = null,
    val plugintype: String? = null,
    val tags: String? = null,
)

@Serializable
data class ResourcesResponse(
    val resources: List<ClusterResource> = emptyList(),
    val version: ProxmoxVersion? = null,
)

@Serializable
data class ProxmoxVersion(
    val version: String? = null,
    val release: String? = null,
)

@Serializable
data class GuestStatus(
    val status: String,
    val name: String? = null,
    val vmid: Int? = null,
    val cpu: Double? = null,
    val cpus: Double? = null,
    val mem: Long? = null,
    val maxmem: Long? = null,
    val disk: Long? = null,
    val maxdisk: Long? = null,
    val uptime: Long? = null,
    val lock: String? = null,
    val qmpstatus: String? = null,
    val swap: Long? = null,
    val maxswap: Long? = null,
)

@Serializable
data class GuestDetail(
    val status: GuestStatus,
    val config: Map<String, JsonElement> = emptyMap(),
    val ips: List<String> = emptyList(),
)

@Serializable
data class Snapshot(
    val name: String,
    val description: String? = null,
    val snaptime: Long? = null,
    val parent: String? = null,
    val vmstate: Int? = null,
)

@Serializable
data class PveTask(
    val upid: String,
    val node: String? = null,
    val type: String? = null,
    val status: String? = null,
    val user: String? = null,
    val starttime: Long? = null,
    val endtime: Long? = null,
    val id: String? = null,
)

@Serializable
data class MediaItem(
    val node: String,
    val storage: String,
    val volid: String,
    val size: Long? = null,
    val ctime: Long? = null,
    val format: String? = null,
    val notes: String? = null,
    val vmid: Int? = null,
    val content: String? = null,
)

data class BackupStorage(
    val node: String,
    val storage: String,
    val shared: Boolean = false,
)

data class BackupOverviewGuest(
    val node: String,
    val type: String,
    val vmid: Int,
    val name: String,
    val status: String?,
    val backupCount: Int,
    val lastBackup: MediaItem?,
)

data class NodeBridge(
    val iface: String,
    val active: Boolean = true,
)

data class DiskStorage(
    val node: String,
    val storage: String,
    val shared: Boolean = false,
)

data class CreateGuestResult(
    val type: String,
    val node: String,
    val vmid: Int,
    val upid: String? = null,
)

@Serializable
data class OkResponse(
    val ok: Boolean = true,
    val upid: String? = null,
)

@Serializable
data class TaskStatus(
    val status: String? = null,
    val exitstatus: String? = null,
    val type: String? = null,
    val id: String? = null,
)

data class GuestRates(
    val netin: Double = 0.0,
    val netout: Double = 0.0,
    val diskread: Double = 0.0,
    val diskwrite: Double = 0.0,
)

@Serializable
data class ErrorBody(
    val error: String? = null,
)

data class LoginPrefs(
    val proxmoxHost: String = "",
    val username: String = "",
    val realm: String = "pam",
    val verifyTls: Boolean = false,
    val rememberCredentials: Boolean = true,
)

data class GuestItem(
    val node: String,
    val type: String,
    val vmid: Int,
    val name: String,
    val status: String,
    val cpu: Double?,
    val mem: Long?,
    val maxmem: Long?,
    val disk: Long?,
    val maxdisk: Long?,
    val uptime: Long?,
    val ips: List<String>,
    val lock: String?,
    val qmpstatus: String? = null,
    val netin: Long? = null,
    val netout: Long? = null,
    val diskread: Long? = null,
    val diskwrite: Long? = null,
    val resourceId: String = "",
    val tags: String? = null,
) {
    val isRunning: Boolean
        get() = status.equals("running", ignoreCase = true)

    val kindLabel: String
        get() = if (type == "lxc") "CT" else "VM"

    val key: String
        get() = "$node/$vmid"
}

data class MarketplaceScript(
    val slug: String,
    val name: String,
    val description: String,
    val kind: String,
    val categories: List<String>,
    val command: String,
    val pageUrl: String,
    val installs: Int = 0,
)

fun ClusterResource.toGuestOrNull(): GuestItem? {
    if (type != "lxc" && type != "qemu") return null
    if (template == 1) return null
    val node = node ?: return null
    val vmid = vmid ?: id.substringAfterLast('/').toIntOrNull() ?: return null
    return GuestItem(
        node = node,
        type = type,
        vmid = vmid,
        name = name?.ifBlank { null } ?: "Guest $vmid",
        status = status ?: "unknown",
        cpu = cpu,
        mem = mem,
        maxmem = maxmem,
        disk = disk,
        maxdisk = maxdisk,
        uptime = uptime,
        ips = ips.orEmpty(),
        lock = lock,
        qmpstatus = qmpstatus,
        netin = netin,
        netout = netout,
        diskread = diskread,
        diskwrite = diskwrite,
        resourceId = id.ifBlank { "$type/$vmid" },
        tags = tags,
    )
}

fun ClusterResource.toNodeOrNull(): ClusterResource? =
    takeIf { type == "node" && !node.isNullOrBlank() }

fun ClusterResource.toStorageOrNull(): ClusterResource? =
    takeIf { type == "storage" && !storage.isNullOrBlank() && !node.isNullOrBlank() }
