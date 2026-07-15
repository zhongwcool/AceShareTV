package com.alex.aceshare.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.aceshare.data.DiscoveredServer
import com.alex.aceshare.data.ServerPreferences
import com.alex.aceshare.discovery.NsdServerDiscovery
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DiscoveryUiState(
    val servers: List<DiscoveredServer> = emptyList(),
    val searching: Boolean = true,
    val autoEnter: Boolean = true,
)

class DiscoveryViewModel(
    private val discovery: NsdServerDiscovery,
    private val preferences: ServerPreferences,
) : ViewModel() {
    private val _searching = MutableStateFlow(true)
    private var autoEnterJob: Job? = null
    private var lastAutoEnteredKey: String? = null

    val uiState: StateFlow<DiscoveryUiState> = combine(
        discovery.servers,
        preferences.autoEnter,
        _searching,
    ) { servers, autoEnter, searching ->
        DiscoveryUiState(
            servers = servers,
            searching = searching && servers.isEmpty(),
            autoEnter = autoEnter,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DiscoveryUiState(),
    )

    private val _navigateToServer = MutableSharedFlow<DiscoveredServer>(extraBufferCapacity = 1)
    val navigateToServer: SharedFlow<DiscoveredServer> = _navigateToServer.asSharedFlow()

    private val lastServer = MutableStateFlow<DiscoveredServer?>(null)

    init {
        viewModelScope.launch {
            preferences.lastServer.collect { lastServer.value = it }
        }
        viewModelScope.launch {
            discovery.servers.collect { servers ->
                maybeAutoEnter(servers)
            }
        }
        viewModelScope.launch {
            preferences.autoEnter.collect {
                maybeAutoEnter(discovery.servers.value)
            }
        }
    }

    fun onStart() {
        _searching.value = true
        discovery.start()
    }

    fun onStop() {
        discovery.stop()
    }

    fun retry() {
        lastAutoEnteredKey = null
        _searching.value = true
        discovery.restart()
    }

    fun selectServer(server: DiscoveredServer) {
        viewModelScope.launch {
            preferences.saveServer(server)
            _navigateToServer.emit(server)
        }
    }

    private fun maybeAutoEnter(servers: List<DiscoveredServer>) {
        autoEnterJob?.cancel()
        if (servers.isEmpty()) {
            lastAutoEnteredKey = null
            return
        }
        autoEnterJob = viewModelScope.launch {
            delay(50)
            val enabled = uiState.value.autoEnter
            if (!enabled) return@launch
            if (servers.size != 1) return@launch
            val server = servers.first()
            if (server.key == lastAutoEnteredKey) return@launch
            val last = lastServer.value
            val shouldEnter = last == null || last.key == server.key
            if (!shouldEnter) return@launch
            delay(800)
            if (discovery.servers.value.size != 1) return@launch
            lastAutoEnteredKey = server.key
            preferences.saveServer(server)
            _navigateToServer.emit(server)
        }
    }

    class Factory(
        private val discovery: NsdServerDiscovery,
        private val preferences: ServerPreferences,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DiscoveryViewModel(discovery, preferences) as T
        }
    }
}
