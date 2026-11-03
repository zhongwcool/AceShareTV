package com.alex.aceshare.ui.browse

import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.alex.aceshare.R
import com.alex.aceshare.data.FileKinds
import com.alex.aceshare.data.PdfRenderLimit
import com.alex.aceshare.data.PreviewFileResult
import com.alex.aceshare.data.PreviewKind
import com.alex.aceshare.data.TextPreview
import com.alex.aceshare.data.renderPdfPages
import com.alex.aceshare.ui.components.FocusableButton
import com.alex.aceshare.ui.theme.TvBackground
import com.alex.aceshare.ui.theme.TvTextPrimary
import com.alex.aceshare.ui.theme.TvTextSecondary
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

private class PlaybackState {
    val playing = mutableStateOf(false)
    val ready = mutableStateOf(false)
    val failed = mutableStateOf(false)
    var toggle: () -> Unit = {}
}

private class PreviewAction(
    val label: String,
    val onClick: () -> Unit,
    val focus: FocusRequester,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FilePreviewOverlay(
    name: String,
    inlineUrl: String,
    previewRevision: Int,
    stamp: String,
    requestFocus: Boolean,
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
    val closeFocus = remember { FocusRequester() }
    val downloadFocus = remember { FocusRequester() }
    val deleteFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val prevFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }
    val playback = remember(inlineUrl) { PlaybackState() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var pdfPage by remember(name) { mutableIntStateOf(0) }
    var pdfCount by remember(name) { mutableIntStateOf(0) }
    var pdfTruncated by remember(name) { mutableStateOf<Int?>(null) }
    LaunchedEffect(requestFocus, name) {
        if (!requestFocus) return@LaunchedEffect
        if (!closeFocus.requestFocus()) {
            withFrameNanos { }
            closeFocus.requestFocus()
        }
    }
    val playLabel = stringResource(
        if (playback.playing.value) R.string.file_preview_pause else R.string.file_preview_play,
    )
    val previousLabel = stringResource(
        if (kind == PreviewKind.Pdf) R.string.file_preview_page_prev else R.string.file_preview_scroll_up,
    )
    val nextLabel = stringResource(
        if (kind == PreviewKind.Pdf) R.string.file_preview_page_next else R.string.file_preview_scroll_down,
    )
    val deleteLabel = stringResource(R.string.file_action_delete)
    val downloadLabel = stringResource(R.string.file_action_download)
    val closeLabel = stringResource(R.string.download_dismiss)
    val pageLabel = if (kind == PreviewKind.Pdf && pdfCount > 0) {
        val base = stringResource(R.string.file_preview_page, pdfPage + 1, pdfCount)
        val truncated = pdfTruncated
        if (truncated != null) {
            base + "  " + stringResource(R.string.file_preview_pdf_truncated, truncated)
        } else {
            base
        }
    } else {
        null
    }
    val actions = buildList {
        when (kind) {
            PreviewKind.Video, PreviewKind.Audio -> add(
                PreviewAction(
                    label = playLabel,
                    onClick = { playback.toggle() },
                    focus = playFocus,
                ),
            )
            PreviewKind.Pdf -> {
                add(
                    PreviewAction(
                        label = previousLabel,
                        onClick = { if (pdfPage > 0) pdfPage-- },
                        focus = prevFocus,
                    ),
                )
                add(
                    PreviewAction(
                        label = nextLabel,
                        onClick = { if (pdfCount > 0 && pdfPage < pdfCount - 1) pdfPage++ },
                        focus = nextFocus,
                    ),
                )
            }
            PreviewKind.Text -> {
                add(
                    PreviewAction(
                        label = previousLabel,
                        onClick = {
                            scope.launch {
                                val distance = scroll.viewportSize.coerceAtLeast(240)
                                scroll.scrollBy(-distance * 0.8f)
                            }
                        },
                        focus = prevFocus,
                    ),
                )
                add(
                    PreviewAction(
                        label = nextLabel,
                        onClick = {
                            scope.launch {
                                val distance = scroll.viewportSize.coerceAtLeast(240)
                                scroll.scrollBy(distance * 0.8f)
                            }
                        },
                        focus = nextFocus,
                    ),
                )
            }
            PreviewKind.Image -> Unit
        }
        add(PreviewAction(label = deleteLabel, onClick = onDelete, focus = deleteFocus))
        add(PreviewAction(label = downloadLabel, onClick = onDownload, focus = downloadFocus))
        add(PreviewAction(label = closeLabel, onClick = onDismiss, focus = closeFocus))
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(TvBackground),
    ) {
        Box(Modifier.fillMaxSize()) {
            when (kind) {
                PreviewKind.Image -> TvImagePreview(
                    name = name,
                    previewRevision = previewRevision,
                    stamp = stamp,
                    loadImage = loadImage,
                )
                PreviewKind.Video -> TvVideoPreview(inlineUrl, playback)
                PreviewKind.Audio -> TvAudioPreview(inlineUrl, playback)
                PreviewKind.Pdf -> TvPdfPreview(
                    name = name,
                    page = pdfPage,
                    downloadFile = downloadFile,
                    onCount = { count ->
                        pdfCount = count
                        if (pdfPage > count - 1) pdfPage = (count - 1).coerceAtLeast(0)
                    },
                    onTruncated = { shown -> pdfTruncated = shown },
                )
                PreviewKind.Text -> TvTextPreview(
                    name = name,
                    scroll = scroll,
                    loadText = loadText,
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent),
                    ),
                )
                .padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                color = TvTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 22.sp,
            )
            if (pageLabel != null) {
                Text(
                    text = pageLabel,
                    modifier = Modifier.padding(start = 16.dp),
                    color = TvTextSecondary,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                    ),
                )
                .padding(start = 32.dp, end = 32.dp, top = 36.dp, bottom = 16.dp),
        ) {
            PreviewActions(actions)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PreviewActions(actions: List<PreviewAction>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        actions.forEachIndexed { index, action ->
            if (index > 0) Spacer(Modifier.width(16.dp))
            val backward = actions.getOrNull(index - 1)?.focus ?: FocusRequester.Cancel
            val forward = actions.getOrNull(index + 1)?.focus ?: FocusRequester.Cancel
            FocusableButton(
                label = action.label,
                onClick = action.onClick,
                rootModifier = Modifier
                    .focusRequester(action.focus)
                    .previewActionFocus(backward, forward),
            )
        }
    }
}

private fun Modifier.previewActionFocus(
    backward: FocusRequester,
    forward: FocusRequester,
): Modifier = focusProperties {
    left = backward
    right = forward
    start = backward
    end = forward
    previous = backward
    next = forward
    up = FocusRequester.Cancel
    down = FocusRequester.Cancel
}

@Composable
private fun TvImagePreview(
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
            load = loadImage,
            fallback = {
                PreviewMessage(stringResource(R.string.file_preview_image_failed))
            },
        )
    }
}

