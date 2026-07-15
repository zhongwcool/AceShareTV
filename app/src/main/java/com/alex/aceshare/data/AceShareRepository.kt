package com.alex.aceshare.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds
sealed interface BrowseContentState {
    data object Idle : BrowseContentState
    data object Loading : BrowseContentState
    data class Ready(val data: ListResponse) : BrowseContentState
    data class Error(val message: String) : BrowseContentState
}

class AceShareRepository(
    private val api: AceShareApi = AceShareApi(),
    private val sseClient: SseClient = SseClient(),
    private val scope: CoroutineScope,
) {
    private val _content = MutableStateFlow<BrowseContentState>(BrowseContentState.Idle)
    val content: StateFlow<BrowseContentState> = _content.asStateFlow()

    private var baseUrl: String? = null
    private var refreshJob: Job? = null
    private var sseJob: Job? = null
    private var pollJob: Job? = null

    fun connect(baseUrl: String) {
        if (this.baseUrl == baseUrl && _content.value is BrowseContentState.Ready) {
            return
        }
        disconnect()
        this.baseUrl = baseUrl
        _content.value = BrowseContentState.Loading
        refresh()
        startSse(baseUrl)
        startPolling(baseUrl)
    }

    fun refresh() {
        val url = baseUrl ?: return
        refreshJob?.cancel()
        refreshJob = scope.launch {
            runCatching { api.fetchList(url) }
                .onSuccess { list ->
                    _content.value = BrowseContentState.Ready(list)
                }
                .onFailure { e ->
                    if (_content.value !is BrowseContentState.Ready) {
                        _content.value = BrowseContentState.Error(
                            e.message ?: "Connection failed",
                        )
                    }
                }
        }
    }

    fun disconnect() {
        refreshJob?.cancel()
        sseJob?.cancel()
        pollJob?.cancel()
        refreshJob = null
        sseJob = null
        pollJob = null
        baseUrl = null
        _content.value = BrowseContentState.Idle
    }

    private fun startSse(url: String) {
        sseJob = scope.launch {
            var attempt = 0
            while (isActive) {
                try {
                    sseClient.observeChanges(url)
                        .catch { /* reconnect below */ }
                        .collect {
                            attempt = 0
                            refresh()
                        }
                } catch (_: Exception) {
                    // fall through to backoff
                }
                attempt++
                val delayMs = min(30_000L, (1L shl min(attempt, 5)) * 500L)
                delay(delayMs)
            }
        }
    }

    private fun startPolling(url: String) {
        pollJob = scope.launch {
            while (isActive) {
                delay(8.seconds)
                if (baseUrl == url) {
                    runCatching { api.fetchList(url) }
                        .onSuccess { list ->
                            _content.value = BrowseContentState.Ready(list)
                        }
                }
            }
        }
    }
}
