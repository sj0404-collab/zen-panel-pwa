package com.zenpanel.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.wireguard.android.backend.Tunnel
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Foreground service that owns the VPN connection and publishes a live status
 * notification (country + real rx/tx speed, read from the WireGuard backend).
 */
class ZenVpnService : Service() {

    private lateinit var manager: VpnManager
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    private var connected = false
    private var serverLabel = ""
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastTs = 0L
    private var downBps = 0.0
    private var upBps = 0.0

    private val ticker = object : Runnable {
        override fun run() {
            refreshStats()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        manager = (application as App).vpnManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> handleConnect(intent)
            ACTION_DISCONNECT -> handleDisconnect()
        }
        return START_STICKY
    }

    private fun handleConnect(intent: Intent) {
        val config = intent.getStringExtra(EXTRA_CONFIG)
        if (config.isNullOrBlank()) {
            stopSelf()
            return
        }
        serverLabel = intent.getStringExtra(EXTRA_LABEL).orEmpty()

        // Satisfy the foreground-service start deadline before doing slow work.
        startForegroundCompat(buildNotification(getString(R.string.vpn_connecting)))

        // GoBackend starts its own VpnService and blocks the caller while waiting
        // for that service to come up. Doing this on the main thread deadlocks,
        // so the tunnel is brought up on a worker thread.
        worker.execute {
            try {
                val result = manager.up(config)
                connected = result == Tunnel.State.UP
                handler.post {
                    lastRx = 0
                    lastTx = 0
                    lastTs = 0
                    refreshStats()
                    handler.removeCallbacks(ticker)
                    handler.post(ticker)
                }
            } catch (e: Exception) {
                connected = false
                handler.post {
                    updateNotification(getString(R.string.vpn_error, e.message ?: ""))
                    handler.postDelayed({ stopSelf() }, 4000)
                }
            }
        }
    }

    private fun handleDisconnect() {
        connected = false
        handler.removeCallbacks(ticker)
        worker.execute { manager.down() }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        worker.shutdown()
        super.onDestroy()
    }

    private fun refreshStats() {
        val stats = manager.statistics()
        val rx = stats?.totalRx() ?: 0L
        val tx = stats?.totalTx() ?: 0L
        val now = System.currentTimeMillis()
        if (lastTs != 0L) {
            val dt = (now - lastTs) / 1000.0
            if (dt > 0) {
                downBps = ((rx - lastRx) / dt).coerceAtLeast(0.0)
                upBps = ((tx - lastTx) / dt).coerceAtLeast(0.0)
            }
        }
        lastRx = rx
        lastTx = tx
        lastTs = now
        val line = getString(R.string.vpn_notif_line, formatSpeed(downBps), formatSpeed(upBps))
        updateNotification(line)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ZenVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (serverLabel.isBlank()) {
            getString(R.string.app_name)
        } else {
            getString(R.string.vpn_notif_title, serverLabel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.disconnect), stop)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(text))
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted; the tunnel keeps running regardless.
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.vpn_channel_description)
                setShowBadge(false)
            }
        )
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

    companion object {
        const val ACTION_CONNECT = "com.zenpanel.app.action.CONNECT"
        const val ACTION_DISCONNECT = "com.zenpanel.app.action.DISCONNECT"
        private const val EXTRA_CONFIG = "extra_config"
        private const val EXTRA_LABEL = "extra_label"
        private const val CHANNEL_ID = "zen_vpn"
        private const val NOTIFICATION_ID = 4321

        fun connect(context: Context, server: VpnServer) {
            val intent = Intent(context, ZenVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_CONFIG, server.config)
                putExtra(EXTRA_LABEL, server.displayName())
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun disconnect(context: Context) {
            val intent = Intent(context, ZenVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }
}
