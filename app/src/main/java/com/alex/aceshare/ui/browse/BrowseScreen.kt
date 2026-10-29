package com.alex.aceshare.ui.browse

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.alex.aceshare.R
import com.alex.aceshare.data.BrowseContentState
import com.alex.aceshare.data.DiscoveredServer
import com.alex.aceshare.data.ListResponse
import com.alex.aceshare.data.TextItem
import com.alex.aceshare.ui.theme.TvPrimary
import com.alex.aceshare.ui.theme.TvSurfaceFocused
import com.alex.aceshare.ui.components.FocusableButton
import com.alex.aceshare.ui.components.FocusableCard
import com.alex.aceshare.ui.components.LineItemCard
import com.alex.aceshare.ui.components.SectionEmpty
import com.alex.aceshare.ui.components.ServerCard
import com.alex.aceshare.ui.components.TabChip
import com.alex.aceshare.ui.theme.TvBackground
import com.alex.aceshare.ui.theme.TvError
import com.alex.aceshare.ui.theme.TvTextPrimary
import com.alex.aceshare.ui.theme.TvTextSecondary

private enum class BrowseTab { Lines, Texts, Files }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    onBackToDiscovery: () -> Unit,
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val servers by viewModel.discoveredServers.collectAsStateWithLifecycle()
    val upload by viewModel.upload.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSwitchSource by remember { mutableStateOf(false) }
    var textDetail by remember { mutableStateOf<TextItem?>(null) }
    var downloadFile by remember { mutableStateOf<String?>(null) }
    var restoreFileFocus by remember { mutableStateOf<String?>(null) }
    var showNoPicker by remember { mutableStateOf(false) }
    var restoreUploadFocus by remember { mutableStateOf(false) }
    var uploadOverlayVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val current = viewModel.current
    val openDocuments = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.upload(uris)
    }
    val getContents = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.upload(uris)
    }
    val dismissDownloadDialog = {
        restoreFileFocus = downloadFile
        downloadFile = null
    }
    val pickFiles = {
        if (upload is UploadUiState.Idle && !showNoPicker) {
            try {
                openDocuments.launch(arrayOf("*/*"))
            } catch (_: ActivityNotFoundException) {
                try {
                    getContents.launch("*/*")
                } catch (_: ActivityNotFoundException) {
                    showNoPicker = true
                } catch (_: IllegalStateException) {
                    // The document picker is already open.
                }
            } catch (_: IllegalStateException) {
                // The document picker is already open.
            }
        }
    }
    val blockBackgroundFocus = downloadFile != null ||
        showNoPicker ||
        upload !is UploadUiState.Idle

    LaunchedEffect(upload) {
        val visible = upload !is UploadUiState.Idle
        if (uploadOverlayVisible && !visible) {
            restoreUploadFocus = true
        }
        uploadOverlayVisible = visible
    }

    BackHandler {
        when {
            upload is UploadUiState.Uploading -> viewModel.cancelUpload()
            upload !is UploadUiState.Idle -> viewModel.dismissUploadResult()
            showNoPicker -> {
                restoreUploadFocus = true
                showNoPicker = false
            }
            downloadFile != null -> dismissDownloadDialog()
            textDetail != null -> textDetail = null
            showSwitchSource -> showSwitchSource = false
            else -> onBackToDiscovery()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(TvBackground),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 28.dp)
                .focusProperties {
                    if (blockBackgroundFocus) canFocus = false
                },
        ) {
            SourceHeader(
                server = current,
                onClick = { showSwitchSource = true },
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TabChip(
                    label = stringResource(R.string.browse_tab_lines),
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                )
                TabChip(
                    label = stringResource(R.string.browse_tab_texts),
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                )
                TabChip(
                    label = stringResource(R.string.browse_tab_files),
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                )
            }
            Spacer(Modifier.height(20.dp))

            when (val state = content) {
                BrowseContentState.Idle, BrowseContentState.Loading -> {
                    Text(
                        text = stringResource(R.string.browse_loading),
                        color = TvTextSecondary,
                        fontSize = 24.sp,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                is BrowseContentState.Error -> {
                    Column(Modifier.padding(24.dp)) {
                        Text(
                            text = stringResource(R.string.browse_error),
                            color = TvError,
                            fontSize = 26.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            color = TvTextSecondary,
                            fontSize = 20.sp,
                        )
                        Spacer(Modifier.height(20.dp))
                        FocusableButton(
                            label = stringResource(R.string.browse_retry),
                            onClick = { viewModel.retry() },
                        )
                    }
                }
                is BrowseContentState.Ready -> {
                    when (BrowseTab.entries[selectedTab]) {
                        BrowseTab.Lines -> LinesPanel(
                            data = state.data,
                            onCopy = { item -> copyToClipboard(context, item) },
                        )
                        BrowseTab.Texts -> TextsPanel(
                            data = state.data,
                            onOpen = { textDetail = it },
                        )
                        BrowseTab.Files -> FilesPanel(
                            data = state.data,
                            onOpen = { downloadFile = it },
                            onUpload = pickFiles,
                            requestUploadFocus = restoreUploadFocus,
                            onUploadFocusConsumed = { restoreUploadFocus = false },
                            pendingFocusFile = restoreFileFocus,
                            onPendingFocusConsumed = { restoreFileFocus = null },
                        )
                    }
                }
            }
        }

        if (showSwitchSource) {
            SwitchSourcePanel(
                servers = servers.ifEmpty { listOf(current) },
                current = current,
                onSelect = { server ->
                    showSwitchSource = false
                    viewModel.connect(server)
                },
                onDismiss = { showSwitchSource = false },
            )
        }

        textDetail?.let { item ->
            TextDetailOverlay(
                item = item,
                onCopy = {
                    copyToClipboard(context, item)
                },
                onDismiss = { textDetail = null },
            )
        }

        downloadFile?.let { name ->
            DownloadDialog(
                fileName = name,
                downloadUrl = "${current.baseUrl}/files/${Uri.encode(name)}",
                onDownload = { url, fileName ->
                    enqueueDownload(context, url, fileName)
                    dismissDownloadDialog()
                },
                onDismiss = dismissDownloadDialog,
            )
        }

        when (val uploadState = upload) {
            UploadUiState.Idle -> Unit
            is UploadUiState.Uploading -> UploadProgressDialog(
                state = uploadState,
                onCancel = { viewModel.cancelUpload() },
            )
            is UploadUiState.Success -> UploadNoticeDialog(
                title = stringResource(R.string.upload_done_title),
                onDismiss = { viewModel.dismissUploadResult() },
            ) {
                Text(
                    text = stringResource(R.string.upload_done_message),
                    color = TvTextSecondary,
                    fontSize = 20.sp,
                )
                Spacer(Modifier.height(12.dp))
                uploadState.files.take(8).forEach { file ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = file.name,
                            modifier = Modifier.weight(1f),
                            color = TvTextPrimary,
                            fontSize = 22.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = formatByteSize(file.size),
                            color = TvTextSecondary,
                            fontSize = 20.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                val hiddenCount = uploadState.files.size - 8
                if (hiddenCount > 0) {
                    Text(
                        text = stringResource(R.string.upload_done_more, hiddenCount),
                        color = TvTextSecondary,
                        fontSize = 20.sp,
                    )
                }
            }
            is UploadUiState.Failure -> UploadNoticeDialog(
                title = stringResource(R.string.upload_failed_title),
                onDismiss = { viewModel.dismissUploadResult() },
            ) {
                Text(
                    text = uploadState.message,
                    color = TvError,
                    fontSize = 22.sp,
                )
            }
        }

        if (showNoPicker) {
            UploadNoticeDialog(
                title = stringResource(R.string.upload_action),
                onDismiss = {
                    restoreUploadFocus = true
                    showNoPicker = false
                },
            ) {
                Text(
                    text = stringResource(R.string.upload_no_picker),
                    color = TvTextPrimary,
                    fontSize = 22.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceHeader(
    server: DiscoveredServer,
    onClick: () -> Unit,
) {
    FocusableCard(onClick = onClick, rootModifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
            Text(
                text = stringResource(R.string.browse_source),
                color = TvTextSecondary,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = server.instanceName,
                color = TvTextPrimary,
                fontSize = 28.sp,
            )
            Text(
                text = "${server.host}:${server.port}",
                color = TvTextSecondary,
                fontSize = 20.sp,
            )
        }
    }
}

@Composable
private fun LinesPanel(
    data: ListResponse,
    onCopy: (TextItem) -> Unit,
) {
    if (data.lines.isEmpty()) {
        SectionEmpty(stringResource(R.string.browse_empty_lines))
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        items(data.lines, key = { "${it.title}|${it.text}" }) { item ->
            LineItemCard(
                title = item.title,
                preview = item.text.replace('\n', ' '),
                onClick = { onCopy(item) },
            )
        }
    }
}

@Composable
private fun TextsPanel(
    data: ListResponse,
    onOpen: (TextItem) -> Unit,
) {
    if (data.texts.isEmpty()) {
        SectionEmpty(stringResource(R.string.browse_empty_texts))
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        items(data.texts, key = { "${it.title}|${it.text.take(32)}" }) { item ->
            LineItemCard(
                title = item.title,
                preview = item.text.replace('\n', ' '),
                onClick = { onOpen(item) },
            )
        }
    }
}

@Composable
private fun ColumnScope.FilesPanel(
    data: ListResponse,
    onOpen: (String) -> Unit,
    onUpload: () -> Unit,
    requestUploadFocus: Boolean,
    onUploadFocusConsumed: () -> Unit,
    pendingFocusFile: String?,
    onPendingFocusConsumed: () -> Unit,
) {
    val uploadFocus = remember { FocusRequester() }
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    data.files.forEach { name ->
        focusRequesters.getOrPut(name) { FocusRequester() }
    }
    LaunchedEffect(requestUploadFocus) {
        if (!requestUploadFocus) return@LaunchedEffect
        uploadFocus.requestFocus()
        withFrameNanos { }
        uploadFocus.requestFocus()
        onUploadFocusConsumed()
    }
    LaunchedEffect(pendingFocusFile) {
        val name = pendingFocusFile ?: return@LaunchedEffect
        focusRequesters[name]?.requestFocus()
        // Dialog removal clears focus in the same frame; restore again after that.
        withFrameNanos { }
        focusRequesters[name]?.requestFocus()
        onPendingFocusConsumed()
    }
    Column(Modifier.weight(1f).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FocusableButton(
                label = stringResource(R.string.upload_action),
                onClick = onUpload,
                rootModifier = Modifier.focusRequester(uploadFocus),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.upload_hint),
                color = TvTextSecondary,
                fontSize = 18.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (data.files.isEmpty()) {
            SectionEmpty(stringResource(R.string.browse_empty_files))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                items(data.files, key = { it }) { name ->
                    FocusableCard(
                        onClick = { onOpen(name) },
                        rootModifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequesters.getValue(name)),
                    ) {
                        Text(
                            text = name,
                            color = TvTextPrimary,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SwitchSourcePanel(
    servers: List<DiscoveredServer>,
    current: DiscoveredServer,
    onSelect: (DiscoveredServer) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(TvBackground.copy(alpha = 0.92f)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(48.dp),
        ) {
            Text(
                text = stringResource(R.string.browse_switch_source),
                color = TvTextPrimary,
            )
            Spacer(Modifier.height(20.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(servers, key = { it.key }) { server ->
                    val label = if (server.key == current.key) {
                        "${server.instanceName} (当前)"
                    } else {
                        server.instanceName
                    }
                    ServerCard(
                        name = label,
                        address = "${server.host}:${server.port}",
                        version = server.version,
                        onClick = { onSelect(server) },
                        rootModifier = Modifier.fillMaxWidth(0.7f),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            FocusableButton(
                label = stringResource(R.string.download_dismiss),
                onClick = onDismiss,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TextDetailOverlay(
    item: TextItem,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scroll = rememberScrollState()
    Box(
        Modifier
            .fillMaxSize()
            .background(TvBackground.copy(alpha = 0.95f)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(48.dp),
        ) {
            Text(
                text = item.title.ifBlank { stringResource(R.string.text_detail_title) },
                color = TvTextPrimary,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = item.text,
                color = TvTextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll),
            )
            Spacer(Modifier.height(20.dp))
            Row {
                FocusableButton(
                    label = stringResource(R.string.copy_full_text),
                    onClick = onCopy,
                )
                Spacer(Modifier.width(16.dp))
                FocusableButton(
                    label = stringResource(R.string.download_dismiss),
                    onClick = onDismiss,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DownloadDialog(
    fileName: String,
    downloadUrl: String,
    onDownload: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val startFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(fileName) {
        if (!startFocus.requestFocus()) {
            withFrameNanos { }
            startFocus.requestFocus()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(TvBackground.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.7f)
                .padding(32.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(R.string.download_title),
                color = TvTextPrimary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = fileName,
                color = TvTextPrimary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.download_message, downloadUrl),
                color = TvTextSecondary,
            )
            Spacer(Modifier.height(28.dp))
            Row {
                FocusableButton(
                    label = stringResource(R.string.download_start),
                    onClick = { onDownload(downloadUrl, fileName) },
                    rootModifier = Modifier
                        .focusRequester(startFocus)
                        .limitDialogButtonFocus(
                            backward = FocusRequester.Cancel,
                            forward = closeFocus,
                        ),
                )
                Spacer(Modifier.width(16.dp))
                FocusableButton(
                    label = stringResource(R.string.download_dismiss),
                    onClick = onDismiss,
                    rootModifier = Modifier
                        .focusRequester(closeFocus)
                        .limitDialogButtonFocus(
                            backward = startFocus,
                            forward = FocusRequester.Cancel,
                        ),
                )
            }
        }
    }
}

/** D-pad stays on the two dialog actions; the file list behind the scrim is not a target. */
private fun Modifier.limitDialogButtonFocus(
    backward: FocusRequester,
    forward: FocusRequester,
): Modifier = focusProperties {
    left = backward
    right = forward
    start = backward
    end = forward
    up = FocusRequester.Cancel
    down = FocusRequester.Cancel
    previous = backward
    next = forward
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun UploadProgressDialog(
    state: UploadUiState.Uploading,
    onCancel: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!cancelFocus.requestFocus()) {
            withFrameNanos { }
            cancelFocus.requestFocus()
        }
    }
    val fraction = if (state.bytesTotal > 0L) {
        (state.bytesSent.coerceAtLeast(0L).toFloat() / state.bytesTotal.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(TvBackground.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.7f)
                .padding(32.dp),
        ) {
            Text(
                text = stringResource(R.string.upload_in_progress),
                color = TvTextPrimary,
                fontSize = 28.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.upload_file_count, state.index, state.count),
                color = TvTextSecondary,
                fontSize = 20.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.fileName,
                color = TvTextPrimary,
                fontSize = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            if (fraction != null) {
                UploadProgressBar(fraction)
                Spacer(Modifier.height(8.dp))
                val percent = ((state.bytesSent.coerceAtLeast(0L) * 100) / state.bytesTotal)
                    .toInt()
                    .coerceIn(0, 100)
                Text(
                    text = stringResource(
                        R.string.upload_bytes,
                        formatByteSize(state.bytesSent),
                        formatByteSize(state.bytesTotal),
                        percent,
                    ),
                    color = TvTextSecondary,
                    fontSize = 20.sp,
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.upload_bytes_sent,
                        formatByteSize(state.bytesSent.coerceAtLeast(0L)),
                    ),
                    color = TvTextSecondary,
                    fontSize = 20.sp,
                )
            }
            Spacer(Modifier.height(28.dp))
            FocusableButton(
                label = stringResource(R.string.upload_cancel),
                onClick = onCancel,
                rootModifier = Modifier
                    .focusRequester(cancelFocus)
                    .limitDialogButtonFocus(
                        backward = FocusRequester.Cancel,
                        forward = FocusRequester.Cancel,
                    ),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun UploadNoticeDialog(
    title: String,
    onDismiss: () -> Unit,
    body: @Composable () -> Unit,
) {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(title) {
        if (!closeFocus.requestFocus()) {
            withFrameNanos { }
            closeFocus.requestFocus()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(TvBackground.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.7f)
                .padding(32.dp),
        ) {
            Text(
                text = title,
                color = TvTextPrimary,
                fontSize = 28.sp,
            )
            Spacer(Modifier.height(16.dp))
            body()
            Spacer(Modifier.height(28.dp))
            FocusableButton(
                label = stringResource(R.string.download_dismiss),
                onClick = onDismiss,
                rootModifier = Modifier
                    .focusRequester(closeFocus)
                    .limitDialogButtonFocus(
                        backward = FocusRequester.Cancel,
                        forward = FocusRequester.Cancel,
                    ),
            )
        }
    }
}

@Composable
private fun UploadProgressBar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(TvSurfaceFocused, RoundedCornerShape(5.dp)),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(10.dp)
                    .background(TvPrimary, RoundedCornerShape(5.dp)),
            )
        }
    }
}

private fun formatByteSize(bytes: Long): String {
    if (bytes < 0L) return "—"
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val shown = if (value >= 10.0 || value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", value)
    }
    return "$shown ${units[unitIndex]}"
}

private fun copyToClipboard(context: Context, item: TextItem) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(item.title, item.text))
    val preview = item.text.replace('\n', ' ').trim().take(40)
    Toast.makeText(
        context,
        context.getString(R.string.copied_format, preview),
        Toast.LENGTH_SHORT,
    ).show()
}

private fun enqueueDownload(context: Context, url: String, fileName: String) {
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(fileName)
        .setDescription("AceShare")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    dm.enqueue(request)
    Toast.makeText(context, context.getString(R.string.download_started), Toast.LENGTH_SHORT).show()
}
