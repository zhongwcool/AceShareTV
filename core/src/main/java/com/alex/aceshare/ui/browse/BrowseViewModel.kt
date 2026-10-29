package com.alex.aceshare.ui.browse

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.aceshare.core.R
import com.alex.aceshare.data.AceShareRepository
import com.alex.aceshare.data.BrowseContentState
import com.alex.aceshare.data.DiscoveredServer
import com.alex.aceshare.data.MAX_UPLOAD_BYTES
import com.alex.aceshare.data.MAX_UPLOAD_FILES
import com.alex.aceshare.data.ServerPreferences
import com.alex.aceshare.data.UploadException
import com.alex.aceshare.data.UploadedFile
import com.alex.aceshare.data.uploadFilesFromUris
import com.alex.aceshare.discovery.NsdServerDiscovery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UploadUiState {
    data object Idle : UploadUiState
    data class Uploading(
        val fileName: String,
        val index: Int,
        val count: Int,
        val bytesSent: Long,
        val bytesTotal: Long,
    ) : UploadUiState

    data class Success(val files: List<UploadedFile>) : UploadUiState
    data class Failure(val message: String) : UploadUiState
}

class BrowseViewModel(
    private val app: Application,
    private val repository: AceShareRepository,
    private val discovery: NsdServerDiscovery,
    private val preferences: ServerPreferences,
    initialServer: DiscoveredServer,
) : ViewModel() {
    private var currentServer = initialServer
    private var uploadJob: Job? = null
    private val uploadLock = Any()
    private var uploadGeneration = 0

    val content: StateFlow<BrowseContentState> = repository.content
    val discoveredServers: StateFlow<List<DiscoveredServer>> = discovery.servers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _upload = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val upload: StateFlow<UploadUiState> = _upload.asStateFlow()

    val current: DiscoveredServer get() = currentServer

    init {
        discovery.start()
        connect(initialServer)
    }

    fun connect(server: DiscoveredServer) {
        if (currentServer.key != server.key) {
            abandonUpload()
        }
        currentServer = server
        viewModelScope.launch {
            preferences.saveServer(server)
        }
        repository.connect(server.baseUrl)
    }

    fun retry() {
        repository.refresh()
    }

    fun upload(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val generation = synchronized(uploadLock) {
            if (uploadJob?.isActive == true) return
            ++uploadGeneration
        }
        uploadJob = viewModelScope.launch {
            val files = try {
                withContext(Dispatchers.IO) {
                    uploadFilesFromUris(app.contentResolver, uris)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UploadException) {
                publishUpload(
                    generation,
                    UploadUiState.Failure(
                        e.message ?: app.getString(R.string.upload_failed_generic),
                    ),
                )
                return@launch
            }
            if (files.isEmpty()) {
                publishUpload(generation, UploadUiState.Failure(app.getString(R.string.upload_none)))
                return@launch
            }
            if (files.size > MAX_UPLOAD_FILES) {
                publishUpload(
                    generation,
                    UploadUiState.Failure(app.getString(R.string.upload_too_many, MAX_UPLOAD_FILES)),
                )
                return@launch
            }
            var knownBytes = 0L
            var totalKnown = true
            for (file in files) {
                if (file.size < 0L) {
                    totalKnown = false
                    continue
                }
                if (file.size > MAX_UPLOAD_BYTES || knownBytes > MAX_UPLOAD_BYTES - file.size) {
                    publishUpload(
                        generation,
                        UploadUiState.Failure(app.getString(R.string.upload_too_large)),
                    )
                    return@launch
                }
                knownBytes += file.size
            }
            val bytesTotal = if (totalKnown) knownBytes else -1L
            publishUpload(
                generation,
                UploadUiState.Uploading(
                    fileName = files.first().displayName,
                    index = 1,
                    count = files.size,
                    bytesSent = 0L,
                    bytesTotal = bytesTotal,
                ),
            )
            try {
                val saved = repository.uploadFiles(files) { progress ->
                    publishUpload(
                        generation,
                        UploadUiState.Uploading(
                            fileName = progress.fileName,
                            index = progress.index,
                            count = progress.count,
                            bytesSent = progress.bytesSent,
                            bytesTotal = progress.bytesTotal,
                        ),
                    )
                }
                publishUpload(generation, UploadUiState.Success(saved.files))
            } catch (e: CancellationException) {
                publishUpload(generation, UploadUiState.Idle)
                throw e
            } catch (e: UploadException) {
                publishUpload(
                    generation,
                    UploadUiState.Failure(
                        e.message ?: app.getString(R.string.upload_failed_generic),
                    ),
                )
            } catch (_: Exception) {
                publishUpload(
                    generation,
                    UploadUiState.Failure(app.getString(R.string.upload_network)),
                )
            } finally {
                if (isActive) repository.refresh()
            }
        }
    }

    fun cancelUpload() {
        abandonUpload()
        repository.refresh()
    }

    private fun abandonUpload() {
        synchronized(uploadLock) {
            uploadGeneration++
            _upload.value = UploadUiState.Idle
        }
        uploadJob?.cancel()
    }

    fun dismissUploadResult() {
        synchronized(uploadLock) {
            if (_upload.value is UploadUiState.Uploading) return
            _upload.value = UploadUiState.Idle
        }
    }

    private fun publishUpload(generation: Int, state: UploadUiState) {
        synchronized(uploadLock) {
            if (generation != uploadGeneration) return
            _upload.value = state
        }
    }

    override fun onCleared() {
        abandonUpload()
        repository.disconnect()
        super.onCleared()
    }

    class Factory(
        private val app: Application,
        private val repository: AceShareRepository,
        private val discovery: NsdServerDiscovery,
        private val preferences: ServerPreferences,
        private val initialServer: DiscoveredServer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BrowseViewModel(app, repository, discovery, preferences, initialServer) as T
        }
    }
}
