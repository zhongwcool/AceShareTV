package com.alex.aceshare.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.caverock.androidsvg.SVG
import java.io.ByteArrayInputStream

fun decodeShareImage(bytes: ByteArray, fileName: String, targetPx: Int): Bitmap? {
    if (bytes.isEmpty() || targetPx <= 0) return null
    return try {
        if (FileKinds.extensionOf(fileName) == "svg") {
            decodeSvg(bytes, targetPx)
        } else {
            decodeRaster(bytes, fileName, targetPx)
        }
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }
}

private fun decodeSvg(bytes: ByteArray, targetPx: Int): Bitmap? {
    val svg = SVG.getFromInputStream(ByteArrayInputStream(bytes))
    val box = svg.documentViewBox
    val aspect = when {
        box != null && box.width() > 0f && box.height() > 0f -> box.width() / box.height()
        svg.documentWidth > 0f && svg.documentHeight > 0f -> svg.documentWidth / svg.documentHeight
        else -> 1f
    }
    val longSide = targetPx.coerceIn(1, 1600)
    var width = longSide
    var height = (width / aspect).toInt().coerceAtLeast(1)
    if (height > longSide) {
        height = longSide
        width = (height * aspect).toInt().coerceIn(1, longSide)
    }
    svg.setDocumentWidth(width.toFloat())
    svg.setDocumentHeight(height.toFloat())
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    svg.renderToCanvas(Canvas(bitmap))
    return bitmap
}

private fun decodeRaster(bytes: ByteArray, fileName: String, targetPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetPx.coerceAtLeast(1))
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    return applyExifRotation(decoded, bytes, fileName)
}

private fun sampleSize(width: Int, height: Int, target: Int): Int {
    var size = 1
    while (width / (size * 2) >= target && height / (size * 2) >= target) {
        size *= 2
    }
    return size
}

private fun applyExifRotation(bitmap: Bitmap, bytes: ByteArray, fileName: String): Bitmap {
    val ext = FileKinds.extensionOf(fileName)
    if (ext != "jpg" && ext != "jpeg") return bitmap
    val orientation = try {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    } catch (_: Exception) {
        return bitmap
    }
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> return bitmap
    }
    val matrix = Matrix().apply { postRotate(degrees) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated != bitmap) bitmap.recycle()
    return rotated
}
