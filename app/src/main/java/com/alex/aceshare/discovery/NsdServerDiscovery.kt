package com.alex.aceshare.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.alex.aceshare.data.DiscoveredServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class NsdServerDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val serversByKey = ConcurrentHashMap<String, DiscoveredServer>()
    private val unresolvedNames = ConcurrentHashMap.newKeySet<String>()

    private val _servers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val servers: StateFlow<List<DiscoveredServer>> = _servers.asStateFlow()

    /** True while the caller wants discovery running (may briefly be mid restart). */
    private val wanted = AtomicBoolean(false)
    private val discovering = AtomicBoolean(false)
    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var pendingRestart = false
    /** Ignore spurious onServiceLost callbacks while soft-restarting discovery. */
    private var suppressLostUntilElapsedMs = 0L

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!wanted.get()) return
            // Soft restart so services that appear after the first scan are still found.
            // Android NSD often misses late mDNS announcements without this.
            requestSoftRestart()
            mainHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    fun start() {
        if (!wanted.compareAndSet(false, true)) return
        acquireMulticastLock()
        beginDiscovery()
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS)
    }

    fun stop() {
        if (!wanted.compareAndSet(true, false)) return
        mainHandler.removeCallbacks(refreshRunnable)
        pendingRestart = false
        endDiscovery()
        unresolvedNames.clear()
        releaseMulticastLock()
    }

    fun restart() {
        stop()
        serversByKey.clear()
        publish()
        start()
    }

    private fun requestSoftRestart() {
        if (!wanted.get()) return
        suppressLostUntilElapsedMs =
            SystemClock.elapsedRealtime() + SUPPRESS_LOST_WINDOW_MS
        if (!discovering.get()) {
            beginDiscovery()
            return
        }
        pendingRestart = true
        endDiscovery()
    }

    private fun beginDiscovery() {
        if (!wanted.get()) return
        if (!discovering.compareAndSet(false, true)) return
        unresolvedNames.clear()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Discovery started: $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Found: ${serviceInfo.serviceName} type=${serviceInfo.serviceType}")
                if (!serviceInfo.serviceType.contains(SERVICE_TYPE_TOKEN)) return
                resolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (SystemClock.elapsedRealtime() < suppressLostUntilElapsedMs) {
                    Log.d(TAG, "Ignore lost during refresh: ${serviceInfo.serviceName}")
                    return
                }
                Log.d(TAG, "Lost: ${serviceInfo.serviceName}")
                val name = serviceInfo.serviceName
                unresolvedNames.remove(name)
                val removed = serversByKey.entries.removeAll { it.value.instanceName == name }
                if (removed) publish()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped: $serviceType")
                discovering.set(false)
                discoveryListener = null
                if (wanted.get() && pendingRestart) {
                    pendingRestart = false
                    // Small delay avoids FAILURE_ALREADY_ACTIVE on some devices.
                    mainHandler.postDelayed({ beginDiscovery() }, RESTART_GAP_MS)
                }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed: $errorCode")
                discovering.set(false)
                discoveryListener = null
                if (wanted.get()) {
                    mainHandler.postDelayed({ beginDiscovery() }, RESTART_GAP_MS)
                } else {
                    releaseMulticastLock()
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: $errorCode")
                discovering.set(false)
                discoveryListener = null
                if (wanted.get() && pendingRestart) {
                    pendingRestart = false
                    mainHandler.postDelayed({ beginDiscovery() }, RESTART_GAP_MS)
                }
            }
        }
        discoveryListener = listener
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "discoverServices failed", e)
            discovering.set(false)
            discoveryListener = null
            if (wanted.get()) {
                mainHandler.postDelayed({ beginDiscovery() }, RESTART_GAP_MS)
            } else {
                releaseMulticastLock()
            }
        }
    }

    private fun endDiscovery() {
        val listener = discoveryListener ?: run {
            discovering.set(false)
            return
        }
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (e: Exception) {
            Log.w(TAG, "stopServiceDiscovery", e)
            discovering.set(false)
            discoveryListener = null
            if (wanted.get() && pendingRestart) {
                pendingRestart = false
                mainHandler.postDelayed({ beginDiscovery() }, RESTART_GAP_MS)
            }
        }
    }

    private fun resolve(serviceInfo: NsdServiceInfo) {
        val name = serviceInfo.serviceName
        if (!unresolvedNames.add(name)) return

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Resolve failed for $name: $errorCode")
                unresolvedNames.remove(name)
            }

            override fun onServiceResolved(resolved: NsdServiceInfo) {
                unresolvedNames.remove(name)
                val host = resolveHost(resolved) ?: run {
                    Log.w(TAG, "No IPv4 for $name")
                    return
                }
                val port = resolved.port
                if (port <= 0) return
                val version = resolved.attributes["version"]?.toString(Charsets.UTF_8)
                val server = DiscoveredServer(
                    instanceName = resolved.serviceName,
                    host = host,
                    port = port,
                    version = version,
                )
                serversByKey[server.key] = server
                publish()
                Log.d(TAG, "Resolved: ${server.instanceName} -> ${server.baseUrl}")
            }
        }

        try {
            @Suppress("DEPRECATION")
            nsdManager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "resolveService failed", e)
            unresolvedNames.remove(name)
        }
    }

    private fun resolveHost(info: NsdServiceInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val addresses = info.hostAddresses
            val ipv4 = addresses.firstOrNull { it is Inet4Address }
            if (ipv4 != null) return ipv4.hostAddress
            return addresses.firstOrNull()?.hostAddress
        }
        @Suppress("DEPRECATION")
        val host = info.host ?: return null
        if (host is Inet4Address) return host.hostAddress
        return host.hostAddress
    }

    private fun publish() {
        _servers.value = serversByKey.values.sortedBy { it.instanceName }
    }

    private fun acquireMulticastLock() {
        if (multicastLock == null) {
            multicastLock = wifiManager?.createMulticastLock("aceshare-nsd")?.apply {
                setReferenceCounted(false)
            }
        }
        try {
            if (multicastLock?.isHeld != true) {
                multicastLock?.acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Multicast lock", e)
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Release multicast", e)
        }
    }

    companion object {
        private const val TAG = "NsdServerDiscovery"
        /** Android NsdManager service type for AceShare. */
        const val SERVICE_TYPE = "_aceshare._tcp."
        private const val SERVICE_TYPE_TOKEN = "_aceshare._tcp"
        /** How often to soft-restart discovery while searching. */
        private const val REFRESH_INTERVAL_MS = 4_000L
        private const val RESTART_GAP_MS = 300L
        private const val SUPPRESS_LOST_WINDOW_MS = 2_500L
    }
}
