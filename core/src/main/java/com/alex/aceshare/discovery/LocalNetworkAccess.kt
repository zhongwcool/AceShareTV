package com.alex.aceshare.discovery

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Android 17 blocks local NSD and LAN sockets unless this runtime permission is granted.
 * Without it, each discoverServices call raises the system device picker.
 */
object LocalNetworkAccess {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    fun permissionToRequest(): String? {
        if (Build.VERSION.SDK_INT < 37) return null
        return PERMISSION
    }

    fun isGranted(context: Context): Boolean {
        val permission = permissionToRequest() ?: return true
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}
