package com.alex.aceshare.data

sealed interface PreviewBytes {
    class Ready(val bytes: ByteArray) : PreviewBytes
    data object TooLarge : PreviewBytes
    data object Failed : PreviewBytes
}

sealed interface TextPreview {
    class Ready(val text: String) : TextPreview
    data object TooLarge : TextPreview
    data object Failed : TextPreview
}

sealed interface PreviewFileResult {
    data object Saved : PreviewFileResult
    data object TooLarge : PreviewFileResult
    data object Failed : PreviewFileResult
}

fun decodePreviewText(bytes: ByteArray): String {
    var start = 0
    if (
        bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() &&
        bytes[1] == 0xBB.toByte() &&
        bytes[2] == 0xBF.toByte()
    ) {
        start = 3
    }
    return bytes.decodeToString(start, bytes.size)
}
