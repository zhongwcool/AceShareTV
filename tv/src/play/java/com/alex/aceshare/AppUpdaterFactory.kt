package com.alex.aceshare

import android.content.Context
import com.alex.aceshare.data.update.AppUpdater
import com.alex.aceshare.data.update.NoOpAppUpdater

fun createAppUpdater(context: Context): AppUpdater = NoOpAppUpdater
