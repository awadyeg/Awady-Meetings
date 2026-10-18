package com.riffado.meetings.mcp

import java.net.Inet4Address
import java.net.NetworkInterface

/** Addresses other devices can use to reach the server. */
object McpNetwork {
    enum class Kind { WIFI, TAILSCALE, OTHER }

    data class LanAddress(val ip: String, val interfaceName: String, val kind: Kind)

    fun addresses(): List<LanAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { nif ->
                nif.inetAddresses.toList().filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    .map { LanAddress(it.hostAddress.orEmpty(), nif.name, kindOf(nif.name, it.hostAddress.orEmpty())) }
            }
    }.getOrDefault(emptyList()).let(::sort)

    fun kindOf(interfaceName: String, ip: String): Kind = when {
        isTailscale(ip) || interfaceName.startsWith("tailscale") -> Kind.TAILSCALE
        interfaceName.startsWith("wlan") || interfaceName.startsWith("ap") || interfaceName.startsWith("swlan") -> Kind.WIFI
        else -> Kind.OTHER
    }

    /** Tailscale hands out addresses from the CGNAT range 100.64.0.0/10. */
    fun isTailscale(ip: String): Boolean {
        val parts = ip.split('.').mapNotNull { it.toIntOrNull() }
        return parts.size == 4 && parts[0] == 100 && parts[1] in 64..127
    }

    /** Wi-Fi first, then Tailscale, then anything else (mobile data addresses are rarely reachable). */
    fun sort(list: List<LanAddress>): List<LanAddress> = list.distinctBy { it.ip }.sortedBy { it.kind.ordinal }

    fun url(ip: String, port: Int): String = "http://$ip:$port${McpHttpServer.ENDPOINT}"
}
