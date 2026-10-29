package com.alex.aceshare.data

import kotlinx.serialization.Serializable

@Serializable
data class TextItem(
    val title: String,
    val text: String,
)

@Serializable
data class ListResponse(
    val files: List<String> = emptyList(),
    val lines: List<TextItem> = emptyList(),
    val texts: List<TextItem> = emptyList(),
)

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
