package com.zenpanel.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.zenpanel.app.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: PanelStore
    private lateinit var app: App

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val handler = Handler(Looper.getMainLooper())
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastTs = 0L

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) startVpn() else toast(getString(R.string.vpn_permission_denied))
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val connectivityManager by lazy { getSystemService(ConnectivityManager::class.java) }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = runOnUiThread { updateStatus(true) }
        override fun onLost(network: Network) = runOnUiThread { updateStatus(isOnline()) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
            runOnUiThread { updateStatus(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) }
    }

    private val statsTicker = object : Runnable {
        override fun run() {
            renderVpn()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as App
        store = PanelStore(this)

        binding.buttonVpnToggle.setOnClickListener { toggleVpn() }
        binding.buttonSelectServer.setOnClickListener {
            startActivity(Intent(this, ServerListActivity::class.java))
        }
        binding.buttonSearch.setOnClickListener { runSearch() }
        binding.buttonTestNotification.setOnClickListener { requestNotification() }
        binding.buttonLaunchExtension.setOnClickListener {
            startActivity(Intent(this, ExtensionActivity::class.java))
        }
        binding.buttonClearLog.setOnClickListener {
            store.clearTraffic()
            renderTraffic()
        }

        updateStatus(isOnline())
        renderTraffic()
    }

    override fun onResume() {
        super.onResume()
        handler.post(statsTicker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(statsTicker)
    }

    override fun onStart() {
        super.onStart()
        connectivityManager?.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onStop() {
        super.onStop()
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun toggleVpn() {
        if (app.vpnManager.isUp) {
            ZenVpnService.disconnect(this)
            toast(getString(R.string.vpn_disconnecting))
        } else {
            requestVpn()
        }
    }

    private fun requestVpn() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            vpnPermissionLauncher.launch(prepare)
        } else {
            startVpn()
        }
    }

    private fun startVpn() {
        val server = app.servers.selected()
        if (server == null) {
            toast(getString(R.string.vpn_no_server))
            startActivity(Intent(this, ServerListActivity::class.java))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        ZenVpnService.connect(this, server)
        toast(getString(R.string.vpn_connecting))
    }

    private fun renderVpn() {
        val up = app.vpnManager.isUp
        val server = app.servers.selected()

        binding.textVpnServer.text = server?.displayName() ?: getString(R.string.vpn_no_server)
        binding.buttonVpnToggle.text =
            getString(if (up) R.string.disconnect else R.string.connect)
        binding.buttonVpnToggle.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (up) R.color.status_offline else R.color.primary)
        )
        binding.textVpnStatus.text =
            getString(if (up) R.string.vpn_connected else R.string.vpn_disconnected)
        binding.textVpnStatus.setTextColor(
            ContextCompat.getColor(this, if (up) R.color.status_online else R.color.text_muted)
        )

        val stats = app.vpnManager.statistics()
        val rx = stats?.totalRx() ?: 0L
        val tx = stats?.totalTx() ?: 0L
        val now = System.currentTimeMillis()
        var downSpeed = 0.0
        var upSpeed = 0.0
        if (lastTs != 0L && up) {
            val dt = (now - lastTs) / 1000.0
            if (dt > 0) {
                downSpeed = ((rx - lastRx) / dt).coerceAtLeast(0.0)
                upSpeed = ((tx - lastTx) / dt).coerceAtLeast(0.0)
            }
        }
        lastRx = rx
        lastTx = tx
        lastTs = now

        binding.textVpnStats.text = getString(
            R.string.vpn_stats,
            formatSpeed(downSpeed),
            formatSpeed(upSpeed),
            formatBytes(rx),
            formatBytes(tx)
        )
    }

    private fun isOnline(): Boolean {
        val cm = connectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun updateStatus(online: Boolean) {
        binding.textStatus.text = getString(if (online) R.string.status_online else R.string.status_offline)
        binding.textStatus.setTextColor(
            ContextCompat.getColor(this, if (online) R.color.status_online else R.color.status_offline)
        )
    }

    private fun runSearch() {
        val query = binding.inputSearch.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) return
        val url = "https://www.google.com/search?q=" + Uri.encode(query)
        store.logTraffic("${timeFormat.format(Date())}  ←  $query")
        renderTraffic()
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        binding.inputSearch.setText("")
    }

    private fun requestNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Notifications.show(this, getString(R.string.app_name), getString(R.string.notify_enabled_body))
        }
    }

    private fun renderTraffic() {
        val log = store.trafficLog
        binding.textTraffic.text =
            if (log.isEmpty()) getString(R.string.traffic_empty) else log.joinToString("\n")
    }

    private fun formatSpeed(bytesPerSecond: Double): String {
        val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
        var value = bytesPerSecond
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit])
    }

    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit])
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
