package com.zenpanel.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the list of VPN servers and which one is selected.
 * Provider agnostic: any WireGuard endpoint can be added.
 */
class ServerRepository(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(): List<VpnServer> {
        val raw = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                VpnServer(
                    id = o.getString("id"),
                    name = o.optString("name"),
                    country = o.optString("country"),
                    countryCode = o.optString("countryCode"),
                    config = o.optString("config")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(server: VpnServer) {
        val list = all() + server
        persist(list)
        if (selectedId == null) selectedId = server.id
    }

    fun remove(id: String) {
        persist(all().filterNot { it.id == id })
        if (selectedId == id) selectedId = all().firstOrNull()?.id
    }

    fun get(id: String?): VpnServer? = all().firstOrNull { it.id == id }

    var selectedId: String?
        get() = prefs.getString(KEY_SELECTED, null)
        set(value) = prefs.edit().putString(KEY_SELECTED, value).apply()

    fun selected(): VpnServer? = get(selectedId)

    private fun persist(list: List<VpnServer>) {
        val array = JSONArray()
        list.forEach { s ->
            array.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("country", s.country)
                    put("countryCode", s.countryCode)
                    put("config", s.config)
                }
            )
        }
        prefs.edit().putString(KEY_SERVERS, array.toString()).apply()
    }

    companion object {
        private const val PREFS = "zen_vpn_servers"
        private const val KEY_SERVERS = "servers"
        private const val KEY_SELECTED = "selected"
    }
}
