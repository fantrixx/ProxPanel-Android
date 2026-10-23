package com.proxpanel.app.data

import kotlinx.serialization.Serializable

data class ServiceIconMatch(val slug: String, val label: String)

data class ServiceIconSuggestion(
    val slug: String,
    val label: String,
    val score: Int,
    val reason: String,
)

enum class GuestIconMode(val id: String) {
    Auto("auto"),
    Cdn("cdn"),
    Upload("upload"),
    None("none"),
    ;

    companion object {
        fun fromId(id: String?): GuestIconMode = entries.firstOrNull { it.id == id } ?: Auto
    }
}

@Serializable
data class GuestIconRecord(
    val mode: String,
    val slug: String? = null,
    val uri: String? = null,
    val updatedAt: Long = 0,
)

private data class Alias(val keys: List<String>, val slug: String, val label: String)

private val ALIASES: List<Alias> = listOf(
    Alias(listOf("homeassistant", "home assistant", "hass", "hassio"), "home-assistant", "Home Assistant"),
    Alias(listOf("nextcloud"), "nextcloud", "Nextcloud"),
    Alias(listOf("jellyfin"), "jellyfin", "Jellyfin"),
    Alias(listOf("jellyseerr", "jellyseer"), "jellyseerr", "Jellyseerr"),
    Alias(listOf("overseerr"), "overseerr", "Overseerr"),
    Alias(listOf("plex"), "plex", "Plex"),
    Alias(listOf("emby"), "emby", "Emby"),
    Alias(listOf("sonarr"), "sonarr", "Sonarr"),
    Alias(listOf("radarr"), "radarr", "Radarr"),
    Alias(listOf("lidarr"), "lidarr", "Lidarr"),
    Alias(listOf("bazarr"), "bazarr", "Bazarr"),
    Alias(listOf("prowlarr"), "prowlarr", "Prowlarr"),
    Alias(listOf("sabnzbd"), "sabnzbd", "SABnzbd"),
    Alias(listOf("nzbget"), "nzbget", "NZBGet"),
    Alias(listOf("qbittorrent", "qbit"), "qbittorrent", "qBittorrent"),
    Alias(listOf("transmission"), "transmission", "Transmission"),
    Alias(listOf("deluge"), "deluge", "Deluge"),
    Alias(listOf("portainer"), "portainer", "Portainer"),
    Alias(listOf("nginxproxymanager", "nginx proxy manager", "npm"), "nginx-proxy-manager", "Nginx Proxy Manager"),
    Alias(listOf("traefik"), "traefik", "Traefik"),
    Alias(listOf("caddy"), "caddy", "Caddy"),
    Alias(listOf("nginx"), "nginx", "Nginx"),
    Alias(listOf("apache", "httpd"), "apache", "Apache"),
    Alias(listOf("pihole", "pi hole", "pi-hole"), "pi-hole", "Pi-hole"),
    Alias(listOf("adguard", "adguardhome", "adguard home"), "adguard-home", "AdGuard Home"),
    Alias(listOf("unbound"), "unbound", "Unbound"),
    Alias(listOf("wireguard"), "wireguard", "WireGuard"),
    Alias(listOf("openvpn"), "openvpn", "OpenVPN"),
    Alias(listOf("tailscale"), "tailscale", "Tailscale"),
    Alias(listOf("cloudflared", "cloudflare tunnel"), "cloudflare", "Cloudflare"),
    Alias(listOf("authentik"), "authentik", "Authentik"),
    Alias(listOf("authelia"), "authelia", "Authelia"),
    Alias(listOf("vaultwarden", "bitwarden"), "vaultwarden", "Vaultwarden"),
    Alias(listOf("immich"), "immich", "Immich"),
    Alias(listOf("photoprism"), "photoprism", "PhotoPrism"),
    Alias(listOf("paperless", "paperlessngx", "paperless ngx"), "paperless-ngx", "Paperless-ngx"),
    Alias(listOf("bookstack"), "bookstack", "BookStack"),
    Alias(listOf("wikijs", "wiki js", "wiki.js"), "wikijs", "Wiki.js"),
    Alias(listOf("grafana"), "grafana", "Grafana"),
    Alias(listOf("prometheus"), "prometheus", "Prometheus"),
    Alias(listOf("influxdb", "influx"), "influxdb", "InfluxDB"),
    Alias(listOf("uptimekuma", "uptime kuma"), "uptime-kuma", "Uptime Kuma"),
    Alias(listOf("netdata"), "netdata", "Netdata"),
    Alias(listOf("zabbix"), "zabbix", "Zabbix"),
    Alias(listOf("gitlab"), "gitlab", "GitLab"),
    Alias(listOf("gitea"), "gitea", "Gitea"),
    Alias(listOf("forgejo"), "forgejo", "Forgejo"),
    Alias(listOf("jenkins"), "jenkins", "Jenkins"),
    Alias(listOf("docker"), "docker", "Docker"),
    Alias(listOf("kubernetes", "k8s"), "kubernetes", "Kubernetes"),
    Alias(listOf("postgres", "postgresql"), "postgresql", "PostgreSQL"),
    Alias(listOf("mariadb"), "mariadb", "MariaDB"),
    Alias(listOf("mysql"), "mysql", "MySQL"),
    Alias(listOf("mongodb", "mongo"), "mongodb", "MongoDB"),
    Alias(listOf("redis"), "redis", "Redis"),
    Alias(listOf("elasticsearch", "elastic"), "elasticsearch", "Elasticsearch"),
    Alias(listOf("rabbitmq"), "rabbitmq", "RabbitMQ"),
    Alias(listOf("mqtt", "mosquitto"), "mosquitto", "Mosquitto"),
    Alias(listOf("zigbee2mqtt", "z2m"), "zigbee2mqtt", "Zigbee2MQTT"),
    Alias(listOf("frigate"), "frigate", "Frigate"),
    Alias(listOf("homebridge"), "homebridge", "Homebridge"),
    Alias(listOf("esphome"), "esphome", "ESPHome"),
    Alias(listOf("nodered", "node red", "node-red"), "node-red", "Node-RED"),
    Alias(listOf("wordpress", "wp"), "wordpress", "WordPress"),
    Alias(listOf("mattermost"), "mattermost", "Mattermost"),
    Alias(listOf("rocketchat", "rocket chat"), "rocket-chat", "Rocket.Chat"),
    Alias(listOf("matrix", "synapse"), "matrix", "Matrix"),
    Alias(listOf("jitsi"), "jitsi", "Jitsi"),
    Alias(listOf("mailcow"), "mailcow", "Mailcow"),
    Alias(listOf("roundcube"), "roundcube", "Roundcube"),
    Alias(listOf("minio"), "minio", "MinIO"),
    Alias(listOf("seafile"), "seafile", "Seafile"),
    Alias(listOf("syncthing"), "syncthing", "Syncthing"),
    Alias(listOf("filebrowser"), "filebrowser", "File Browser"),
    Alias(listOf("nextcloudaio", "nc aio"), "nextcloud", "Nextcloud"),
    Alias(listOf("onlyoffice"), "onlyoffice", "OnlyOffice"),
    Alias(listOf("ubuntu"), "ubuntu-linux", "Ubuntu"),
    Alias(listOf("debian"), "debian-linux", "Debian"),
    Alias(listOf("fedora"), "fedora", "Fedora"),
    Alias(listOf("arch", "archlinux", "arch linux"), "arch-linux", "Arch Linux"),
    Alias(listOf("windows", "win11", "win10"), "microsoft-windows", "Windows"),
    Alias(listOf("alpine"), "alpine-linux", "Alpine"),
    Alias(listOf("code server", "codeserver"), "vscode", "code-server"),
    Alias(listOf("jupyter", "jupyterlab"), "jupyter", "Jupyter"),
    Alias(listOf("ollama"), "ollama", "Ollama"),
    Alias(listOf("openwebui", "open webui"), "open-webui", "Open WebUI"),
    Alias(listOf("proxpanel"), "proxmox", "ProxPanel"),
    Alias(listOf("proxmox", "pve"), "proxmox", "Proxmox"),
    Alias(listOf("truenas"), "truenas", "TrueNAS"),
    Alias(listOf("unraid"), "unraid", "Unraid"),
    Alias(listOf("opnsense"), "opnsense", "OPNsense"),
    Alias(listOf("pfsense"), "pfsense", "pfSense"),
    Alias(listOf("teamspeak", "teamspeak3", "ts3", "ts server"), "teamspeak", "TeamSpeak"),
    Alias(listOf("discord"), "discord", "Discord"),
    Alias(listOf("minecraft", "paper mc", "papermc", "spigot", "bukkit"), "minecraft", "Minecraft"),
    Alias(listOf("steam", "steamcmd"), "steam", "Steam"),
    Alias(listOf("murmur", "mumble"), "mumble", "Mumble"),
    Alias(listOf("freepbx"), "freepbx", "FreePBX"),
)

