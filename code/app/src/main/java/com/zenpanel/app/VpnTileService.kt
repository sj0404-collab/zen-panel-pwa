package com.zenpanel.app

import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.app.ServiceCompat

/**
 * Quick Settings tile for toggling the tunnel. Adapted from the wrapper project's tile service.
 */
class VpnTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTile()
    }

    override fun onClick() {
        super.onClick()
        if (VpnService.prepare(this) != null) {
            val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(open)
            return
        }
        val intent = Intent(this, ZenVpnService::class.java)
        if (isVpnRunning()) {
            intent.action = ZenVpnService.ACTION_DISCONNECT
        } else {
            intent.action = ZenVpnService.ACTION_CONNECT
            val server = ServerRepository(this).selected() ?: run { syncTile(); return }
            intent.putExtra(ZenVpnService.EXTRA_CONFIG, server.config)
            intent.putExtra(ZenVpnService.EXTRA_LABEL, server.displayName())
        }
        startForegroundService(intent)
        handler().postDelayed({ syncTile() }, 1500)
    }

    private fun syncTile() {
        val tile = qsTile ?: return
        tile.state = if (isVpnRunning()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.updateTile()
    }

    private fun isVpnRunning(): Boolean =
        getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_RUNNING, false)

    private fun handler() = android.os.Handler(mainLooper)

    companion object {
        private const val PREFS = "zen_vpn_state"
        private const val KEY_RUNNING = "running"
    }
}