@Composable
private fun TvVideoPreview(url: String, playback: PlaybackState) {
    if (playback.failed.value) {
        PreviewMessage(stringResource(R.string.file_preview_media_failed))
        return
    }
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .focusProperties { canFocus = false },
        factory = { context ->
            VideoView(context).apply {
                isFocusable = false
                setVideoURI(Uri.parse(url))
                setOnPreparedListener {
                    playback.ready.value = true
                    start()
                    playback.playing.value = true
                }
                setOnCompletionListener { playback.playing.value = false }
                setOnErrorListener { _, _, _ ->
                    playback.failed.value = true
                    true
                }
                playback.toggle = {
                    if (playback.ready.value && !playback.failed.value) {
                        if (isPlaying) {
                            pause()
                            playback.playing.value = false
                        } else {
                            start()
                            playback.playing.value = true
                        }
                    }
                }
            }
        },
        onRelease = { it.stopPlayback() },
    )
}

@Composable
private fun TvAudioPreview(url: String, playback: PlaybackState) {
    val context = LocalContext.current
    val player = remember(url) { MediaPlayer() }
    DisposableEffect(url) {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        player.setOnErrorListener { _, _, _ ->
            playback.failed.value = true
            true
        }
        player.setOnPreparedListener {
            playback.ready.value = true
            it.start()
            playback.playing.value = true
        }
        player.setOnCompletionListener { playback.playing.value = false }
        playback.toggle = {
            if (playback.ready.value && !playback.failed.value) {
                if (playback.playing.value) {
                    player.pause()
                    playback.playing.value = false
                } else {
                    player.start()
                    playback.playing.value = true
                }
            }
        }
        try {
            player.setDataSource(context, Uri.parse(url))
            player.prepareAsync()
        } catch (_: Exception) {
            playback.failed.value = true
        }
        onDispose {
            playback.toggle = {}
            player.setOnPreparedListener(null)
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            player.release()
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (playback.failed.value) {
            PreviewMessage(stringResource(R.string.file_preview_media_failed))
        } else {
            Text(
                text = stringResource(
                    if (playback.playing.value) R.string.file_preview_pause else R.string.file_preview_play,
                ),
                color = TvTextSecondary,
                fontSize = 24.sp,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvPdfPreview(
    name: String,
    page: Int,
    downloadFile: suspend (String, File) -> PreviewFileResult,
    onCount: (Int) -> Unit,
    onTruncated: (Int?) -> Unit,
) {
    val context = LocalContext.current
    val pages = remember(name) { mutableStateListOf<Bitmap>() }
    var phase by remember(name) { mutableStateOf<PdfPhase>(PdfPhase.Loading) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx().coerceIn(1, 1080) }
        LaunchedEffect(name, widthPx) {
            val stale = pages.toList()
            pages.clear()
            onCount(0)
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
                            if (scope.isActive) {
                                pages.add(bitmap)
                                onCount(pages.size)
                            } else {
                                bitmap.recycle()
                            }
                        }
                        onTruncated((limit as? PdfRenderLimit.Truncated)?.shown)
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
        val bitmap = pages.getOrNull(page)?.takeUnless { it.isRecycled }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                bitmap != null -> Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                phase == PdfPhase.Loading -> PreviewMessage(stringResource(R.string.browse_loading))
                phase == PdfPhase.TooLarge -> PreviewMessage(stringResource(R.string.file_preview_too_large))
                phase == PdfPhase.Failed || (phase != PdfPhase.Loading && pages.isEmpty()) -> {
                    PreviewMessage(stringResource(R.string.file_preview_pdf_failed))
                }
            }
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvTextPreview(
    name: String,
    scroll: androidx.compose.foundation.ScrollState,
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
        TextPhase.Loading -> PreviewMessage(stringResource(R.string.browse_loading))
        is TextPhase.Ready -> {
            Text(
                text = current.text,
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = false }
                    .verticalScroll(scroll)
                    .padding(start = 48.dp, end = 48.dp, top = 84.dp, bottom = 112.dp)
                    .focusable(false),
                color = TvTextPrimary,
                fontSize = 22.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        TextPhase.TooLarge -> PreviewMessage(stringResource(R.string.file_preview_text_too_large))
        TextPhase.Failed -> PreviewMessage(stringResource(R.string.file_preview_text_failed))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PreviewMessage(message: String) {
    Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = TvTextSecondary,
            fontSize = 22.sp,
        )
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