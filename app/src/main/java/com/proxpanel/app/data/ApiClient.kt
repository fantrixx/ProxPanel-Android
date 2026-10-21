package com.proxpanel.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class ApiException(val status: Int, message: String) : Exception(message)

@Serializable
private data class PveEnvelope<T>(val data: T? = null)

@Serializable
private data class PveTicketData(
    val ticket: String,
    @SerialName("CSRFPreventionToken") val csrf: String,
    val username: String,
)

class ApiClient(
    private val sessionStore: SessionStore,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val cookieJar = object : CookieJar {
        private val store = mutableMapOf<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val existing = store.getOrPut(url.host) { mutableListOf() }
            cookies.forEach { cookie ->
                existing.removeAll { it.name == cookie.name }
                existing += cookie
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host].orEmpty()

        fun clear() = store.clear()

        fun setTicket(url: HttpUrl, ticket: String) {
            // hostOnlyDomain is required for IP hosts; domain() cookies often never get sent.
            val cookie = Cookie.Builder()
                .name("PVEAuthCookie")
                .value(ticket)
                .hostOnlyDomain(url.host)
                .path("/")
                .build()
            store[url.host] = mutableListOf(cookie)
        }
    }

    @Volatile
    private var baseUrl: String = ""

    @Volatile
    private var authTicket: String = ""

    @Volatile
    private var csrfToken: String = ""

    @Volatile
    private var username: String = ""

    @Volatile
    private var client: OkHttpClient = buildClient(verifyTls = false)

    fun configure(
        baseUrl: String,
        verifyTls: Boolean,
        ticket: String? = null,
        csrf: String? = null,
        username: String? = null,
    ) {
        this.baseUrl = baseUrl.trim().trimEnd('/')
        this.authTicket = ticket.orEmpty()
        this.csrfToken = csrf.orEmpty()
        this.username = username.orEmpty()
        client = buildClient(verifyTls)
        cookieJar.clear()
        val url = this.baseUrl.toHttpUrlOrNull()
        if (!ticket.isNullOrBlank() && url != null) {
            cookieJar.setTicket(url, ticket)
        }
    }

    fun clearSessionLocal() {
        cookieJar.clear()
        authTicket = ""
        csrfToken = ""
        username = ""
    }

    private fun buildClient(verifyTls: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .followRedirects(false)

        if (!verifyTls) {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val ssl = SSLContext.getInstance("TLS")
            ssl.init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
            builder.sslSocketFactory(ssl.socketFactory, trustAll)
            builder.hostnameVerifier { _, _ -> true }
        }
        return builder.build()
    }

    suspend fun login(
        proxmoxHost: String,
        username: String,
        password: String,
        realm: String,
        verifyTls: Boolean,
    ): AuthUser = withContext(Dispatchers.IO) {
        configure(proxmoxHost, verifyTls)
        val userAtRealm = if (username.contains('@')) username else "$username@$realm"
        val body = FormBody.Builder()
            .add("username", userAtRealm)
            .add("password", password)
            .build()
        val request = Request.Builder()
            .url("$baseUrl/api2/json/access/ticket")
            .post(body)
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(response.code, extractError(text) ?: "Login failed (HTTP ${response.code})")
            }
            val ticket = json.decodeFromString<PveEnvelope<PveTicketData>>(text).data
                ?: throw ApiException(response.code, "Empty login response")
            val url = baseUrl.toHttpUrlOrNull()
                ?: throw ApiException(0, "Invalid Proxmox URL")
            cookieJar.setTicket(url, ticket.ticket)
            authTicket = ticket.ticket
            csrfToken = ticket.csrf
            this@ApiClient.username = ticket.username
            sessionStore.saveAuth(
                ticket = ticket.ticket,
                csrf = ticket.csrf,
                username = ticket.username,
            )
            AuthUser(username = ticket.username, host = baseUrl, authKind = "ticket")
        }
    }

    suspend fun me(): AuthUser = withContext(Dispatchers.IO) {
        // /version is public — use an authenticated endpoint for the session check.
        requestRaw("GET", "/api2/json/nodes")
        AuthUser(
            username = username.ifBlank { "user" },
            host = baseUrl,
            authKind = "ticket",
        )
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        clearSessionLocal()
        sessionStore.clearSession()
    }

    suspend fun resources(): ResourcesResponse = withContext(Dispatchers.IO) {
        // Match ProxPanel web: full /cluster/resources, plus node guest fallback.
        val errors = mutableListOf<String>()
        val fromCluster = runCatching {
            parseClusterResources(requestRaw("GET", "/api2/json/cluster/resources"))
        }.onFailure { errors += (it.message ?: it.toString()) }
            .getOrDefault(emptyList())

        val guestsFromNodes = runCatching { loadGuestsFromNodes() }
            .onFailure { errors += (it.message ?: it.toString()) }
            .getOrDefault(emptyList())

        val merged = LinkedHashMap<String, ClusterResource>()
        for (row in fromCluster) {
            merged[row.id] = row
        }
        for (row in guestsFromNodes) {
            val key = "${row.type}/${row.vmid}"
            if (!merged.containsKey(key) && !merged.containsKey("${row.type}/${row.node}/${row.vmid}")) {
                merged[key] = row
            }
        }

        if (merged.isEmpty() && errors.isNotEmpty()) {
            throw ApiException(0, errors.first())
        }

        val version = runCatching {
            decodeDataObject<ProxmoxVersion>(requestRaw("GET", "/api2/json/version"))
        }.getOrNull()

        ResourcesResponse(resources = merged.values.toList(), version = version)
    }

    suspend fun guest(node: String, type: String, vmid: Int): GuestDetail =
        withContext(Dispatchers.IO) {
            val kind = guestKind(type)
            val status = decodeDataObject<GuestStatus>(
                requestRaw("GET", "/api2/json/nodes/${enc(node)}/$kind/$vmid/status/current"),
            )
            val configEl = runCatching {
                decodeDataElement(requestRaw("GET", "/api2/json/nodes/${enc(node)}/$kind/$vmid/config"))
            }.getOrNull()
            val config = (configEl as? JsonObject)?.toMap().orEmpty()
            val ips = buildList {
                if (kind == "lxc" && status.status.equals("running", ignoreCase = true)) {
                    addAll(lxcIps(node, vmid))
                }
                if (kind == "qemu" && status.status.equals("running", ignoreCase = true)) {
                    addAll(agentIps(node, vmid))
                }
                addAll(extractIps(config))
            }
            GuestDetail(status = status, config = config, ips = ips.distinct())
        }

    suspend fun action(node: String, type: String, vmid: Int, action: String): OkResponse =
        withContext(Dispatchers.IO) {
            val kind = guestKind(type)
            requestForm(
                "POST",
                "/api2/json/nodes/${enc(node)}/$kind/$vmid/status/${enc(action)}",
            )
            OkResponse(ok = true)
        }

    suspend fun snapshots(node: String, type: String, vmid: Int): List<Snapshot> =
        withContext(Dispatchers.IO) {
            val kind = guestKind(type)
            val data = decodeDataElement(
                requestRaw("GET", "/api2/json/nodes/${enc(node)}/$kind/$vmid/snapshot"),
            ) as? JsonArray ?: return@withContext emptyList()
            data.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val name = obj.string("name") ?: return@mapNotNull null
                if (name == "current") return@mapNotNull null
                Snapshot(
                    name = name,
                    description = obj.string("description"),
                    snaptime = obj.long("snaptime"),
                    parent = obj.string("parent"),
                    vmstate = obj.int("vmstate"),
                )
            }.sortedByDescending { it.snaptime ?: 0L }
        }

    suspend fun createSnapshot(
        node: String,
        type: String,
        vmid: Int,
        snapname: String,
        description: String = "",
        vmstate: Boolean = false,
    ): OkResponse = withContext(Dispatchers.IO) {
        val kind = guestKind(type)
        val params = mutableMapOf(
            "snapname" to snapname,
            "description" to description,
        )
        if (kind == "qemu" && vmstate) params["vmstate"] = "1"
        requestForm("POST", "/api2/json/nodes/${enc(node)}/$kind/$vmid/snapshot", params)
        OkResponse(ok = true)
    }

    suspend fun deleteSnapshot(node: String, type: String, vmid: Int, snapname: String): OkResponse =
        withContext(Dispatchers.IO) {
            val kind = guestKind(type)
            requestRaw(
                "DELETE",
                "/api2/json/nodes/${enc(node)}/$kind/$vmid/snapshot/${enc(snapname)}",
            )
            OkResponse(ok = true)
        }

    suspend fun rollbackSnapshot(node: String, type: String, vmid: Int, snapname: String): OkResponse =
        withContext(Dispatchers.IO) {
            val kind = guestKind(type)
            requestForm(
                "POST",
                "/api2/json/nodes/${enc(node)}/$kind/$vmid/snapshot/${enc(snapname)}/rollback",
            )
            OkResponse(ok = true)
        }

    suspend fun updateGuestConfig(
        node: String,
        type: String,
        vmid: Int,
        cores: Int? = null,
        memoryMb: Int? = null,
        swapMb: Int? = null,
    ): OkResponse = withContext(Dispatchers.IO) {
        val kind = guestKind(type)
        val params = mutableMapOf<String, String>()
        if (cores != null) params["cores"] = cores.toString()
        if (memoryMb != null) params["memory"] = memoryMb.toString()
        if (swapMb != null && kind == "lxc") params["swap"] = swapMb.toString()
        if (params.isEmpty()) return@withContext OkResponse(ok = true)
        requestForm("PUT", "/api2/json/nodes/${enc(node)}/$kind/$vmid/config", params)
        OkResponse(ok = true)
    }

    suspend fun tasks(limit: Int = 80): List<PveTask> = withContext(Dispatchers.IO) {
        val nodes = parseNodeNames(requestRaw("GET", "/api2/json/nodes"))
        val out = mutableListOf<PveTask>()
        for (node in nodes) {
            runCatching {
                val data = decodeDataElement(
                    requestRaw("GET", "/api2/json/nodes/${enc(node)}/tasks?limit=$limit"),
                ) as? JsonArray ?: JsonArray(emptyList())
                data.forEach { el ->
                    val obj = el as? JsonObject ?: return@forEach
                    val upid = obj.string("upid") ?: return@forEach
                    out += PveTask(
                        upid = upid,
                        node = obj.string("node") ?: node,
                        type = obj.string("type"),
                        status = obj.string("status"),
                        user = obj.string("user"),
                        starttime = obj.long("starttime"),
                        endtime = obj.long("endtime"),
                        id = obj.string("id"),
                    )
                }
            }
        }
        out.sortedByDescending { it.starttime ?: 0L }.take(limit)
    }

    suspend fun taskLog(node: String, upid: String, start: Int = 0, limit: Int = 80): List<String> =
        withContext(Dispatchers.IO) {
            val data = decodeDataElement(
                requestRaw(
                    "GET",
                    "/api2/json/nodes/${enc(node)}/tasks/${enc(upid)}/log?start=$start&limit=$limit",
                ),
            ) as? JsonArray ?: return@withContext emptyList()
            data.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                obj.string("t")
            }
        }

    suspend fun taskStatus(node: String, upid: String): TaskStatus =
        withContext(Dispatchers.IO) {
            decodeDataObject(
                requestRaw("GET", "/api2/json/nodes/${enc(node)}/tasks/${enc(upid)}/status"),
            )
        }

    suspend fun backupStorages(): List<BackupStorage> = withContext(Dispatchers.IO) {
        resources().resources.mapNotNull { r ->
            val content = r.content.orEmpty()
            if (r.type != "storage" || r.node.isNullOrBlank() || r.storage.isNullOrBlank()) return@mapNotNull null
            if (!content.split(',').map { it.trim() }.contains("backup")) return@mapNotNull null
            BackupStorage(node = r.node, storage = r.storage, shared = r.shared == 1)
        }.distinctBy { "${it.node}/${it.storage}" }
    }

    suspend fun storageContent(node: String, storage: String, content: String? = null): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val qs = content?.let { "?content=${enc(it)}" }.orEmpty()
            val data = decodeDataElement(
                requestRaw(
                    "GET",
                    "/api2/json/nodes/${enc(node)}/storage/${enc(storage)}/content$qs",
                ),
            ) as? JsonArray ?: return@withContext emptyList()
            data.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val volid = obj.string("volid") ?: return@mapNotNull null
                MediaItem(
                    node = node,
                    storage = storage,
                    volid = volid,
                    size = obj.long("size"),
                    ctime = obj.long("ctime"),
                    format = obj.string("format"),
                    notes = obj.string("notes"),
                    vmid = obj.int("vmid"),
                    content = obj.string("content"),
                )
            }
        }

    suspend fun guestBackups(node: String, type: String, vmid: Int): List<MediaItem> =
        withContext(Dispatchers.IO) {
            backupStorages().flatMap { store ->
                runCatching {
                    storageContent(store.node, store.storage, "backup").filter { item ->
                        item.vmid == vmid ||
                            item.volid.contains("-$vmid-") ||
                            item.volid.contains("/$type-$vmid-") ||
                            item.volid.contains("vzdump-$type-$vmid-")
                    }
                }.getOrDefault(emptyList())
            }.sortedByDescending { it.ctime ?: 0L }
        }

    suspend fun backupsOverview(): List<BackupOverviewGuest> = withContext(Dispatchers.IO) {
        val guests = resources().resources.mapNotNull { it.toGuestOrNull() }
        val stores = backupStorages()
        val allBackups = stores.flatMap { store ->
            runCatching { storageContent(store.node, store.storage, "backup") }
                .getOrDefault(emptyList())
        }
        guests.map { g ->
            val mine = allBackups.filter { item ->
                item.vmid == g.vmid ||
                    item.volid.contains("vzdump-${g.type}-${g.vmid}-") ||
                    item.volid.contains("/${g.type}-${g.vmid}-")
            }.sortedByDescending { it.ctime ?: 0L }
            BackupOverviewGuest(
                node = g.node,
                type = g.type,
                vmid = g.vmid,
                name = g.name,
                status = g.status,
                backupCount = mine.size,
                lastBackup = mine.firstOrNull(),
            )
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun startBackup(
        node: String,
        vmid: Int,
        storage: String,
        mode: String = "snapshot",
        compress: String = "zstd",
    ): OkResponse = withContext(Dispatchers.IO) {
        val params = mapOf(
            "vmid" to vmid.toString(),
            "storage" to storage,
            "mode" to mode,
            "compress" to compress,
            "remove" to "0",
        )
        val text = requestForm("POST", "/api2/json/nodes/${enc(node)}/vzdump", params)
        OkResponse(ok = true, upid = unwrapUpid(text))
    }

    suspend fun restoreBackup(
        node: String,
        type: String,
        vmid: Int,
        archive: String,
        storage: String? = null,
        force: Boolean = false,
    ): OkResponse = withContext(Dispatchers.IO) {
        val kind = guestKind(type)
        val params = mutableMapOf(
            "vmid" to vmid.toString(),
        )
        if (!storage.isNullOrBlank()) params["storage"] = storage
        if (force) params["force"] = "1"
        if (kind == "qemu") {
            params["archive"] = archive
        } else {
            params["ostemplate"] = archive
            params["restore"] = "1"
        }
        val text = requestForm("POST", "/api2/json/nodes/${enc(node)}/$kind", params)
        OkResponse(ok = true, upid = unwrapUpid(text))
    }

    suspend fun deleteBackup(node: String, storage: String, volid: String): OkResponse =
        withContext(Dispatchers.IO) {
            val store = storage.ifBlank { volid.substringBefore(':', missingDelimiterValue = storage) }
            val volname = if (volid.contains(':')) volid.substringAfter(':') else volid
            requestRaw(
                "DELETE",
                "/api2/json/nodes/${enc(node)}/storage/${enc(store)}/content/${enc(volname)}",
            )
            OkResponse(ok = true)
        }

    suspend fun nextId(): Int = withContext(Dispatchers.IO) {
        val data = decodeDataElement(requestRaw("GET", "/api2/json/cluster/nextid"))
        when (data) {
            is JsonPrimitive -> data.contentOrNull?.toIntOrNull()
            else -> null
        } ?: throw ApiException(0, "Could not read next VMID")
    }

    suspend fun nodeBridges(node: String): List<NodeBridge> = withContext(Dispatchers.IO) {
        val data = decodeDataElement(
            requestRaw("GET", "/api2/json/nodes/${enc(node)}/network"),
        ) as? JsonArray ?: return@withContext emptyList()
        data.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            if (obj.string("type") != "bridge") return@mapNotNull null
            val iface = obj.string("iface") ?: return@mapNotNull null
            NodeBridge(iface = iface, active = obj.int("active") != 0)
        }.sortedBy { it.iface }
    }

    suspend fun diskStorages(content: String): List<DiskStorage> = withContext(Dispatchers.IO) {
        resources().resources.mapNotNull { r ->
            if (r.type != "storage" || r.node.isNullOrBlank() || r.storage.isNullOrBlank()) {
                return@mapNotNull null
            }
            val allowed = r.content.orEmpty().split(',').map { it.trim() }
            if (!allowed.contains(content)) return@mapNotNull null
            DiskStorage(node = r.node, storage = r.storage, shared = r.shared == 1)
        }.distinctBy { "${it.node}/${it.storage}" }
    }

    suspend fun createLxc(
        node: String,
        vmid: Int,
        hostname: String,
        ostemplate: String,
        password: String,
        storage: String,
        diskGiB: Int,
        cores: Int,
        memoryMb: Int,
        swapMb: Int,
        bridge: String,
        unprivileged: Boolean,
        start: Boolean,
    ): CreateGuestResult = withContext(Dispatchers.IO) {
        val params = mutableMapOf(
            "vmid" to vmid.toString(),
            "hostname" to hostname,
            "ostemplate" to ostemplate,
            "password" to password,
            "rootfs" to "$storage:$diskGiB",
            "cores" to cores.toString(),
            "memory" to memoryMb.toString(),
            "swap" to swapMb.toString(),
            "net0" to "name=eth0,bridge=$bridge,ip=dhcp",
            "unprivileged" to if (unprivileged) "1" else "0",
        )
        if (start) params["start"] = "1"
        val text = requestForm("POST", "/api2/json/nodes/${enc(node)}/lxc", params)
        CreateGuestResult(type = "lxc", node = node, vmid = vmid, upid = unwrapUpid(text))
    }

    suspend fun createQemu(
        node: String,
        vmid: Int,
        name: String,
        storage: String,
        diskGiB: Int,
        cores: Int,
        memoryMb: Int,
        bridge: String,
        iso: String?,
        start: Boolean,
    ): CreateGuestResult = withContext(Dispatchers.IO) {
        val params = mutableMapOf(
            "vmid" to vmid.toString(),
            "name" to name,
            "cores" to cores.toString(),
            "memory" to memoryMb.toString(),
            "scsihw" to "virtio-scsi-single",
            "scsi0" to "$storage:$diskGiB",
            "net0" to "virtio,bridge=$bridge",
            "ostype" to "l26",
            "cpu" to "x86-64-v2-AES",
            "agent" to "1",
        )
        if (!iso.isNullOrBlank()) {
            params["ide2"] = "$iso,media=cdrom"
            params["boot"] = "order=ide2;scsi0"
        } else {
            params["boot"] = "order=scsi0"
        }
        if (start) params["start"] = "1"
        val text = requestForm("POST", "/api2/json/nodes/${enc(node)}/qemu", params)
        CreateGuestResult(type = "qemu", node = node, vmid = vmid, upid = unwrapUpid(text))
    }

    suspend fun mediaItems(content: String): List<MediaItem> = withContext(Dispatchers.IO) {
        resources().resources.mapNotNull { r ->
            if (r.type != "storage" || r.node.isNullOrBlank() || r.storage.isNullOrBlank()) return@mapNotNull null
            val allowed = r.content.orEmpty().split(',').map { it.trim() }
            if (!allowed.contains(content)) return@mapNotNull null
            r
        }.flatMap { r ->
            runCatching { storageContent(r.node!!, r.storage!!, content) }
                .getOrDefault(emptyList())
        }.sortedByDescending { it.ctime ?: 0L }
    }

    fun consoleWebUrl(node: String, type: String, vmid: Int): String {
        val kind = guestKind(type)
        return "$baseUrl/?console=$kind&novnc=1&vmid=$vmid&node=${enc(node)}"
    }

    private fun guestKind(type: String): String = when (type) {
        "lxc" -> "lxc"
        else -> "qemu"
    }

    private fun unwrapUpid(text: String): String? = runCatching {
        val data = decodeDataElement(text)
        (data as? JsonPrimitive)?.contentOrNull ?: (data as? JsonObject)?.string("upid")
    }.getOrNull()

    private fun lxcIps(node: String, vmid: Int): List<String> = runCatching {
        val data = decodeDataElement(
            requestRaw("GET", "/api2/json/nodes/${enc(node)}/lxc/$vmid/interfaces"),
        )
        val arr = data as? JsonArray ?: return@runCatching emptyList()
        arr.flatMap { el ->
            val obj = el as? JsonObject ?: return@flatMap emptyList()
            val inet = obj.string("inet")?.substringBefore('/')
            val inet6 = obj.string("inet6")?.substringBefore('/')
            listOfNotNull(
                inet?.takeIf { it.isNotBlank() && it != "127.0.0.1" },
                inet6?.takeIf { it.isNotBlank() && it != "::1" && !it.startsWith("fe80") },
            )
        }
    }.getOrDefault(emptyList())

    private fun agentIps(node: String, vmid: Int): List<String> = runCatching {
        val root = decodeDataElement(
            requestRaw("GET", "/api2/json/nodes/${enc(node)}/qemu/$vmid/agent/network-get-interfaces"),
        )
        val result = when (root) {
            is JsonObject -> root["result"] ?: root
            else -> root
        }
        val interfaces = when (result) {
            is JsonArray -> result
            is JsonObject -> result["result"]?.jsonArray
            else -> null
        } ?: return@runCatching emptyList()

        interfaces.flatMap { iface ->
            val addrs = iface.jsonObject["ip-addresses"]?.jsonArray.orEmpty()
            addrs.mapNotNull { addr ->
                val obj = addr.jsonObject
                val type = obj["ip-address-type"]?.jsonPrimitive?.contentOrNull
                val ip = obj["ip-address"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                if (type == "ipv4" && ip != "127.0.0.1") ip else null
            }
        }
    }.getOrDefault(emptyList())

    private fun extractIps(config: Map<String, JsonElement>): List<String> {
        val ips = mutableListOf<String>()
        config.forEach { (key, value) ->
            if (!key.startsWith("net")) return@forEach
            val raw = (value as? JsonPrimitive)?.contentOrNull ?: return@forEach
            raw.split(',').forEach { part ->
                val trimmed = part.trim()
                if (trimmed.startsWith("ip=")) {
                    val ip = trimmed.removePrefix("ip=").substringBefore('/').trim()
                    if (ip.isNotBlank() && !ip.equals("dhcp", ignoreCase = true) && ip != "0.0.0.0") {
                        ips += ip
                    }
                }
                if (trimmed.startsWith("ip6=")) {
                    val ip = trimmed.removePrefix("ip6=").substringBefore('/').trim()
                    if (ip.isNotBlank() && !ip.equals("dhcp", ignoreCase = true) && ip != "::") {
                        ips += ip
                    }
                }
            }
        }
        return ips
    }

    private fun loadGuestsFromNodes(): List<ClusterResource> {
        val nodes = parseNodeNames(requestRaw("GET", "/api2/json/nodes"))
        val out = mutableListOf<ClusterResource>()
        for (node in nodes) {
            runCatching {
                out += parseNodeGuests(
                    node = node,
                    type = "qemu",
                    text = requestRaw("GET", "/api2/json/nodes/${enc(node)}/qemu"),
                )
            }
            runCatching {
                out += parseNodeGuests(
                    node = node,
                    type = "lxc",
                    text = requestRaw("GET", "/api2/json/nodes/${enc(node)}/lxc"),
                )
            }
        }
        return out
    }

    private fun parseNodeNames(text: String): List<String> {
        val data = decodeDataElement(text)
        val arr = data as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            obj.string("node") ?: obj.string("name")
        }
    }

    private fun parseClusterResources(text: String): List<ClusterResource> {
        val data = decodeDataElement(text)
        val arr = data as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el -> parseResourceObject(el as? JsonObject ?: return@mapNotNull null) }
    }

    private fun parseNodeGuests(node: String, type: String, text: String): List<ClusterResource> {
        val data = decodeDataElement(text)
        val arr = data as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val vmid = obj.int("vmid") ?: return@mapNotNull null
            ClusterResource(
                id = "$type/$vmid",
                type = type,
                node = node,
                vmid = vmid,
                name = obj.string("name"),
                status = obj.string("status"),
                cpu = obj.double("cpu"),
                maxcpu = obj.double("maxcpu") ?: obj.double("cpus"),
                mem = obj.long("mem"),
                maxmem = obj.long("maxmem"),
                disk = obj.long("disk"),
                maxdisk = obj.long("maxdisk"),
                uptime = obj.long("uptime"),
                netin = obj.long("netin"),
                netout = obj.long("netout"),
                diskread = obj.long("diskread"),
                diskwrite = obj.long("diskwrite"),
                template = obj.int("template"),
                lock = obj.string("lock"),
                qmpstatus = obj.string("qmpstatus"),
            )
        }
    }

    private fun parseResourceObject(obj: JsonObject): ClusterResource? {
        val type = obj.string("type") ?: return null
        val id = obj.string("id") ?: "$type/${obj.string("vmid").orEmpty()}"
        return ClusterResource(
            id = id,
            type = type,
            node = obj.string("node"),
            vmid = obj.int("vmid"),
            name = obj.string("name"),
            status = obj.string("status"),
            cpu = obj.double("cpu"),
            maxcpu = obj.double("maxcpu"),
            mem = obj.long("mem"),
            maxmem = obj.long("maxmem"),
            disk = obj.long("disk"),
            maxdisk = obj.long("maxdisk"),
            uptime = obj.long("uptime"),
            netin = obj.long("netin"),
            netout = obj.long("netout"),
            diskread = obj.long("diskread"),
            diskwrite = obj.long("diskwrite"),
            template = obj.int("template"),
            lock = obj.string("lock"),
            qmpstatus = obj.string("qmpstatus"),
            storage = obj.string("storage"),
            content = obj.string("content"),
            shared = obj.int("shared"),
            plugintype = obj.string("plugintype"),
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? {
        val p = this[key] as? JsonPrimitive ?: return null
        p.contentOrNull?.toIntOrNull()?.let { return it }
        return p.contentOrNull?.toDoubleOrNull()?.toInt()
    }

    private fun JsonObject.long(key: String): Long? {
        val p = this[key] as? JsonPrimitive ?: return null
        p.contentOrNull?.toLongOrNull()?.let { return it }
        return p.contentOrNull?.toDoubleOrNull()?.toLong()
    }

    private fun JsonObject.double(key: String): Double? {
        val p = this[key] as? JsonPrimitive ?: return null
        return p.contentOrNull?.toDoubleOrNull()
    }

    private fun requestForm(
        method: String,
        path: String,
        params: Map<String, String> = emptyMap(),
    ): String {
        val body = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
        }.build()
        return execute(method, path, body)
    }

    private fun requestRaw(method: String, path: String): String {
        val body = when (method) {
            "GET", "HEAD" -> null
            "DELETE" -> null
            else -> ByteArray(0).toRequestBody(null)
        }
        return execute(method, path, body)
    }

    private fun execute(method: String, path: String, body: okhttp3.RequestBody?): String {
        if (baseUrl.isBlank()) throw ApiException(0, "Proxmox URL is not set.")
        val needsCsrf = method != "GET" && method != "HEAD"
        val request = Request.Builder()
            .url("$baseUrl$path")
            .method(method, body)
            .header("Accept", "application/json")
            .apply {
                if (authTicket.isNotBlank()) {
                    header("Cookie", "PVEAuthCookie=$authTicket")
                }
                if (needsCsrf && csrfToken.isNotBlank()) {
                    header("CSRFPreventionToken", csrfToken)
                }
            }
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(response.code, extractError(text) ?: "HTTP ${response.code}")
            }
            return text
        }
    }

    private inline fun <reified T> decodeDataObject(text: String): T {
        val envelope = json.decodeFromString<PveEnvelope<JsonElement>>(text)
        val data = envelope.data ?: throw ApiException(0, "Empty response")
        return json.decodeFromJsonElement(data)
    }

    private fun decodeDataElement(text: String): JsonElement {
        val envelope = json.decodeFromString<PveEnvelope<JsonElement>>(text)
        return envelope.data ?: JsonObject(emptyMap())
    }

    private fun extractError(text: String): String? {
        if (text.isBlank()) return null
        return runCatching {
            val el = json.parseToJsonElement(text)
            when (el) {
                is JsonObject -> {
                    el["errors"]?.let { errors ->
                        when (errors) {
                            is JsonObject -> errors.values.joinToString("; ") {
                                (it as? JsonPrimitive)?.contentOrNull ?: it.toString()
                            }
                            is JsonPrimitive -> errors.content
                            else -> null
                        }
                    } ?: el["message"]?.jsonPrimitive?.contentOrNull
                        ?: el["data"]?.jsonPrimitive?.contentOrNull
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun enc(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
