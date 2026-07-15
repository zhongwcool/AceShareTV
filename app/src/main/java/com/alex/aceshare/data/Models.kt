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

data class DiscoveredServer(
    val instanceName: String,
    val host: String,
    val port: Int,
    val version: String? = null,
) {
    val baseUrl: String get() = "http://$host:$port"
    val key: String get() = "$host:$port"
}
