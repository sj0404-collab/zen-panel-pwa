package com.zenpanel.app

import android.app.Application

class App : Application() {

    lateinit var vpnManager: VpnManager
        private set

    lateinit var servers: ServerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        servers = ServerRepository(this)
        vpnManager = VpnManager(this)
    }
}
