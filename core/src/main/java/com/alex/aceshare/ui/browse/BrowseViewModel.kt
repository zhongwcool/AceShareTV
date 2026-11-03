package com.alex.aceshare.ui.browse

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.aceshare.core.R
import com.alex.aceshare.data.AceShareRepository
import com.alex.aceshare.data.BrowseContentState
import com.alex.aceshare.data.DiscoveredServer
import com.alex.aceshare.data.FileCategory
import com.alex.aceshare.data.FileKinds
import com.alex.aceshare.data.FileMeta
import com.alex.aceshare.data.ListResponse
import com.alex.aceshare.data.MAX_UPLOAD_BYTES
import com.alex.aceshare.data.MAX_UPLOAD_FILES
import com.alex.aceshare.data.PreviewBytes
import com.alex.aceshare.data.PreviewFileResult
import com.alex.aceshare.data.ServerPreferences
import com.alex.aceshare.data.TextPreview
import com.alex.aceshare.data.UploadException
import com.alex.aceshare.data.UploadedFile
import com.alex.aceshare.data.decodePreviewText
import com.alex.aceshare.data.decodeShareImage
import com.alex.aceshare.data.previewKey
import com.alex.aceshare.data.stamp
import com.alex.aceshare.data.uploadFilesFromUris
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import com.alex.aceshare.discovery.NsdServerDiscovery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PendingFileDelete(
    val job: Job,
    val deadlineMs: Long,
)

private const val FILE_DELETE_UNDO_MS = 5_000L
private const val FAILED_PREVIEW_RETRY_MS = 120_000L

