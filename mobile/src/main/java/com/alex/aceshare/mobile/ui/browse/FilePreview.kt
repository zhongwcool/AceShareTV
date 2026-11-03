package com.alex.aceshare.mobile.ui.browse

import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alex.aceshare.data.FileKinds
import com.alex.aceshare.data.PdfRenderLimit
import com.alex.aceshare.data.PreviewFileResult
import com.alex.aceshare.data.PreviewKind
import com.alex.aceshare.data.TextPreview
import com.alex.aceshare.data.renderPdfPages
import com.alex.aceshare.mobile.R
import kotlinx.coroutines.isActive
import java.io.File

private val MediaBackground = Color(0xFF111318)
private val MediaForeground = Color(0xFFE6EDF3)

@Composable
fun FilePreviewDialog(
    name: String,
    inlineUrl: String,
    previewRevision: Int,
    stamp: String,
    loadImage: suspend (String, Int) -> Bitmap?,
    loadText: suspend (String) -> TextPreview,
    downloadFile: suspend (String, File) -> PreviewFileResult,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val kind = FileKinds.previewOf(name)
    if (kind == null) {
        LaunchedEffect(name) { onDismiss() }
        return
    }
    val media = kind == PreviewKind.Image || kind == PreviewKind.Video || kind == PreviewKind.Audio
    val titleColor = if (media) MediaForeground else MaterialTheme.colorScheme.onSurface
    val iconTint = if (media) Color(0xFF9AA3AD) else MaterialTheme.colorScheme.onSurfaceVariant
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(14.dp),
            color = if (media) MediaBackground else MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        modifier = Modifier.weight(1f),
                        color = titleColor,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.file_action_delete_named, name),
                            tint = iconTint,
                        )
                    }
                    IconButton(onClick = onDownload) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = stringResource(R.string.file_action_download_named, name),
                            tint = iconTint,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.download_dismiss),
                            tint = iconTint,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when (kind) {
                        PreviewKind.Image -> ImagePreview(
                            name = name,
                            previewRevision = previewRevision,
                            stamp = stamp,
                            loadImage = loadImage,
                        )
                        PreviewKind.Video -> VideoPreview(inlineUrl)
                        PreviewKind.Audio -> AudioPreview(inlineUrl)
                        PreviewKind.Pdf -> PdfPreview(name = name, downloadFile = downloadFile)
                        PreviewKind.Text -> TextPreviewBody(name = name, loadText = loadText)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImagePreview(
    name: String,
    previewRevision: Int,
    stamp: String,
    loadImage: suspend (String, Int) -> Bitmap?,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val targetPx = with(LocalDensity.current) {
            maxOf(maxWidth, maxHeight).roundToPx().coerceIn(1, 2048)
        }
        ShareImage(
            name = name,
            targetPx = targetPx,
            revision = previewRevision,
            stamp = stamp,
            contentDescription = name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            showLoading = true,
            load = loadImage,
            fallback = {
                PreviewMessage(
                    stringResource(R.string.file_preview_image_failed),
                    color = MediaForeground,
                )
            },
        )
    }
}

@Composable
private fun VideoPreview(url: String) {
    var failed by remember(url) { mutableStateOf(false) }
    var ready by remember(url) { mutableStateOf(false) }
    var playing by remember(url) { mutableStateOf(false) }
    var view by remember(url) { mutableStateOf<VideoView?>(null) }
    if (failed) {
        PreviewMessage(stringResource(R.string.file_preview_media_failed), color = MediaForeground)
        return
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
            factory = { context ->
                VideoView(context).apply {
                    isFocusable = false
                    setVideoURI(Uri.parse(url))
                    setOnPreparedListener {
                        ready = true
                        start()
                        playing = true
                    }
                    setOnCompletionListener { playing = false }
                    setOnErrorListener { _, _, _ ->
                        failed = true
                        true
                    }
                    view = this
                }
            },
            onRelease = {
                it.stopPlayback()
                view = null
            },
        )
        if (!ready) {
            CircularProgressIndicator(Modifier.padding(16.dp))
        } else {
            IconButton(
                onClick = {
                    val video = view ?: return@IconButton
                    if (video.isPlaying) {
                        video.pause()
                        playing = false
                    } else {
                        video.start()
                        playing = true
                    }
                },
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (playing) R.string.file_preview_pause else R.string.file_preview_play,
                    ),
                    tint = MediaForeground,
                )
            }
        }
    }
}

