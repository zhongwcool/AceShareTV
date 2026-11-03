package com.alex.aceshare.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.net.Proxy
import kotlin.coroutines.coroutineContext
import java.util.concurrent.TimeUnit

class AceShareApi(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = defaultJson(),
) {
    private val uploadClient: OkHttpClient = client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.MINUTES)
        .readTimeout(2, TimeUnit.MINUTES)
        .build()
    private val previewClient: OkHttpClient = client.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    val mediaClient: OkHttpClient = client.newBuilder()
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    suspend fun fetchList(baseUrl: String): ListResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/list")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body?.string()
                ?: throw IllegalStateException("Empty body")
            json.decodeFromString(ListResponse.serializer(), body)
        }
    }

    suspend fun fetchVersion(baseUrl: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/version")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                json.decodeFromString(VersionResponse.serializer(), body).version
            }
        }.getOrNull()
    }

    suspend fun uploadFiles(
        baseUrl: String,
        files: List<UploadFile>,
        onProgress: (UploadProgress) -> Unit,
    ): UploadResponse = withContext(Dispatchers.IO) {
        if (files.isEmpty()) throw UploadException("没有可上传的文件")
        if (files.size > MAX_UPLOAD_FILES) {
            throw UploadException("一次最多上传 $MAX_UPLOAD_FILES 个文件")
        }
        val bytesTotal = uploadBytesTotal(files)
        val job = coroutineContext.job
        var lastReportNs = 0L
        var completedBytes = 0L

        fun report(progress: UploadProgress, force: Boolean) {
            val now = System.nanoTime()
            if (!force && now - lastReportNs < 200_000_000L) return
            lastReportNs = now
            onProgress(progress)
        }

        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
        files.forEachIndexed { index, file ->
            val body = object : RequestBody() {
                override fun contentType(): MediaType = OCTET_STREAM

                override fun contentLength(): Long = -1L

                override fun isOneShot(): Boolean = true

                override fun writeTo(sink: BufferedSink) {
                    var sent = 0L
                    try {
                        file.openStream().use { input ->
                            val buffer = ByteArray(UPLOAD_BUFFER_BYTES)
                            while (true) {
                                job.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                sink.write(buffer, 0, read)
                                sent += read
                                report(
                                    UploadProgress(
                                        fileName = file.displayName,
                                        index = index + 1,
                                        count = files.size,
                                        bytesSent = completedBytes + sent,
                                        bytesTotal = bytesTotal,
                                    ),
                                    force = false,
                                )
                            }
                        }
                    } catch (e: UploadException) {
                        throw e
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: FileNotFoundException) {
                        throw UploadException("无法读取「${file.displayName}」")
                    } catch (_: SecurityException) {
                        throw UploadException("无法读取「${file.displayName}」")
                    }
                    report(
                        UploadProgress(
                            fileName = file.displayName,
                            index = index + 1,
                            count = files.size,
                            bytesSent = completedBytes + sent,
                            bytesTotal = bytesTotal,
                        ),
                        force = true,
                    )
                    completedBytes += sent
                }
            }
            multipart.addPart(
                MultipartBody.Part.create(
                    Headers.headersOf("Content-Disposition", fileContentDisposition(file.displayName)),
                    body,
                ),
            )
        }

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/upload")
            .post(multipart.build())
            .build()
        val call = uploadClient.newCall(request)
        coroutineContext.job.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            call.execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw UploadException(httpUploadError(response.code, text))
                }
                val parsed = try {
                    json.decodeFromString(UploadResponse.serializer(), text)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    throw UploadException("上传结果无法识别")
                }
                if (parsed.files.isEmpty()) {
                    throw UploadException("没有可保存的文件")
                }
                parsed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: UploadException) {
            throw e
        } catch (e: IOException) {
            val uploadFailure = e as? UploadException ?: e.cause as? UploadException
            if (uploadFailure != null) throw uploadFailure
            if (!isActive) throw CancellationException("upload cancelled", e)
            throw UploadException(networkUploadError(e))
        }
    }

    suspend fun deleteItem(baseUrl: String, kind: String, name: String) = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(DeleteBody.serializer(), DeleteBody(kind, name))
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/delete")
            .post(payload.toRequestBody(JSON_UTF8))
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) return@withContext
            val text = response.body.string().trim()
            val message = text.lineSequence().firstOrNull()?.trim().orEmpty()
            if (message.isNotEmpty() && message.length <= 200 && !message.startsWith("<")) {
                throw IllegalStateException(message)
            }
            throw IllegalStateException("删除失败")
        }
    }

    suspend fun readCappedBytes(url: String, maxBytes: Long): PreviewBytes = withContext(Dispatchers.IO) {
        if (maxBytes <= 0L) return@withContext PreviewBytes.Failed
        val call = previewClient.newCall(Request.Builder().url(url).get().build())
        coroutineContext.job.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) return@withContext PreviewBytes.Failed
                val body = response.body ?: return@withContext PreviewBytes.Failed
                val length = body.contentLength()
                if (length > maxBytes) return@withContext PreviewBytes.TooLarge
                body.byteStream().use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) return@withContext PreviewBytes.TooLarge
                        output.write(buffer, 0, read)
                    }
                    PreviewBytes.Ready(output.toByteArray())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            if (!isActive) throw CancellationException("preview cancelled", e)
            PreviewBytes.Failed
        }
    }

    suspend fun downloadCapped(url: String, dest: File, maxBytes: Long): PreviewFileResult =
        withContext(Dispatchers.IO) {
            if (maxBytes <= 0L) return@withContext PreviewFileResult.Failed
            val parent = dest.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return@withContext PreviewFileResult.Failed
            }
            val call = previewClient.newCall(Request.Builder().url(url).get().build())
            coroutineContext.job.invokeOnCompletion { cause ->
                if (cause != null) call.cancel()
            }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        dest.delete()
                        return@withContext PreviewFileResult.Failed
                    }
                    val body = response.body
                    if (body == null) {
                        dest.delete()
                        return@withContext PreviewFileResult.Failed
                    }
                    val length = body.contentLength()
                    if (length > maxBytes) {
                        dest.delete()
                        return@withContext PreviewFileResult.TooLarge
                    }
                    body.byteStream().use { input ->
                        FileOutputStream(dest).use { output ->
                            val buffer = ByteArray(8192)
                            var total = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > maxBytes) {
                                    output.close()
                                    dest.delete()
                                    return@withContext PreviewFileResult.TooLarge
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    PreviewFileResult.Saved
                }
            } catch (e: CancellationException) {
                dest.delete()
                throw e
            } catch (e: IOException) {
                dest.delete()
                if (!isActive) throw CancellationException("preview cancelled", e)
                PreviewFileResult.Failed
            }
        }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private val OCTET_STREAM = "application/octet-stream".toMediaType()
        private val JSON_UTF8 = "application/json; charset=utf-8".toMediaType()
        private const val UPLOAD_BUFFER_BYTES = 64 * 1024
    }
}

private fun uploadBytesTotal(files: List<UploadFile>): Long {
    var known = 0L
    for (file in files) {
        if (file.size < 0L) return -1L
        if (known > Long.MAX_VALUE - file.size) return -1L
        known += file.size
    }
    return known
}

private fun httpUploadError(code: Int, raw: String): String {
    val text = raw.trim().lineSequence().firstOrNull()?.trim().orEmpty()
    if (text.isNotEmpty() && text.length <= 200 && !text.startsWith("<")) {
        return text
    }
    return when (code) {
        404, 405 -> "服务器不支持上传，请更新 AceShare"
        413 -> "文件过大"
        else -> "上传失败（HTTP $code）"
    }
}

@Serializable
private data class DeleteBody(val kind: String, val name: String)

private fun networkUploadError(error: IOException): String {
    val message = error.message.orEmpty()
    return if (message.contains("timeout", ignoreCase = true)) {
        "上传超时，请重试"
    } else {
        "无法连接到服务器"
    }
}
