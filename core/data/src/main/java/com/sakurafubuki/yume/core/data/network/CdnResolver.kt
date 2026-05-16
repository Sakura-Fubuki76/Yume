package com.sakurafubuki.yume.core.data.network

import com.sakurafubuki.yume.core.model.CdnNode
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CdnResolver @Inject constructor() {

    private val dohServers = listOf(
        "223.5.5.5",
        "119.29.29.29",
        "180.76.76.76",
        "114.114.114.114",
    )

    private val dnsTimeoutMs = 3000
    private val tcpTimeoutMs = 3000

    suspend fun resolveAllIps(hostname: String): List<String> = withContext(Dispatchers.IO) {
        val results = mutableSetOf<String>()

        try {
            InetAddress.getAllByName(hostname).mapTo(results) { it.hostAddress ?: "" }
        } catch (_: Exception) { }

        val dohResults = dohServers.flatMap { dns ->
            try {
                queryDoh(hostname, dns)
            } catch (_: Exception) {
                emptyList()
            }
        }
        results.addAll(dohResults)

        results.filter { it.isNotEmpty() && !it.contains(":") }
    }

    private fun queryDoh(hostname: String, dnsIp: String): List<String> {
        val url = URL("https://$dnsIp/dns-query?name=$hostname&type=A")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = dnsTimeoutMs
        connection.readTimeout = dnsTimeoutMs
        connection.setRequestProperty("Accept", "application/dns-json")
        try {
            val code = connection.responseCode
            if (code != 200) return emptyList()
            val body = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val answers = json.optJSONArray("Answer") ?: return emptyList()
            val ips = mutableListOf<String>()
            for (i in 0 until answers.length()) {
                val answer = answers.getJSONObject(i)
                if (answer.getInt("type") == 1) {
                    ips.add(answer.getString("data"))
                }
            }
            return ips
        } finally {
            connection.disconnect()
        }
    }

    fun measureLatency(ip: String, port: Int, timeoutMs: Int = tcpTimeoutMs): Long = try {
        val start = System.currentTimeMillis()
        Socket().use { socket ->
            socket.connect(InetSocketAddress(ip, port), timeoutMs)
        }
        System.currentTimeMillis() - start
    } catch (_: Exception) {
        Long.MAX_VALUE
    }

    suspend fun scan(hostname: String, port: Int = 443): List<CdnNode> = coroutineScope {
        val ips = resolveAllIps(hostname)
        if (ips.isEmpty()) return@coroutineScope emptyList()

        val results = ips.map { ip ->
            async(Dispatchers.IO) {
                val latency = measureLatency(ip, port)
                CdnNode(ip, latency)
            }
        }.awaitAll()

        results
            .filter { it.latencyMs < Long.MAX_VALUE }
            .sortedBy { it.latencyMs }
    }
}
