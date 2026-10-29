package com.alex.aceshare.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.FileNotFoundException
import java.io.IOException
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

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private val OCTET_STREAM = "application/octet-stream".toMediaType()
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

private fun networkUploadError(error: IOException): String {
    val message = error.message.orEmpty()
    return if (message.contains("timeout", ignoreCase = true)) {
        "上传超时，请重试"
    } else {
        "无法连接到服务器"
    }
}
