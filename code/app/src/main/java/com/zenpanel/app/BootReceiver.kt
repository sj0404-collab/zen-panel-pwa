package com.zenpanel.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log

/**
 * Restarts the VPN after boot when it was connected before reboot.
 * Adapted from the wrapper project's BootReceiver.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON") {
            return
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_WAS_CONNECTED, false)) {
            Log.d(TAG, "VPN was off, skipping autostart")
            return
        }

        if (VpnService.prepare(context) != null) {
            Log.w(TAG, "VPN permission is not granted, skipping autostart")
            return
        }

        val server = ServerRepository(context).selected() ?: return
        val config = server.config
        val label = server.displayName()

        Log.d(TAG, "Restoring VPN after $action")
        val serviceIntent = Intent(context, ZenVpnService::class.java).apply {
            setAction(ZenVpnService.ACTION_CONNECT)
            putExtra(ZenVpnService.EXTRA_CONFIG, config)
            putExtra(ZenVpnService.EXTRA_LABEL, label)
        }
        context.startForegroundService(serviceIntent)
    }

    companion object {
        private const val TAG = "BootReceiver"
        private const val PREFS = "zen_vpn_state"
        private const val KEY_WAS_CONNECTED = "was_connected"
    }
}