private const val CDN = "https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/png"

private val STRICT_ONLY = setOf(
    "npm", "wp", "hass", "pve", "k8s", "z2m", "mongo", "influx", "elastic", "matrix", "arch", "ts3",
)

private val NOISE = setOf(
    "server", "servers", "srv", "svc", "service", "services", "media", "mediaserver", "app", "apps",
    "ct", "vm", "lxc", "qemu", "node", "host", "prod", "dev", "test", "staging", "main", "backup",
    "new", "old", "bot", "api", "db", "sql", "panel", "stack", "instance", "v1", "v2", "v3",
)

fun guestIconKey(node: String, type: String, vmid: Int): String = "$node:$type:$vmid"

fun serviceIconUrl(slug: String): String = "$CDN/$slug.png"

private fun normalizeText(value: String): String =
    value.lowercase()
        .replace("&", " and ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun tokensOf(value: String): List<String> =
    normalizeText(value).split(' ').filter { it.isNotEmpty() }

private fun escapeRegExp(s: String): String =
    Regex.escape(s)

private fun compact(s: String): String = s.replace(" ", "")

private fun stripNoise(token: String): String {
    var t = token.replace(Regex("\\d+$"), "")
    var changed = true
    while (changed && t.length > 3) {
        changed = false
        for (n in NOISE) {
            if (t.length > n.length + 3 && t.endsWith(n)) {
                t = t.dropLast(n.length)
                changed = true
                break
            }
        }
        for (n in NOISE) {
            if (t.length > n.length + 3 && t.startsWith(n)) {
                t = t.drop(n.length)
                changed = true
                break
            }
        }
    }
    return t.replace(Regex("\\d+$"), "")
}

private fun tokenHitsKey(token: String, keyCompact: String): Boolean {
    if (keyCompact.isEmpty() || keyCompact.length < 3) return false
    if (token == keyCompact) return true
    val stripped = stripNoise(token)
    if (stripped == keyCompact) return true
    if (token.startsWith(keyCompact) && NOISE.contains(token.drop(keyCompact.length))) return true
    if (stripped.startsWith(keyCompact) && stripped.length - keyCompact.length <= 2) return true
    if (keyCompact.length >= 5 && token.contains(keyCompact) && token.length - keyCompact.length <= 8) {
        val around = token.replace(keyCompact, "")
        if (around.isEmpty() || around.all { it.isDigit() } || NOISE.contains(around)) return true
    }
    return false
}

fun resolveServiceIcon(name: String?, tags: String?): ServiceIconMatch? {
    val nameNorm = normalizeText(name.orEmpty())
    if (nameNorm.isEmpty() && tags.isNullOrBlank()) return null

    val tagParts = (tags ?: "")
        .split(Regex("[;,]"))
        .map { normalizeText(it) }
        .filter { it.isNotEmpty() }

    val nameTokens = tokensOf(nameNorm).filter { !NOISE.contains(it) && !it.all { c -> c.isDigit() } }
    val rawNameTokens = tokensOf(nameNorm)
    val allTokens = rawNameTokens + tagParts.flatMap { tokensOf(it) }
    val compactName = compact(nameNorm)
    val strippedCompact = stripNoise(compactName)

    val ranked = ALIASES.flatMap { alias ->
        alias.keys.map { key -> Triple(alias, key, key.length) }
    }.sortedByDescending { it.third }

    for ((alias, key, _) in ranked) {
        val keyNorm = normalizeText(key)
        val keyCompact = compact(keyNorm)
        val strict = STRICT_ONLY.contains(keyNorm) || keyNorm.length <= 3

        if (nameNorm == keyNorm || compactName == keyCompact || strippedCompact == keyCompact) {
            return ServiceIconMatch(alias.slug, alias.label)
        }

        if (tagParts.any { t ->
                t == keyNorm || compact(t) == keyCompact || stripNoise(compact(t)) == keyCompact
            }
        ) {
            return ServiceIconMatch(alias.slug, alias.label)
        }

        if (strict) {
            if (nameTokens.size == 1) {
                val t = nameTokens[0]
                if (t == keyNorm || t == keyCompact || stripNoise(t) == keyCompact) {
                    return ServiceIconMatch(alias.slug, alias.label)
                }
            }
            continue
        }

        if (allTokens.any { t -> tokenHitsKey(t, keyCompact) || tokenHitsKey(t, keyNorm) }) {
            return ServiceIconMatch(alias.slug, alias.label)
        }

        if (keyNorm.contains(' ')) {
            val re = Regex("(?:^|\\s)${escapeRegExp(keyNorm)}(?:\\s|$)")
            if (re.containsMatchIn(nameNorm)) return ServiceIconMatch(alias.slug, alias.label)
        }

        if (keyCompact.length >= 4) {
            if (tokenHitsKey(compactName, keyCompact) || tokenHitsKey(strippedCompact, keyCompact)) {
                return ServiceIconMatch(alias.slug, alias.label)
            }
            val re = Regex(escapeRegExp(keyCompact), RegexOption.IGNORE_CASE)
            if (re.containsMatchIn(compactName)) {
                val rest = compactName.replace(keyCompact, "", ignoreCase = true)
                if (rest.isEmpty() || rest.all { it.isDigit() } || NOISE.contains(rest)) {
                    return ServiceIconMatch(alias.slug, alias.label)
                }
                val restTokens = Regex("[a-z]+|[0-9]+", RegexOption.IGNORE_CASE).findAll(rest).map { it.value }.toList()
                if (restTokens.all { NOISE.contains(it) || it.all { c -> c.isDigit() } }) {
                    return ServiceIconMatch(alias.slug, alias.label)
                }
            }
        }
    }
    return null
}

private fun sharedPrefixLen(a: String, b: String): Int {
    val n = minOf(a.length, b.length)
    var i = 0
    while (i < n && a[i] == b[i]) i++
    return i
}

fun suggestServiceIcons(
    name: String?,
    tags: String?,
    limit: Int = 8,
): List<ServiceIconSuggestion> {
    val nameNorm = normalizeText(name.orEmpty())
    val tagParts = (tags ?: "")
        .split(Regex("[;,]"))
        .map { normalizeText(it) }
        .filter { it.isNotEmpty() }
    if (nameNorm.isEmpty() && tagParts.isEmpty()) return emptyList()

    val rawTokens = (tokensOf(nameNorm) + tagParts.flatMap { tokensOf(it) })
        .filter { !it.all { c -> c.isDigit() } }
    val usefulTokens = rawTokens
        .filter { !NOISE.contains(it) }
        .map { stripNoise(it) }
        .filter { it.length >= 2 }
    val compactName = stripNoise(compact(nameNorm))
    val hay = "${nameNorm} ${tagParts.joinToString(" ")} $compactName".lowercase()

    val confident = resolveServiceIcon(name, tags)
    val bySlug = linkedMapOf<String, ServiceIconSuggestion>()

    fun consider(alias: Alias, score: Int, reason: String) {
        if (score < 12) return
        val prev = bySlug[alias.slug]
        if (prev == null || score > prev.score) {
            bySlug[alias.slug] = ServiceIconSuggestion(alias.slug, alias.label, score, reason)
        }
    }

    for (alias in ALIASES) {
        for (key in alias.keys) {
            val keyNorm = normalizeText(key)
            val keyCompact = compact(keyNorm)
            if (keyCompact.length < 2) continue

            if (confident?.slug == alias.slug) {
                consider(alias, 100, "Best match for this name")
                break
            }

            if (usefulTokens.contains(keyNorm) || usefulTokens.contains(keyCompact)) {
                consider(alias, 90, "Name contains “$keyNorm”")
                continue
            }
            if (compactName == keyCompact) {
                consider(alias, 92, "Matches the name exactly")
                continue
            }
            if (tokenHitsKey(compactName, keyCompact) || usefulTokens.any { tokenHitsKey(it, keyCompact) }) {
                consider(alias, 85, "Looks like “${alias.label}”")
                continue
            }

            if (keyCompact.length >= 4 && hay.contains(keyCompact)) {
                consider(alias, 70, "Name includes “$keyNorm”")
                continue
            }
            if (keyNorm.contains(' ') && hay.contains(keyNorm)) {
                consider(alias, 75, "Name includes “$keyNorm”")
                continue
            }

            for (token in usefulTokens) {
                if (token.length < 3 || keyCompact.length < 3) continue
                when {
                    keyCompact.startsWith(token) && token.length >= 4 ->
                        consider(alias, 40 + token.length, "“$token…” could be ${alias.label}")
                    token.startsWith(keyCompact) && keyCompact.length >= 4 ->
                        consider(alias, 55 + keyCompact.length, "Starts like ${alias.label}")
                    else -> {
                        val pref = sharedPrefixLen(token, keyCompact)
                        val minLen = minOf(token.length, keyCompact.length)
                        if (pref >= 4 && pref.toDouble() / minLen >= 0.6) {
                            consider(alias, 25 + pref * 3, "Similar to ${alias.label}")
                        }
                    }
                }
            }

            if (compactName.length >= 4 && keyCompact.length >= 4) {
                val pref = sharedPrefixLen(compactName, keyCompact)
                if (pref >= 5) {
                    consider(alias, 30 + pref * 2, "Similar to ${alias.label}")
                }
            }

            val labelTokens = tokensOf(alias.label).filter { it.length >= 4 && !NOISE.contains(it) }
            for (lt in labelTokens) {
                if (usefulTokens.contains(lt) || compactName.contains(lt)) {
                    consider(alias, 48, "Related to “$lt”")
                }
            }
        }
    }

    return bySlug.values
        .sortedWith(compareByDescending<ServiceIconSuggestion> { it.score }.thenBy { it.label })
        .take(limit)
}

fun listServiceCatalog(): List<ServiceIconMatch> {
    val seen = mutableSetOf<String>()
    val out = mutableListOf<ServiceIconMatch>()
    for (alias in ALIASES) {
        if (!seen.add(alias.slug)) continue
        out += ServiceIconMatch(alias.slug, alias.label)
    }
    return out.sortedBy { it.label }
}
