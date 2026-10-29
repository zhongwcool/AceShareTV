package com.alex.aceshare

import android.content.Context
import com.alex.aceshare.data.AceShareRepository
import com.alex.aceshare.data.ServerPreferences
import com.alex.aceshare.data.update.AppUpdater
import com.alex.aceshare.discovery.NsdServerDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AceShareContainer(
    context: Context,
    val appUpdater: AppUpdater,
) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val discovery = NsdServerDiscovery(appContext)
    val preferences = ServerPreferences(appContext)
    val repository = AceShareRepository(scope = appScope)
}
