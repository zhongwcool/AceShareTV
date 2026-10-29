package com.alex.aceshare.mobile

import android.app.Application
import com.alex.aceshare.AceShareContainer
import com.alex.aceshare.createAppUpdater

class MobileApp : Application() {
    lateinit var container: AceShareContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AceShareContainer(this, createAppUpdater(this))
    }
}
