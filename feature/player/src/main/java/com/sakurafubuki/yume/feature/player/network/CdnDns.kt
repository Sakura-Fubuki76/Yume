package com.sakurafubuki.yume.feature.player.network

import okhttp3.Dns
import java.net.InetAddress

class CdnDns(
    private val selections: () -> Map<String, String>,
) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val results = mutableListOf<InetAddress>()

        selections().get(hostname)?.let { ip ->
            try {
                results.add(InetAddress.getByName(ip))
            } catch (_: Exception) { }
        }

        try {
            results.addAll(Dns.SYSTEM.lookup(hostname))
        } catch (_: Exception) { }

        return results.distinct()
    }
}
