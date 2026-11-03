package com.alex.aceshare.data

import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

private const val MEDIA_TIMEOUT_MS = 20_000L
private const val MEDIA_BLOCK_BYTES = 256 * 1024
private const val MEDIA_READ_BUDGET_BYTES = 24L * 1024 * 1024

// MediaMetadataRetriever blocks its thread; two workers keep one slow file from stalling the grid.
private val mediaExecutor = Executors.newFixedThreadPool(2) { task ->
    Thread(task, "ace-media").apply { isDaemon = true }
}

private val frameTimesUs = longArrayOf(1_000_000L, 100_000L, 0L, -1L)

class RemoteMedia(
    val frame: Bitmap?,
    val durationMs: Long,
)

suspend fun loadRemoteMedia(
    client: OkHttpClient,
    url: String,
    knownSize: Long,
    targetPx: Int,
    wantFrame: Boolean,
): RemoteMedia {
    val source = HttpMediaDataSource(client, url, knownSize)
    try {
        val media = withTimeoutOrNull(MEDIA_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val future = mediaExecutor.submit {
                    val result = readRemoteMedia(source, targetPx, wantFrame)
                    if (cont.isActive) cont.resume(result) else result.frame?.recycle()
                }
                cont.invokeOnCancellation {
                    source.cancel()
                    future.cancel(false)
                }
            }
        }
        return media ?: RemoteMedia(frame = null, durationMs = -1L)
    } finally {
        source.cancel()
    }
}

private fun readRemoteMedia(source: MediaDataSource, targetPx: Int, wantFrame: Boolean): RemoteMedia {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(source)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: -1L
        if (!wantFrame) return RemoteMedia(frame = null, durationMs = duration)
        val px = targetPx.coerceIn(1, 1600)
        var raw: Bitmap? = null
        for (time in frameTimesUs) {
            raw = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, px, px)
                } else {
                    retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            } catch (_: Exception) {
                null
            }
            if (raw != null) break
        }
        return RemoteMedia(frame = raw?.let { scaleFrame(it, px) }, durationMs = duration)
    } catch (_: Exception) {
        return RemoteMedia(frame = null, durationMs = -1L)
    } catch (_: OutOfMemoryError) {
        return RemoteMedia(frame = null, durationMs = -1L)
    } finally {
        try {
            retriever.release()
        } catch (_: Exception) {
            // The retriever can already be released after a failed data source.
        }
    }
}

private fun scaleFrame(bitmap: Bitmap, targetPx: Int): Bitmap {
    val longest = max(bitmap.width, bitmap.height)
    if (longest <= targetPx || longest <= 0) return bitmap
    val scale = targetPx.toFloat() / longest.toFloat()
    val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
    if (scaled != bitmap) bitmap.recycle()
    return scaled
}

/**
 * Reads the remote file with HTTP Range requests so OkHttp timeouts apply and a cancelled
 * load fails the retriever quickly instead of leaving it blocked on a socket.
 */
private class HttpMediaDataSource(
    private val client: OkHttpClient,
    private val url: String,
    knownSize: Long,
) : MediaDataSource() {
    private val lock = Any()
    @Volatile private var cancelled = false
    @Volatile private var call: Call? = null
    private var totalSize = knownSize
    private var blockStart = 0L
    private var block = ByteArray(0)
    private var fetched = 0L

    fun cancel() {
        cancelled = true
        call?.cancel()
    }

    override fun getSize(): Long = synchronized(lock) {
        if (totalSize < 0L) loadBlock(0L)
        totalSize
    }

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int = synchronized(lock) {
        if (size <= 0) return 0
        if (totalSize in 0..position) return -1
        if (position < blockStart || position >= blockStart + block.size) loadBlock(position)
        val start = position - blockStart
        if (start < 0L || start >= block.size) return -1
        val count = min(size.toLong(), block.size - start).toInt()
        System.arraycopy(block, start.toInt(), buffer, offset, count)
        count
    }

    override fun close() {
        cancel()
    }

    private fun loadBlock(position: Long) {
        if (cancelled) throw IOException("cancelled")
        if (fetched >= MEDIA_READ_BUDGET_BYTES) throw IOException("read budget exceeded")
        val end = position + MEDIA_BLOCK_BYTES - 1
        val next = client.newCall(
            Request.Builder()
                .url(url)
                .header("Range", "bytes=$position-$end")
                .build(),
        )
        call = next
        if (cancelled) next.cancel()
        next.execute().use { response ->
            val body = response.body
            when (response.code) {
                206 -> {
                    parseRangeTotal(response.header("Content-Range"))?.let { totalSize = it }
                    block = readUpTo(body.byteStream(), MEDIA_BLOCK_BYTES)
                }
                200 -> {
                    val length = body.contentLength()
                    if (length >= 0L && totalSize < 0L) totalSize = length
                    val input = body.byteStream()
                    skipFully(input, position)
                    fetched += position
                    block = readUpTo(input, MEDIA_BLOCK_BYTES)
                }
                416 -> {
                    if (totalSize < 0L) totalSize = position
                    block = ByteArray(0)
                }
                else -> throw IOException("HTTP ${response.code}")
            }
        }
        blockStart = position
        fetched += block.size
    }
}

private fun parseRangeTotal(header: String?): Long? {
    return header?.substringAfterLast('/', "")?.trim()?.toLongOrNull()
}

private fun readUpTo(input: InputStream, limit: Int): ByteArray {
    val buffer = ByteArray(limit)
    var filled = 0
    while (filled < limit) {
        val read = input.read(buffer, filled, limit - filled)
        if (read < 0) break
        filled += read
    }
    return if (filled == limit) buffer else buffer.copyOf(filled)
}

private fun skipFully(input: InputStream, count: Long) {
    var remaining = count
    while (remaining > 0L) {
        val skipped = input.skip(remaining)
        if (skipped > 0L) {
            remaining -= skipped
        } else if (input.read() < 0) {
            throw IOException("unexpected end of stream")
        } else {
            remaining--
        }
    }
}
