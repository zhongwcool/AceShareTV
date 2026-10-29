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
import com.alex.aceshare.data.VersionResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class NsdServerDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val serversByKey = ConcurrentHashMap<String, DiscoveredServer>()
    private val unresolvedNames = ConcurrentHashMap.newKeySet<String>()
    private val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var probeJob: Job? = null
    private val probeJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val probeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

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
        startEmulatorProbe()
    }

    fun stop() {
        if (!wanted.compareAndSet(true, false)) return
        mainHandler.removeCallbacks(refreshRunnable)
        pendingRestart = false
        endDiscovery()
        unresolvedNames.clear()
        releaseMulticastLock()
        stopEmulatorProbe()
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

    /**
     * The emulator virtual router does not forward mDNS. 10.0.2.2 is the host
     * loopback alias, so probe the AceShare ports the desktop build listens on.
     */
    private fun startEmulatorProbe() {
        if (!isEmulator()) return
        probeJob?.cancel()
        probeJob = probeScope.launch {
            while (isActive && wanted.get()) {
                val server = probeEmulatorHost()
                if (!isActive || !wanted.get()) return@launch
                publishEmulatorServer(server)
                delay(PROBE_INTERVAL_MS)
            }
        }
    }

    private fun stopEmulatorProbe() {
        probeJob?.cancel()
        probeJob = null
    }

    private fun probeEmulatorHost(): DiscoveredServer? {
        for (port in EMULATOR_HOST_PORTS) {
            probeHostPort(port)?.let { return it }
        }
        return null
    }

    private fun probeHostPort(port: Int): DiscoveredServer? {
        val baseUrl = "http://$EMULATOR_HOST:$port"
        val version = httpGet("$baseUrl/api/version")?.let(::parseVersion)
        if (version != null) return emulatorServer(port, version)
        val listBody = httpGet("$baseUrl/api/list") ?: return null
        if (!looksLikeAceShareList(listBody)) return null
        return emulatorServer(port, version = null)
    }

    private fun emulatorServer(port: Int, version: String?) = DiscoveredServer(
        instanceName = "AceShare",
        host = EMULATOR_HOST,
        port = port,
        version = version,
    )

    private fun httpGet(url: String): String? {
        return try {
            val request = Request.Builder().url(url).get().build()
            probeClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body.string()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Probe $url failed: ${e.message}")
            null
        }
    }

    private fun parseVersion(body: String): String? {
        return try {
            probeJson.decodeFromString(VersionResponse.serializer(), body)
                .version
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun looksLikeAceShareList(body: String): Boolean {
        return try {
            val obj = probeJson.parseToJsonElement(body) as? JsonObject ?: return false
            "files" in obj || "lines" in obj || "texts" in obj
        } catch (_: Exception) {
            false
        }
    }

    private fun publishEmulatorServer(server: DiscoveredServer?) {
        var changed = false
        val hostPrefix = "$EMULATOR_HOST:"
        if (server == null) {
            for (key in serversByKey.keys.filter { it.startsWith(hostPrefix) }) {
                if (serversByKey.remove(key) != null) changed = true
            }
        } else {
            if (serversByKey.put(server.key, server) != server) changed = true
            for (key in serversByKey.keys.filter { it.startsWith(hostPrefix) && it != server.key }) {
                if (serversByKey.remove(key) != null) changed = true
            }
        }
        if (changed && wanted.get()) {
            Log.d(TAG, "Emulator host: ${server?.baseUrl ?: "gone"}")
            publish()
        }
    }

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT
        val model = Build.MODEL
        val product = Build.PRODUCT
        return Build.HARDWARE == "ranchu" ||
            Build.HARDWARE == "goldfish" ||
            fingerprint.contains("generic") ||
            fingerprint.contains("emulator") ||
            fingerprint.contains("sdk_gphone") ||
            model.contains("Emulator") ||
            model.contains("Android SDK built for") ||
            model.contains("sdk_gphone") ||
            product.contains("sdk_gphone") ||
            product.contains("emulator") ||
            product.contains("simulator")
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
        private const val PROBE_INTERVAL_MS = 4_000L
        private const val EMULATOR_HOST = "10.0.2.2"
        private val EMULATOR_HOST_PORTS = intArrayOf(80, 8000)
    }
}
