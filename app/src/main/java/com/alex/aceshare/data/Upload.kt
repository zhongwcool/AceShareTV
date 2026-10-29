package com.alex.aceshare.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

const val MAX_UPLOAD_FILES = 100
const val MAX_UPLOAD_BYTES = 8L * 1024 * 1024 * 1024

data class UploadFile(
    val displayName: String,
    val size: Long,
    val openStream: () -> InputStream,
)

data class UploadProgress(
    val fileName: String,
    val index: Int,
    val count: Int,
    val bytesSent: Long,
    val bytesTotal: Long,
)

class UploadException(message: String) : IOException(message)

fun uploadFilesFromUris(resolver: ContentResolver, uris: List<Uri>): List<UploadFile> {
    if (uris.isEmpty()) return emptyList()
    val seen = HashSet<String>(uris.size)
    val files = ArrayList<UploadFile>(uris.size)
    for (uri in uris) {
        if (!seen.add(uri.toString())) continue
        val (name, size) = readUploadMeta(resolver, uri)
        files += UploadFile(
            displayName = name,
            size = size,
            openStream = {
                try {
                    resolver.openInputStream(uri)
                        ?: throw UploadException("无法读取「$name」")
                } catch (e: UploadException) {
                    throw e
                } catch (_: SecurityException) {
                    throw UploadException("无法读取「$name」")
                } catch (_: FileNotFoundException) {
                    throw UploadException("无法读取「$name」")
                }
            },
        )
    }
    return files
}

/**
 * OkHttp rejects non-ASCII header values. Go decodes RFC 2231 `filename*`
 * into the name used by `/api/upload`, including Chinese file names.
 */
internal fun fileContentDisposition(fileName: String): String {
    val cleaned = fileName
        .replace("\u0000", "")
        .replace("\r", "")
        .replace("\n", "")
        .trim()
        .ifBlank { "file" }
        .let { name -> if (name.length > 240) name.take(240) else name }
    val fallback = asciiFallbackFileName(cleaned)
    val encoded = encodeRfc2231(cleaned)
    return "form-data; name=\"file\"; filename=\"$fallback\"; filename*=UTF-8''$encoded"
}

private fun readUploadMeta(resolver: ContentResolver, uri: Uri): Pair<String, Long> {
    try {
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return fallbackUploadName(uri) to -1L
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val rawName = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                cursor.getString(nameIndex).orEmpty()
            } else {
                ""
            }
            val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                cursor.getLong(sizeIndex)
            } else {
                -1L
            }
            val name = sanitizeDisplayName(rawName).ifBlank { fallbackUploadName(uri) }
            return name to if (size < 0L) -1L else size
        }
    } catch (_: SecurityException) {
        throw UploadException("无法读取所选文件")
    } catch (_: IllegalArgumentException) {
        throw UploadException("无法读取所选文件")
    }
    return fallbackUploadName(uri) to -1L
}

private fun sanitizeDisplayName(raw: String): String {
    return raw
        .replace('\n', ' ')
        .replace('\r', ' ')
        .trim()
}

private fun fallbackUploadName(uri: Uri): String {
    val raw = uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.substringAfterLast(':')
        .orEmpty()
    val decoded = Uri.decode(raw)
    return sanitizeDisplayName(decoded)
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .ifBlank { "file" }
}

private fun asciiFallbackFileName(name: String): String {
    val extStart = name.lastIndexOf('.')
    val hasExt = extStart in 1 until name.lastIndex
    val stem = if (hasExt) name.substring(0, extStart) else name
    val ext = if (hasExt) name.substring(extStart + 1) else ""
    val safeStem = stem.map(::asciiFileChar).joinToString("").trim('_').ifBlank { "file" }
    val safeExt = ext.map(::asciiFileChar).joinToString("").trim('_')
    return if (safeExt.isEmpty()) safeStem else "$safeStem.$safeExt"
}

private fun asciiFileChar(ch: Char): Char {
    return if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '_') {
        ch
    } else {
        '_'
    }
}

private fun encodeRfc2231(value: String): String {
    val bytes = value.toByteArray(Charsets.UTF_8)
    val out = StringBuilder(bytes.size)
    for (byte in bytes) {
        val c = byte.toInt() and 0xFF
        val ch = c.toChar()
        if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '.' || ch == '-' || ch == '_') {
            out.append(ch)
        } else {
            out.append('%')
            out.append(HEX[c ushr 4])
            out.append(HEX[c and 0x0F])
        }
    }
    return out.toString()
}

private val HEX = "0123456789ABCDEF".toCharArray()
