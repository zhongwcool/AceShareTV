package com.alex.aceshare

import android.app.Application

class AceShareApp : Application() {
    lateinit var container: AceShareContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AceShareContainer(this, createAppUpdater(this))
    }
}
