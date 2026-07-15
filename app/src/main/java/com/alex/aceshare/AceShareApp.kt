package com.alex.aceshare

import android.app.Application
import com.alex.aceshare.data.AceShareRepository
import com.alex.aceshare.data.ServerPreferences
import com.alex.aceshare.discovery.NsdServerDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AceShareApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var discovery: NsdServerDiscovery
        private set
    lateinit var preferences: ServerPreferences
        private set
    lateinit var repository: AceShareRepository
        private set

    override fun onCreate() {
        super.onCreate()
        discovery = NsdServerDiscovery(this)
        preferences = ServerPreferences(this)
        repository = AceShareRepository(scope = appScope)
    }
}
