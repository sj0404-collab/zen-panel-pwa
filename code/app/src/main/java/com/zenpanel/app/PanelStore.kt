package com.zenpanel.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight persistent store for panel state (Browsec toggle + traffic log),
 * the native equivalent of the PWA LocalStorage cache.
 */
class PanelStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var browsecEnabled: Boolean
        get() = prefs.getBoolean(KEY_BROWSEC, false)
        set(value) = prefs.edit().putBoolean(KEY_BROWSEC, value).apply()

    val trafficLog: List<String>
        get() = prefs.getString(KEY_LOG, "")
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun logTraffic(entry: String) {
        val updated = (trafficLog + entry).takeLast(MAX_LOG)
        prefs.edit().putString(KEY_LOG, updated.joinToString("\n")).apply()
    }

    fun clearTraffic() {
        prefs.edit().remove(KEY_LOG).apply()
    }

    companion object {
        private const val PREFS = "zen_panel_store"
        private const val KEY_BROWSEC = "browsec_enabled"
        private const val KEY_LOG = "traffic_log"
        private const val MAX_LOG = 50
    }
}
