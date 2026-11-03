package com.alex.aceshare.data

import kotlinx.serialization.Serializable

@Serializable
data class TextItem(
    val title: String,
    val text: String,
)

@Serializable
data class FileMeta(
    val name: String,
    val size: Long = -1,
    val mtime: Long = 0,
)

/** Changes when the file is replaced; empty when the server does not send fileMeta. */
fun FileMeta?.stamp(): String = if (this == null) "" else "$size:$mtime"

fun previewKey(name: String, stamp: String): String = "$stamp|$name"

@Serializable
data class ListResponse(
    val files: List<String> = emptyList(),
    val fileMeta: List<FileMeta> = emptyList(),
    val lines: List<TextItem> = emptyList(),
    val texts: List<TextItem> = emptyList(),
) {
    fun stampOf(name: String): String = fileMeta.firstOrNull { it.name == name }.stamp()
}

@Serializable
data class VersionResponse(
    val version: String? = null,
    val commit: String? = null,
    val buildTime: String? = null,
)

@Serializable
data class UploadedFile(
    val name: String,
    val size: Long = 0,
)

@Serializable
data class UploadResponse(
    val files: List<UploadedFile> = emptyList(),
)

data class DiscoveredServer(
    val instanceName: String,
    val host: String,
    val port: Int,
    val version: String? = null,
) {
    val baseUrl: String get() = "http://$host:$port"
    val key: String get() = "$host:$port"
}
