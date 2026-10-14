package com.zenpanel.app

import java.util.UUID

/**
 * A VPN endpoint the user can connect to. The actual encrypted tunnel is provided
 * by a WireGuard configuration that the user supplies (own server or provider).
 */
data class VpnServer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val country: String,
    val countryCode: String,
    val config: String
) {
    fun flag(): String {
        val cc = countryCode.trim().uppercase()
        if (cc.length != 2) return "🌐"
        val base = 0x1F1E6
        val first = base + (cc[0] - 'A')
        val second = base + (cc[1] - 'A')
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    fun displayName(): String = flag() + "  " + (if (country.isBlank()) name else country)
}
