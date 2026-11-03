package com.alex.aceshare.data

import java.util.Locale

/**
 * 与网页端同一套扩展名规则：先归类，再决定能不能在页面里预览。
 * 图片、视频、音频用网格；其余用列表。压缩包和不能预览的文档只有下载。
 */
enum class FileCategory {
    Image,
    Video,
    Audio,
    Document,
    Text,
    Archive,
    Other,
}

enum class PreviewKind {
    Image,
    Video,
    Audio,
    Pdf,
    Text,
}

data class FileGroup(
    val category: FileCategory,
    val files: List<String>,
)

object FileKinds {
    const val TEXT_PREVIEW_MAX_BYTES = 1024 * 1024
    const val BINARY_PREVIEW_MAX_BYTES = 32L * 1024 * 1024
    const val PDF_MAX_PAGES = 16

    private val imageExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "ico", "avif", "heic", "heif", "tif", "tiff",
    )
    private val imagePreviewExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "ico", "avif",
    )
    private val videoExtensions = setOf(
        "mp4", "webm", "ogv", "mov", "mkv", "avi", "m4v", "wmv", "flv",
    )
    private val videoPreviewExtensions = setOf(
        "mp4", "webm", "ogv", "m4v", "mov",
    )
    private val audioExtensions = setOf(
        "mp3", "wav", "ogg", "oga", "m4a", "flac", "aac", "opus", "wma",
    )
    private val audioPreviewExtensions = setOf(
        "mp3", "wav", "ogg", "oga", "m4a", "flac", "aac", "opus",
    )
    private val documentExtensions = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf",
    )
    private val textExtensions = setOf(
        "txt", "md", "markdown", "json", "xml", "csv", "log", "yml", "yaml", "ini", "conf",
        "html", "htm", "css", "js", "ts", "go", "py", "java", "c", "h", "cpp", "rs", "sh", "bat", "ps1",
    )
    private val archiveExtensions = setOf(
        "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz",
    )

    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase(Locale.ROOT)
    }

    fun categoryOf(name: String): FileCategory {
        return when (extensionOf(name)) {
            in imageExtensions -> FileCategory.Image
            in videoExtensions -> FileCategory.Video
            in audioExtensions -> FileCategory.Audio
            in documentExtensions -> FileCategory.Document
            in textExtensions -> FileCategory.Text
            in archiveExtensions -> FileCategory.Archive
            else -> FileCategory.Other
        }
    }

    fun previewOf(name: String): PreviewKind? {
        return when (extensionOf(name)) {
            in imagePreviewExtensions -> PreviewKind.Image
            in videoPreviewExtensions -> PreviewKind.Video
            in audioPreviewExtensions -> PreviewKind.Audio
            "pdf" -> PreviewKind.Pdf
            in textExtensions -> PreviewKind.Text
            else -> null
        }
    }

    fun usesMediaGrid(category: FileCategory): Boolean {
        return category == FileCategory.Image ||
            category == FileCategory.Video ||
            category == FileCategory.Audio
    }

    fun groupFiles(files: List<String>): List<FileGroup> {
        if (files.isEmpty()) return emptyList()
        val buckets = linkedMapOf<FileCategory, MutableList<String>>()
        for (name in files) {
            buckets.getOrPut(categoryOf(name)) { mutableListOf() }.add(name)
        }
        return FileCategory.entries.mapNotNull { category ->
            val names = buckets[category] ?: return@mapNotNull null
            FileGroup(category, names.toList())
        }
    }
}
