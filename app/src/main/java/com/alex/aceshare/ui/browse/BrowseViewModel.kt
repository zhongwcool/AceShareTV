package com.alex.aceshare.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.aceshare.data.AceShareRepository
import com.alex.aceshare.data.BrowseContentState
import com.alex.aceshare.data.DiscoveredServer
import com.alex.aceshare.data.ServerPreferences
import com.alex.aceshare.discovery.NsdServerDiscovery
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BrowseViewModel(
    private val repository: AceShareRepository,
    private val discovery: NsdServerDiscovery,
    private val preferences: ServerPreferences,
    initialServer: DiscoveredServer,
) : ViewModel() {
    private var currentServer = initialServer

    val content: StateFlow<BrowseContentState> = repository.content
    val discoveredServers: StateFlow<List<DiscoveredServer>> = discovery.servers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val current: DiscoveredServer get() = currentServer

    init {
        discovery.start()
        connect(initialServer)
    }

    fun connect(server: DiscoveredServer) {
        currentServer = server
        viewModelScope.launch {
            preferences.saveServer(server)
        }
        repository.connect(server.baseUrl)
    }

    fun retry() {
        repository.refresh()
    }

    override fun onCleared() {
        repository.disconnect()
        super.onCleared()
    }

    class Factory(
        private val repository: AceShareRepository,
        private val discovery: NsdServerDiscovery,
        private val preferences: ServerPreferences,
        private val initialServer: DiscoveredServer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BrowseViewModel(repository, discovery, preferences, initialServer) as T
        }
    }
}
