package com.alex.aceshare.ui.browse

import android.graphics.Bitmap
import android.view.ViewConfiguration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.alex.aceshare.R
import com.alex.aceshare.data.FileCategory
import com.alex.aceshare.data.FileKinds
import com.alex.aceshare.data.FileMeta
import com.alex.aceshare.data.ListResponse
import com.alex.aceshare.data.PreviewKind
import com.alex.aceshare.data.formatByteSize
import com.alex.aceshare.data.formatDuration
import com.alex.aceshare.data.formatFileTime
import com.alex.aceshare.data.previewKey
import com.alex.aceshare.data.stamp
import com.alex.aceshare.ui.components.FocusableCard
import com.alex.aceshare.ui.components.SectionEmpty
import com.alex.aceshare.ui.theme.TvSurfaceFocused
import com.alex.aceshare.ui.theme.TvTextPrimary
import com.alex.aceshare.ui.theme.TvTextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MediaTileColor = Color(0xFF1A1D21)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ColumnScope.FilesPanel(
    data: ListResponse,
    previewRevision: Int,
    onOpen: (String) -> Unit,
    onPreview: (String) -> Unit,
    onDelete: (String) -> Unit,
    pendingFocusFile: String?,
    onPendingFocusConsumed: () -> Unit,
    durations: Map<String, Long>,
    loadImage: suspend (String, Int) -> Bitmap?,
    loadDuration: suspend (String) -> Unit,
) {
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    data.files.forEach { name ->
        focusRequesters.getOrPut(name) { FocusRequester() }
    }
    LaunchedEffect(pendingFocusFile, data.files) {
        val name = pendingFocusFile ?: return@LaunchedEffect
        focusRequesters[name]?.requestFocus()
        withFrameNanos { }
        focusRequesters[name]?.requestFocus()
        onPendingFocusConsumed()
    }
    Column(Modifier.weight(1f).fillMaxWidth()) {
        if (data.files.isEmpty()) {
            SectionEmpty(stringResource(R.string.browse_empty_files))
            return
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val columns = if (maxWidth >= 960.dp) 5 else 4
            val groups = remember(data.files) { FileKinds.groupFiles(data.files) }
            val meta = remember(data.fileMeta) { data.fileMeta.associateBy { it.name } }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                groups.forEach { group ->
                    item(key = "header-${group.category}") {
                        FileGroupHeader(category = group.category, count = group.files.size)
                    }
                    if (FileKinds.usesMediaGrid(group.category)) {
                        group.files.chunked(columns).forEach { row ->
                            item(key = "media-${group.category}-${row.joinToString("|")}") {
                                MediaRow(
                                    names = row,
                                    columns = columns,
                                    category = group.category,
                                    previewRevision = previewRevision,
                                    focusRequesters = focusRequesters,
                                    onOpen = onOpen,
                                    onPreview = onPreview,
                                    onDelete = onDelete,
                                    durations = durations,
                                    meta = meta,
                                    loadImage = loadImage,
                                    loadDuration = loadDuration,
                                )
                            }
                        }
                    } else {
                        group.files.forEach { name ->
                            item(key = "row-$name") {
                                FileRow(
                                    name = name,
                                    category = group.category,
                                    focusRequester = focusRequesters.getValue(name),
                                    onOpen = { onOpen(name) },
                                    onPreview = { onPreview(name) },
                                    onDelete = { onDelete(name) },
                                    meta = meta[name],
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FileGroupHeader(category: FileCategory, count: Int) {
    Row(
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(category.labelRes()),
            color = TvTextSecondary,
            fontSize = 20.sp,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .background(TvSurfaceFocused, CircleShape)
                .padding(horizontal = 10.dp, vertical = 2.dp),
        ) {
            Text(
                text = count.toString(),
                color = TvTextPrimary,
                fontSize = 16.sp,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MediaRow(
    names: List<String>,
    columns: Int,
    category: FileCategory,
    previewRevision: Int,
    focusRequesters: Map<String, FocusRequester>,
    onOpen: (String) -> Unit,
    onPreview: (String) -> Unit,
    onDelete: (String) -> Unit,
    durations: Map<String, Long>,
    meta: Map<String, FileMeta>,
    loadImage: suspend (String, Int) -> Bitmap?,
    loadDuration: suspend (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        names.forEach { name ->
            val preview = FileKinds.previewOf(name)
            val stamp = meta[name].stamp()
            FocusableCard(
                onClick = {
                    if (
                        preview == PreviewKind.Image ||
                        preview == PreviewKind.Video ||
                        preview == PreviewKind.Audio
                    ) {
                        onPreview(name)
                    } else {
                        onOpen(name)
                    }
                },
                onLongClick = { onDelete(name) },
                rootModifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequesters.getValue(name)),
            ) {
                MediaTileContent(
                    name = name,
                    category = category,
                    previewRevision = previewRevision,
                    stamp = stamp,
                    durationMs = durations[previewKey(name, stamp)] ?: -1L,
                    sizeBytes = meta[name]?.size ?: -1L,
                    loadImage = loadImage,
                    loadDuration = loadDuration,
                )
            }
        }
        repeat(columns - names.size) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MediaTileContent(
    name: String,
    category: FileCategory,
    previewRevision: Int,
    stamp: String,
    durationMs: Long,
    sizeBytes: Long,
    loadImage: suspend (String, Int) -> Bitmap?,
    loadDuration: suspend (String) -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(MediaTileColor),
    ) {
        val targetPx = with(LocalDensity.current) { maxWidth.roundToPx().coerceAtLeast(1) }
        val preview = FileKinds.previewOf(name)
        if (preview == PreviewKind.Image || category == FileCategory.Video) {
            ShareImage(
                name = name,
                targetPx = targetPx,
                revision = previewRevision,
                stamp = stamp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                load = loadImage,
                fallback = {
                    if (category != FileCategory.Video) {
                        CategoryGlyph(category, Modifier.size(36.dp), tint = Color(0xFFC8CDD3))
                    }
                },
            )
        }
        if (category == FileCategory.Audio) {
            LaunchedEffect(name, stamp, previewRevision) {
                loadDuration(name)
            }
            CategoryGlyph(
                category = category,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(42.dp),
                tint = Color.White.copy(alpha = 0.92f),
            )
        } else if (category == FileCategory.Video) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White,
                )
            }
        } else if (preview != PreviewKind.Image) {
            CategoryGlyph(
                category = category,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp),
                tint = Color.White.copy(alpha = 0.92f),
            )
        }
        MediaCaption(
            name = name,
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            nameSize = 14.sp,
            detailSize = 12.sp,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BoxScope.MediaCaption(
    name: String,
    durationMs: Long,
    sizeBytes: Long,
    nameSize: androidx.compose.ui.unit.TextUnit,
    detailSize: androidx.compose.ui.unit.TextUnit,
) {
    val detail = mediaDetail(durationMs, sizeBytes)
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                ),
            )
            .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            color = Color.White,
            fontSize = nameSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (detail.isNotEmpty()) {
            Text(
                text = detail,
                modifier = Modifier.padding(start = 6.dp),
                color = Color.White.copy(alpha = 0.86f),
                fontSize = detailSize,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FileRow(
    name: String,
    category: FileCategory,
    focusRequester: FocusRequester,
    onOpen: () -> Unit,
    onPreview: () -> Unit,
    onDelete: () -> Unit,
    meta: FileMeta?,
) {
    val canPreview = FileKinds.previewOf(name) != null
    val activate = rememberFileActivate(
        previewable = canPreview,
        onPreview = onPreview,
        onDownload = onOpen,
    )
    FocusableCard(
        onClick = { activate.onClick() },
        onLongClick = onDelete,
        rootModifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (!state.isFocused) activate.cancel()
            }
            .focusRequester(focusRequester),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryGlyph(
                category = category,
                modifier = Modifier.size(24.dp),
                tint = TvTextSecondary,
            )
            Text(
                text = name,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 12.dp),
                color = TvTextPrimary,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FileStat(meta)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FileStat(meta: FileMeta?) {
    if (meta == null) return
    val size = formatByteSize(meta.size)
    val time = formatFileTime(meta.mtime)
    if (size.isEmpty() && time.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (size.isNotEmpty()) {
            Text(text = size, color = TvTextSecondary, fontSize = 16.sp, maxLines = 1)
        }
        if (time.isNotEmpty()) {
            Text(
                text = time,
                modifier = Modifier.padding(start = if (size.isNotEmpty()) 10.dp else 0.dp),
                color = TvTextSecondary,
                fontSize = 16.sp,
                maxLines = 1,
            )
        }
    }
}

private fun mediaDetail(durationMs: Long, sizeBytes: Long): String {
    return listOf(formatDuration(durationMs), formatByteSize(sizeBytes))
        .filter { it.isNotEmpty() }
        .joinToString("  ")
}

private class FileActivate {
    var pending: Job? = null
    var onClick: () -> Unit = {}

    fun cancel() {
        pending?.cancel()
        pending = null
    }
}

@Composable
private fun rememberFileActivate(
    previewable: Boolean,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
): FileActivate {
    val scope = rememberCoroutineScope()
    val timeout = remember { ViewConfiguration.getDoubleTapTimeout().toLong() }
    val activate = remember { FileActivate() }
    DisposableEffect(activate) {
        onDispose { activate.cancel() }
    }
    activate.onClick = {
        if (!previewable) {
            activate.cancel()
            onDownload()
        } else if (activate.pending != null) {
            activate.cancel()
            onDownload()
        } else {
            activate.pending = scope.launch {
                delay(timeout)
                activate.pending = null
                onPreview()
            }
        }
    }
    return activate
}

@Composable
internal fun ShareImage(
    name: String,
    targetPx: Int,
    revision: Int,
    stamp: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    load: suspend (String, Int) -> Bitmap?,
    fallback: @Composable () -> Unit = {},
) {
    val state by produceState<ShareImageState>(ShareImageState.Loading, name, targetPx, revision, stamp) {
        value = ShareImageState.Loading
        val loaded = load(name, targetPx)?.takeUnless { it.isRecycled }
        value = if (loaded == null) ShareImageState.Failed else ShareImageState.Ready(loaded)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        when (val current = state) {
            ShareImageState.Loading -> Unit
            ShareImageState.Failed -> fallback()
            is ShareImageState.Ready -> {
                if (!current.bitmap.isRecycled) {
                    Image(
                        bitmap = current.bitmap.asImageBitmap(),
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = contentScale,
                    )
                } else {
                    fallback()
                }
            }
        }
    }
}

private sealed interface ShareImageState {
    data object Loading : ShareImageState
    data object Failed : ShareImageState
    data class Ready(val bitmap: Bitmap) : ShareImageState
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryGlyph(
    category: FileCategory,
    modifier: Modifier,
    tint: Color,
) {
    Icon(
        imageVector = category.icon(),
        contentDescription = null,
        modifier = modifier,
        tint = tint,
    )
}

private fun FileCategory.labelRes(): Int = when (this) {
    FileCategory.Image -> R.string.file_category_image
    FileCategory.Video -> R.string.file_category_video
    FileCategory.Audio -> R.string.file_category_audio
    FileCategory.Document -> R.string.file_category_document
    FileCategory.Text -> R.string.file_category_text
    FileCategory.Archive -> R.string.file_category_archive
    FileCategory.Other -> R.string.file_category_other
}

private fun FileCategory.icon(): ImageVector = when (this) {
    FileCategory.Image -> Icons.Filled.Image
    FileCategory.Video -> Icons.Filled.PlayArrow
    FileCategory.Audio -> Icons.Filled.MusicNote
    FileCategory.Document -> Icons.Filled.Description
    FileCategory.Text -> Icons.AutoMirrored.Filled.Subject
    FileCategory.Archive -> Icons.Filled.FolderZip
    FileCategory.Other -> Icons.AutoMirrored.Filled.InsertDriveFile
}
