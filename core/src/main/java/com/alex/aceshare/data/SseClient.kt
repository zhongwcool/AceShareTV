package com.alex.aceshare.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Emits Unit whenever the server signals a content change via SSE.
 */
class SseClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // SSE long-lived
        .retryOnConnectionFailure(true)
        .build(),
) {
    fun observeChanges(baseUrl: String): Flow<Unit> = callbackFlow {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/events")
            .header("Accept", "text/event-stream")
            .get()
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                if (type == null || type == "change" || type == "message") {
                    trySend(Unit)
                }
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?,
            ) {
                close(t ?: IllegalStateException("SSE failed: ${response?.code}"))
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }

        val eventSource = EventSources.createFactory(client)
            .newEventSource(request, listener)

        awaitClose { eventSource.cancel() }
    }
}