@Composable
private fun AudioPreview(url: String) {
    val context = LocalContext.current
    var failed by remember(url) { mutableStateOf(false) }
    var ready by remember(url) { mutableStateOf(false) }
    var playing by remember(url) { mutableStateOf(false) }
    val player = remember(url) { MediaPlayer() }
    DisposableEffect(url) {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        player.setOnErrorListener { _, _, _ ->
            failed = true
            true
        }
        player.setOnPreparedListener {
            ready = true
            it.start()
            playing = true
        }
        player.setOnCompletionListener { playing = false }
        try {
            player.setDataSource(context, Uri.parse(url))
            player.prepareAsync()
        } catch (_: Exception) {
            failed = true
        }
        onDispose {
            player.setOnPreparedListener(null)
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            player.release()
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            failed -> PreviewMessage(
                stringResource(R.string.file_preview_media_failed),
                color = MediaForeground,
            )
            !ready -> CircularProgressIndicator()
            else -> IconButton(
                onClick = {
                    if (playing) {
                        player.pause()
                        playing = false
                    } else {
                        player.start()
                        playing = true
                    }
                },
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (playing) R.string.file_preview_pause else R.string.file_preview_play,
                    ),
                    modifier = Modifier.size(48.dp),
                    tint = MediaForeground,
                )
            }
        }
    }
}

@Composable
private fun PdfPreview(
    name: String,
    downloadFile: suspend (String, File) -> PreviewFileResult,
) {
    val context = LocalContext.current
    val pages = remember(name) { mutableStateListOf<Bitmap>() }
    var phase by remember(name) { mutableStateOf<PdfPhase>(PdfPhase.Loading) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx().coerceIn(1, 1080) }
        LaunchedEffect(name, widthPx) {
            val stale = pages.toList()
            pages.clear()
            if (stale.isNotEmpty()) {
                withFrameNanos { }
                stale.forEach { bitmap ->
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
            phase = PdfPhase.Loading
            val directory = File(context.cacheDir, "aceshare-preview").apply { mkdirs() }
            val dest = File(directory, "preview-${System.nanoTime()}.pdf")
            try {
                when (downloadFile(name, dest)) {
                    PreviewFileResult.Saved -> {
                        val scope = this
                        val limit = renderPdfPages(dest, widthPx) { bitmap ->
                            if (scope.isActive) pages.add(bitmap) else bitmap.recycle()
                        }
                        phase = when (limit) {
                            PdfRenderLimit.Complete -> if (pages.isEmpty()) PdfPhase.Failed else PdfPhase.Ready
                            is PdfRenderLimit.Truncated -> PdfPhase.Truncated(limit.shown)
                            PdfRenderLimit.Failed -> if (pages.isEmpty()) PdfPhase.Failed else PdfPhase.Ready
                        }
                    }
                    PreviewFileResult.TooLarge -> phase = PdfPhase.TooLarge
                    PreviewFileResult.Failed -> phase = PdfPhase.Failed
                }
            } finally {
                dest.delete()
            }
        }
        when (val current = phase) {
            PdfPhase.Loading -> {
                if (pages.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.browse_loading),
                                modifier = Modifier.padding(top = 12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    PdfPageList(pages, footer = null)
                }
            }
            PdfPhase.Ready -> PdfPageList(pages, footer = null)
            is PdfPhase.Truncated -> PdfPageList(
                pages,
                footer = stringResource(R.string.file_preview_pdf_truncated, current.shown),
            )
            PdfPhase.TooLarge -> PreviewMessage(stringResource(R.string.file_preview_too_large))
            PdfPhase.Failed -> PreviewMessage(stringResource(R.string.file_preview_pdf_failed))
        }
    }
    DisposableEffect(name) {
        onDispose {
            pages.forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            pages.clear()
        }
    }
}

@Composable
private fun PdfPageList(pages: List<Bitmap>, footer: String?) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(pages.size) { index ->
            val bitmap = pages[index]
            if (!bitmap.isRecycled) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.FillWidth,
                )
            }
        }
        if (footer != null) {
            item {
                Text(
                    text = footer,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun TextPreviewBody(
    name: String,
    loadText: suspend (String) -> TextPreview,
) {
    var phase by remember(name) { mutableStateOf<TextPhase>(TextPhase.Loading) }
    LaunchedEffect(name) {
        phase = when (val result = loadText(name)) {
            is TextPreview.Ready -> TextPhase.Ready(result.text)
            TextPreview.TooLarge -> TextPhase.TooLarge
            TextPreview.Failed -> TextPhase.Failed
        }
    }
    when (val current = phase) {
        TextPhase.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.browse_loading),
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        is TextPhase.Ready -> {
            val vertical = rememberScrollState()
            val horizontal = rememberScrollState()
            SelectionContainer(Modifier.fillMaxSize()) {
                Text(
                    text = current.text,
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(vertical)
                        .horizontalScroll(horizontal),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    softWrap = false,
                )
            }
        }
        TextPhase.TooLarge -> PreviewMessage(stringResource(R.string.file_preview_text_too_large))
        TextPhase.Failed -> PreviewMessage(stringResource(R.string.file_preview_text_failed))
    }
}

@Composable
private fun PreviewMessage(message: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text = message, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}

private sealed interface PdfPhase {
    data object Loading : PdfPhase
    data object Ready : PdfPhase
    data class Truncated(val shown: Int) : PdfPhase
    data object TooLarge : PdfPhase
    data object Failed : PdfPhase
}

private sealed interface TextPhase {
    data object Loading : TextPhase
    data class Ready(val text: String) : TextPhase
    data object TooLarge : TextPhase
    data object Failed : TextPhase
}
