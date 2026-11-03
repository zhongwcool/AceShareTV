package com.alex.aceshare.mobile.ui.browse

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.alex.aceshare.mobile.R

private val MediaTileColor = Color(0xFF1A1D21)

@Composable
fun FilesPanel(
    data: ListResponse,
    previewRevision: Int,
    onDownload: (String) -> Unit,
    onPreview: (String) -> Unit,
    onDelete: (String) -> Unit,
    durations: Map<String, Long>,
    loadImage: suspend (String, Int) -> Bitmap?,
    loadDuration: suspend (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.upload_hint),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (data.files.isEmpty()) {
            EmptyText(stringResource(R.string.browse_empty_files))
            return
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = when {
                maxWidth >= 840.dp -> 6
                maxWidth >= 600.dp -> 4
                else -> 3
            }
            val groups = remember(data.files) { FileKinds.groupFiles(data.files) }
            val meta = remember(data.fileMeta) { data.fileMeta.associateBy { it.name } }
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
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
                                    onDownload = onDownload,
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
                        items(group.files, key = { "row-$it" }) { name ->
                            FileRow(
                                name = name,
                                category = group.category,
                                onDownload = { onDownload(name) },
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

@Composable
private fun FileGroupHeader(category: FileCategory, count: Int) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(category.labelRes()),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = count.toString(),
            modifier = Modifier
                .padding(start = 8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .padding(horizontal = 8.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MediaRow(
    names: List<String>,
    columns: Int,
    category: FileCategory,
    previewRevision: Int,
    onDownload: (String) -> Unit,
    onPreview: (String) -> Unit,
    onDelete: (String) -> Unit,
    durations: Map<String, Long>,
    meta: Map<String, FileMeta>,
    loadImage: suspend (String, Int) -> Bitmap?,
    loadDuration: suspend (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        names.forEach { name ->
            val stamp = meta[name].stamp()
            MediaTile(
                name = name,
                category = category,
                previewRevision = previewRevision,
                stamp = stamp,
                modifier = Modifier.weight(1f),
                onDownload = { onDownload(name) },
                onPreview = { onPreview(name) },
                onDelete = { onDelete(name) },
                durationMs = durations[previewKey(name, stamp)] ?: -1L,
                sizeBytes = meta[name]?.size ?: -1L,
                loadImage = loadImage,
                loadDuration = loadDuration,
            )
        }
        repeat(columns - names.size) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MediaTile(
    name: String,
    category: FileCategory,
    previewRevision: Int,
    stamp: String,
    modifier: Modifier,
    onDownload: () -> Unit,
    onPreview: () -> Unit,
    onDelete: () -> Unit,
    durationMs: Long,
    sizeBytes: Long,
    loadImage: suspend (String, Int) -> Bitmap?,
    loadDuration: suspend (String) -> Unit,
) {
    val preview = FileKinds.previewOf(name)
    val canPreview = preview == PreviewKind.Image ||
        preview == PreviewKind.Video ||
        preview == PreviewKind.Audio
    BoxWithConstraints(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MediaTileColor)
            .clickable { if (canPreview) onPreview() else onDownload() },
    ) {
        val targetPx = with(LocalDensity.current) { maxWidth.roundToPx().coerceAtLeast(1) }
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
                        CategoryGlyph(category, Modifier.size(28.dp), tint = Color(0xFFC8CDD3))
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
                    .size(36.dp),
                tint = Color.White.copy(alpha = 0.92f),
            )
        } else if (category == FileCategory.Video) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Color.White,
                )
            }
        } else if (preview != PreviewKind.Image) {
            CategoryGlyph(
                category = category,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp),
                tint = Color.White.copy(alpha = 0.92f),
            )
        }
        MediaCaption(name = name, durationMs = durationMs, sizeBytes = sizeBytes)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.file_action_delete_named, name),
                modifier = Modifier.size(16.dp),
                tint = Color.White,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDownload),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Download,
                contentDescription = stringResource(R.string.file_action_download_named, name),
                modifier = Modifier.size(16.dp),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun FileRow(
    name: String,
    category: FileCategory,
    onDownload: () -> Unit,
    onPreview: () -> Unit,
    onDelete: () -> Unit,
    meta: FileMeta?,
) {
    val canPreview = FileKinds.previewOf(name) != null
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (canPreview) onPreview() else onDownload() }
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryGlyph(
                category = category,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = name,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FileStat(meta)
            Row(modifier = Modifier.padding(start = 4.dp)) {
                CompactIconButton(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.file_action_delete_named, name),
                    onClick = onDelete,
                )
                CompactIconButton(
                    imageVector = Icons.Filled.Download,
                    contentDescription = stringResource(R.string.file_action_download_named, name),
                    onClick = onDownload,
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 50.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
        )
    }
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
    showLoading: Boolean = false,
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
            ShareImageState.Loading -> {
                if (showLoading) {
                    CircularProgressIndicator()
                }
            }
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

@Composable
private fun BoxScope.MediaCaption(name: String, durationMs: Long, sizeBytes: Long) {
    val detail = listOf(formatDuration(durationMs), formatByteSize(sizeBytes))
        .filter { it.isNotEmpty() }
        .joinToString("  ")
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                ),
            )
            .padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (detail.isNotEmpty()) {
            Text(
                text = detail,
                modifier = Modifier.padding(start = 4.dp),
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CompactIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FileStat(meta: FileMeta?) {
    if (meta == null) return
    val size = formatByteSize(meta.size)
    val time = formatFileTime(meta.mtime)
    if (size.isEmpty() && time.isEmpty()) return
    Row(
        modifier = Modifier.padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (size.isNotEmpty()) {
            Text(
                text = size,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (time.isNotEmpty()) {
            Text(
                text = time,
                modifier = Modifier.padding(start = if (size.isNotEmpty()) 8.dp else 0.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
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
