package com.alex.aceshare.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface PdfRenderLimit {
    data object Complete : PdfRenderLimit
    data class Truncated(val shown: Int) : PdfRenderLimit
    data object Failed : PdfRenderLimit
}

suspend fun renderPdfPages(
    file: File,
    targetWidthPx: Int,
    maxPages: Int = FileKinds.PDF_MAX_PAGES,
    onPage: (Bitmap) -> Unit,
): PdfRenderLimit {
    if (maxPages <= 0) return PdfRenderLimit.Failed
    return try {
        withContext(Dispatchers.IO) {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val count = renderer.pageCount
                    if (count <= 0) return@withContext PdfRenderLimit.Failed
                    val limit = minOf(count, maxPages)
                    val width = targetWidthPx.coerceIn(1, 1080)
                    for (index in 0 until limit) {
                        renderer.openPage(index).use { page ->
                            val pageWidth = page.width.coerceAtLeast(1)
                            val pageHeight = page.height.coerceAtLeast(1)
                            val scale = width.toFloat() / pageWidth.toFloat()
                            val height = (pageHeight * scale).toInt().coerceIn(1, 4096)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            val matrix = Matrix().apply { setScale(scale, scale) }
                            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            withContext(Dispatchers.Main) { onPage(bitmap) }
                        }
                    }
                    if (count > limit) PdfRenderLimit.Truncated(limit) else PdfRenderLimit.Complete
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        PdfRenderLimit.Failed
    } catch (_: OutOfMemoryError) {
        PdfRenderLimit.Failed
    }
}
