package com.alex.aceshare.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.aceshare.data.DiscoveredServer
import com.alex.aceshare.data.ServerPreferences
import com.alex.aceshare.data.update.AppUpdateInfo
import com.alex.aceshare.data.update.AppUpdater
import com.alex.aceshare.data.update.UpdateCheckResult
import com.alex.aceshare.discovery.NsdServerDiscovery
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoveryUiState(
    val servers: List<DiscoveredServer> = emptyList(),
    val searching: Boolean = true,
    val autoEnter: Boolean = true,
)

sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data class Available(
        val info: AppUpdateInfo,
        val currentVersionName: String,
    ) : UpdateUiState()
    data class Downloading(
        val info: AppUpdateInfo,
        val progress: Int,
    ) : UpdateUiState()
    data class Error(
        val messageRes: UpdateError,
        val info: AppUpdateInfo? = null,
    ) : UpdateUiState()
}

enum class UpdateError {
    DownloadFailed,
    InstallFailed,
    PermissionRequired,
}

class DiscoveryViewModel(
    private val discovery: NsdServerDiscovery,
    private val preferences: ServerPreferences,
    private val updateChecker: AppUpdater,
) : ViewModel() {
    private val _searching = MutableStateFlow(true)
    private var autoEnterJob: Job? = null
    private var lastAutoEnteredKey: String? = null
    private var updateChecked = false

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

    private val _updateUiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateUiState: StateFlow<UpdateUiState> = _updateUiState.asStateFlow()

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
        checkForUpdateIfNeeded()
    }

    fun onStop() {
        discovery.stop()
    }

    fun selectServer(server: DiscoveredServer) {
        viewModelScope.launch {
            preferences.saveServer(server)
            _navigateToServer.emit(server)
        }
    }

    fun dismissUpdate() {
        _updateUiState.value = UpdateUiState.Idle
    }

    fun startUpdate() {
        val current = _updateUiState.value
        val info = when (current) {
            is UpdateUiState.Available -> current.info
            is UpdateUiState.Error -> current.info ?: return
            is UpdateUiState.Downloading -> return
            UpdateUiState.Idle -> return
        }
        viewModelScope.launch {
            _updateUiState.value = UpdateUiState.Downloading(info, 0)
            val result = updateChecker.downloadAndInstall(info) { progress ->
                _updateUiState.update { state ->
                    if (state is UpdateUiState.Downloading) {
                        state.copy(progress = progress)
                    } else {
                        state
                    }
                }
            }
            result.fold(
                onSuccess = {
                    _updateUiState.value = UpdateUiState.Idle
                },
                onFailure = { error ->
                    val message = if (error.message == AppUpdater.PERMISSION_REQUIRED) {
                        UpdateError.PermissionRequired
                    } else {
                        UpdateError.DownloadFailed
                    }
                    _updateUiState.value = UpdateUiState.Error(message, info)
                },
            )
        }
    }

    private fun checkForUpdateIfNeeded() {
        if (updateChecked) return
        updateChecked = true
        viewModelScope.launch {
            when (val result = updateChecker.checkForUpdate()) {
                is UpdateCheckResult.Available -> {
                    autoEnterJob?.cancel()
                    _updateUiState.value = UpdateUiState.Available(
                        info = result.info,
                        currentVersionName = updateChecker.currentVersionName(),
                    )
                }
                UpdateCheckResult.UpToDate, UpdateCheckResult.Failed -> Unit
            }
        }
    }

    private fun maybeAutoEnter(servers: List<DiscoveredServer>) {
        autoEnterJob?.cancel()
        if (servers.isEmpty()) {
            lastAutoEnteredKey = null
            return
        }
        // Don't auto-enter while an update prompt is visible.
        if (_updateUiState.value !is UpdateUiState.Idle) return

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
            if (_updateUiState.value !is UpdateUiState.Idle) return@launch
            lastAutoEnteredKey = server.key
            preferences.saveServer(server)
            _navigateToServer.emit(server)
        }
    }

    class Factory(
        private val discovery: NsdServerDiscovery,
        private val preferences: ServerPreferences,
        private val updateChecker: AppUpdater,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DiscoveryViewModel(discovery, preferences, updateChecker) as T
        }
    }
}
