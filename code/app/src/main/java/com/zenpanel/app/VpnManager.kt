package com.zenpanel.app

import android.content.Context
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.StringReader

/**
 * Thin wrapper around the WireGuard userspace backend.
 *
 * This is the real engine: it creates a TUN interface, installs the route table
 * from the supplied config and forwards traffic through the encrypted tunnel.
 * Statistics are read straight from wireguard-go, so speed/counters are real.
 */
class VpnManager(private val context: Context) {

    private val backend: Backend = GoBackend(context)

    private var tunnel: Tunnel? = null

    /** Called whenever the tunnel state changes (UP/DOWN). */
    var onStateChanged: ((Tunnel.State) -> Unit)? = null

    val isUp: Boolean
        get() = state == Tunnel.State.UP

    val state: Tunnel.State
        get() = tunnel?.let {
            try {
                backend.getState(it)
            } catch (_: Exception) {
                Tunnel.State.DOWN
            }
        } ?: Tunnel.State.DOWN

    /**
     * Bring the tunnel up using a wg-quick style configuration text.
     * Throws on invalid config / missing VPN permission.
     */
    @Throws(Exception::class)
    fun up(configText: String): Tunnel.State {
        val parsed: Config = Config.parse(StringReader(configText).buffered())
        val newTunnel = object : Tunnel {
            override fun getName(): String = TUNNEL_NAME

            override fun onStateChange(newState: Tunnel.State) {
                onStateChanged?.invoke(newState)
            }
        }
        tunnel = newTunnel
        return backend.setState(newTunnel, Tunnel.State.UP, parsed)
    }

    fun down(): Tunnel.State {
        val current = tunnel ?: return Tunnel.State.DOWN
        val result = try {
            backend.setState(current, Tunnel.State.DOWN, null)
        } catch (_: Exception) {
            Tunnel.State.DOWN
        }
        tunnel = null
        return result
    }

    fun statistics(): Statistics? {
        val current = tunnel ?: return null
        return try {
            backend.getStatistics(current)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TUNNEL_NAME = "zen-panel"
    }
}
