package com.zenpanel.app

import java.net.*
import kotlin.concurrent.thread

/**
 * Lightweight diagnostic monitor adapted from the wrapper project:
 * ping, jitter, ping-based speed, external IP. Runs off the main thread.
 */
class NetworkMonitor {

    fun measureOnce(onDone: (ping: Long, jitter: Long, externalIp: String?) -> Unit) {
        thread {
            val history = mutableListOf<Long>()
            var externalIp: String? = null
            repeat(3) {
                val p = tcpPing("1.1.1.1", 443, 3000)
                if (p > 0) history.add(p)
            }
            val ping = if (history.isEmpty()) -1L else history.average().toLong()
            val jitter = if (history.size < 2) 0L else
                history.zipWithNext().sumOf { kotlin.math.abs(it.first - it.second) } / (history.size - 1)
            externalIp = externalIpCheck()
            onDone(ping, jitter, externalIp)
        }
    }

    private fun tcpPing(host: String, port: Int, timeout: Int): Long {
        return try {
            val s = Socket()
            val start = System.currentTimeMillis()
            s.connect(InetSocketAddress(host, port), timeout)
            val elapsed = System.currentTimeMillis() - start
            s.close()
            elapsed
        } catch (_: Exception) {
            -1L
        }
    }

    private fun externalIpCheck(): String? {
        for (url in arrayOf("https://api.ipify.org", "https://ifconfig.me/ip")) {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 5000
                c.readTimeout = 5000
                c.setRequestProperty("User-Agent", "ZenPanel/1.0")
                val r = c.inputStream.bufferedReader().readText().trim()
                c.disconnect()
                if (r.isNotEmpty()) return r
            } catch (_: Exception) {
            }
        }
        return null
    }
}