data class UndoDeletePrompt(
    val name: String,
    val secondsLeft: Int,
)

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

    private val deleteJobs = LinkedHashMap<String, PendingFileDelete>()
    private val deleteLock = Any()
    private var countdownJob: Job? = null
    private val _pendingDeletedFiles = MutableStateFlow<List<String>>(emptyList())
    val pendingDeletedFiles: StateFlow<List<String>> = _pendingDeletedFiles.asStateFlow()
    private val _undoDelete = MutableStateFlow<UndoDeletePrompt?>(null)
    val undoDelete: StateFlow<UndoDeletePrompt?> = _undoDelete.asStateFlow()
    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()

    private val previewBitmaps = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount.coerceAtLeast(1)
    }
    private val failedPreviews = ConcurrentHashMap<String, Long>()
    private val metaLock = Any()
    private var metaSource: ListResponse? = null
    private var metaByName: Map<String, FileMeta> = emptyMap()

    /** Bumped only by a manual refresh, so tiles retry loads that failed earlier. */
    private val _previewRevision = MutableStateFlow(0)
    val previewRevision: StateFlow<Int> = _previewRevision.asStateFlow()

    /** Keyed by [previewKey], so a replaced file does not reuse the old duration. */
    private val _mediaDurations = MutableStateFlow<Map<String, Long>>(emptyMap())
    val mediaDurations: StateFlow<Map<String, Long>> = _mediaDurations.asStateFlow()

    val current: DiscoveredServer get() = currentServer

    init {
        discovery.start()
        connect(initialServer)
    }

    fun connect(server: DiscoveredServer) {
        if (currentServer.key != server.key) {
            abandonUpload()
            cancelPendingFileDeletes()
            previewBitmaps.evictAll()
            failedPreviews.clear()
            _mediaDurations.value = emptyMap()
        }
        currentServer = server
        viewModelScope.launch {
            preferences.saveServer(server)
        }
        repository.connect(server.baseUrl)
    }

    fun retry() {
        failedPreviews.clear()
        _previewRevision.value++
        repository.refresh()
    }

    fun deleteFile(name: String) {
        if (name.isBlank()) return
        val deadlineMs = SystemClock.elapsedRealtime() + FILE_DELETE_UNDO_MS
        val job = viewModelScope.launch {
            delay(FILE_DELETE_UNDO_MS)
            val stillPending = synchronized(deleteLock) {
                val removed = deleteJobs.remove(name) != null
                if (removed) publishPendingDeletes()
                removed
            }
            if (!stillPending) return@launch
            try {
                repository.deleteItem("file", name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _deleteError.value = e.message?.takeIf { it.isNotBlank() } ?: "删除失败"
            }
            repository.refresh()
        }
        synchronized(deleteLock) {
            if (deleteJobs.containsKey(name)) {
                job.cancel()
                return
            }
            deleteJobs[name] = PendingFileDelete(job, deadlineMs)
            publishPendingDeletes()
            ensureCountdown()
        }
    }

    fun undoDeleteFile(name: String? = null) {
        synchronized(deleteLock) {
            val target = name ?: deleteJobs.keys.lastOrNull() ?: return
            deleteJobs.remove(target)?.job?.cancel()
            publishPendingDeletes()
        }
    }

    fun dismissDeleteError() {
        _deleteError.value = null
    }

    fun downloadFileUrl(name: String): String = fileUrl(name, inline = false)

    fun inlineFileUrl(name: String): String = fileUrl(name, inline = true)

    suspend fun loadPreviewBitmap(name: String, targetPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val px = targetPx.coerceIn(1, 1600)
        val meta = metaOf(name)
        val fileKey = "${currentServer.key}|${previewKey(name, meta.stamp())}"
        val cacheKey = "$fileKey|$px"
        previewBitmaps.get(cacheKey)?.takeIf { !it.isRecycled }?.let { return@withContext it }
        val failKey = "$fileKey|image"
        if (failedRecently(failKey)) return@withContext null
        val bitmap = if (FileKinds.categoryOf(name) == FileCategory.Video) {
            val media = repository.probeMedia(inlineFileUrl(name), meta?.size ?: -1L, px, wantFrame = true)
            rememberDuration(name, meta, media.durationMs)
            media.frame
        } else {
            val body = repository.readPreviewBytes(
                inlineFileUrl(name),
                FileKinds.BINARY_PREVIEW_MAX_BYTES,
            )
            (body as? PreviewBytes.Ready)?.bytes?.let { decodeShareImage(it, name, px) }
        }
        if (bitmap == null) {
            markFailed(failKey)
        } else {
            previewBitmaps.put(cacheKey, bitmap)
        }
        bitmap
    }

    suspend fun loadMediaDuration(name: String) = withContext(Dispatchers.IO) {
        val meta = metaOf(name)
        val key = previewKey(name, meta.stamp())
        if (_mediaDurations.value.containsKey(key)) return@withContext
        val failKey = "${currentServer.key}|$key|duration"
        if (failedRecently(failKey)) return@withContext
        val media = repository.probeMedia(inlineFileUrl(name), meta?.size ?: -1L, 1, wantFrame = false)
        if (media.durationMs > 0L) {
            rememberDuration(name, meta, media.durationMs)
        } else {
            markFailed(failKey)
        }
    }

    suspend fun loadTextPreview(name: String): TextPreview {
        val body = repository.readPreviewBytes(inlineFileUrl(name), FileKinds.TEXT_PREVIEW_MAX_BYTES.toLong())
        return when (body) {
            is PreviewBytes.Ready -> TextPreview.Ready(decodePreviewText(body.bytes))
            PreviewBytes.TooLarge -> TextPreview.TooLarge
            PreviewBytes.Failed -> TextPreview.Failed
        }
    }

    suspend fun downloadPreviewFile(name: String, dest: File): PreviewFileResult {
        return repository.downloadPreviewFile(
            inlineFileUrl(name),
            dest,
            FileKinds.BINARY_PREVIEW_MAX_BYTES,
        )
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

    private fun cancelPendingFileDeletes() {
        synchronized(deleteLock) {
            deleteJobs.values.forEach { it.job.cancel() }
            deleteJobs.clear()
            publishPendingDeletes()
        }
    }

    private fun publishPendingDeletes() {
        _pendingDeletedFiles.value = deleteJobs.keys.toList()
        val last = deleteJobs.entries.lastOrNull()
        _undoDelete.value = last?.let { (fileName, pending) ->
            UndoDeletePrompt(fileName, secondsLeft(pending.deadlineMs).coerceAtLeast(1))
        }
    }

    private fun ensureCountdown() {
        if (countdownJob?.isActive == true) return
        countdownJob = viewModelScope.launch {
            while (isActive) {
                val pending = synchronized(deleteLock) {
                    publishPendingDeletes()
                    deleteJobs.isNotEmpty()
                }
                if (!pending) break
                delay(200)
            }
        }
    }

    private fun secondsLeft(deadlineMs: Long): Int {
        val remaining = deadlineMs - SystemClock.elapsedRealtime()
        if (remaining <= 0L) return 0
        return ((remaining + 999L) / 1000L).toInt()
    }

    private fun failedRecently(key: String): Boolean {
        val at = failedPreviews[key] ?: return false
        if (SystemClock.elapsedRealtime() - at < FAILED_PREVIEW_RETRY_MS) return true
        failedPreviews.remove(key, at)
        return false
    }

    private fun markFailed(key: String) {
        failedPreviews[key] = SystemClock.elapsedRealtime()
    }

    private fun metaOf(name: String): FileMeta? = synchronized(metaLock) {
        val data = (repository.content.value as? BrowseContentState.Ready)?.data ?: return null
        if (data !== metaSource) {
            metaSource = data
            metaByName = data.fileMeta.associateBy { it.name }
        }
        metaByName[name]
    }

    private fun rememberDuration(name: String, meta: FileMeta?, durationMs: Long) {
        if (durationMs <= 0L) return
        val key = previewKey(name, meta.stamp())
        _mediaDurations.update { current ->
            if (current[key] == durationMs) current else current + (key to durationMs)
        }
    }

    private fun fileUrl(name: String, inline: Boolean): String {
        val url = "${currentServer.baseUrl.trimEnd('/')}/files/${Uri.encode(name)}"
        return if (inline) "$url?inline=1" else url
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
