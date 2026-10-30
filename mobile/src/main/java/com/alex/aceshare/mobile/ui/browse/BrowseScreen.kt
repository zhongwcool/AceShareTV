package com.alex.aceshare.mobile.ui.browse

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.alex.aceshare.data.BrowseContentState
import com.alex.aceshare.data.DiscoveredServer
import com.alex.aceshare.data.ListResponse
import com.alex.aceshare.data.TextItem
import com.alex.aceshare.mobile.R
import com.alex.aceshare.ui.browse.BrowseViewModel
import com.alex.aceshare.ui.browse.UploadUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class BrowseTab { Lines, Texts, Files }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    onBackToDiscovery: () -> Unit,
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val servers by viewModel.discoveredServers.collectAsStateWithLifecycle()
    val upload by viewModel.upload.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSwitchSource by rememberSaveable { mutableStateOf(false) }
    var downloadFile by rememberSaveable { mutableStateOf<String?>(null) }
    var showNoPicker by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val onCopy: (TextItem) -> Unit = { item ->
        if (copyToClipboard(context, item)) {
            val message = context.getString(R.string.copied_format, copyPreview(item))
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
        }
    }
    val current = viewModel.current
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
    val wide = adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
    )
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
    val detailBack = selectedTab == BrowseTab.Texts.ordinal && navigator.canNavigateBack()
    val overlay = downloadFile != null ||
        showSwitchSource ||
        showNoPicker ||
        upload !is UploadUiState.Idle

    LaunchedEffect(refreshing, content) {
        if (refreshing && content !is BrowseContentState.Loading && content !is BrowseContentState.Idle) {
            delay(400)
            refreshing = false
        }
    }

    BackHandler(enabled = overlay || detailBack) {
        when {
            upload is UploadUiState.Uploading -> viewModel.cancelUpload()
            upload !is UploadUiState.Idle -> viewModel.dismissUploadResult()
            showNoPicker -> showNoPicker = false
            downloadFile != null -> downloadFile = null
            showSwitchSource -> showSwitchSource = false
            detailBack -> scope.launch { navigator.navigateBack() }
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            item(
                selected = selectedTab == BrowseTab.Lines.ordinal,
                onClick = { selectedTab = BrowseTab.Lines.ordinal },
                icon = {
                    Icon(painterResource(R.drawable.ic_tab_lines), contentDescription = null)
                },
                label = { Text(stringResource(R.string.browse_tab_lines)) },
            )
            item(
                selected = selectedTab == BrowseTab.Texts.ordinal,
                onClick = { selectedTab = BrowseTab.Texts.ordinal },
                icon = {
                    Icon(painterResource(R.drawable.ic_tab_texts), contentDescription = null)
                },
                label = { Text(stringResource(R.string.browse_tab_texts)) },
            )
            item(
                selected = selectedTab == BrowseTab.Files.ordinal,
                onClick = { selectedTab = BrowseTab.Files.ordinal },
                icon = {
                    Icon(painterResource(R.drawable.ic_tab_files), contentDescription = null)
                },
                label = { Text(stringResource(R.string.browse_tab_files)) },
            )
        },
        layoutType = layoutType,
    ) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(Modifier.padding(12.dp)) {
                        Text(
                            text = data.visuals.message,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            contentWindowInsets = if (layoutType == NavigationSuiteType.NavigationBar) {
                WindowInsets(0, 0, 0, 0)
            } else {
                WindowInsets.navigationBars
            },
            topBar = {
                TopAppBar(
                    windowInsets = TopAppBarDefaults.windowInsets,
                    title = {
                        Column(
                            Modifier.clickable { showSwitchSource = true },
                        ) {
                            Text(
                                text = stringResource(R.string.browse_source),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = current.instanceName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${current.host}:${current.port}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackToDiscovery) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.navigate_back),
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                if (selectedTab == BrowseTab.Files.ordinal && content is BrowseContentState.Ready) {
                    FloatingActionButton(onClick = pickFiles) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.upload_action),
                        )
                    }
                }
            },
        ) { innerPadding ->
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    viewModel.retry()
                },
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                when (val state = content) {
                    BrowseContentState.Idle, BrowseContentState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.browse_loading),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is BrowseContentState.Error -> {
                        Column(Modifier.padding(24.dp)) {
                            Text(
                                text = stringResource(R.string.browse_error),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = state.message,
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = { viewModel.retry() },
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text(stringResource(R.string.browse_retry))
                            }
                        }
                    }
                    is BrowseContentState.Ready -> {
                        when (BrowseTab.entries[selectedTab]) {
                            BrowseTab.Lines -> LinesPanel(
                                data = state.data,
                                wide = wide,
                                onCopy = onCopy,
                            )
                            BrowseTab.Texts -> TextsPanel(
                                data = state.data,
                                navigator = navigator,
                                onOpen = { item ->
                                    scope.launch {
                                        navigator.navigateTo(
                                            ListDetailPaneScaffoldRole.Detail,
                                            textItemKey(item),
                                        )
                                    }
                                },
                                onCopy = onCopy,
                            )
                            BrowseTab.Files -> FilesPanel(
                                data = state.data,
                                wide = wide,
                                onOpen = { downloadFile = it },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSwitchSource) {
        ModalBottomSheet(onDismissRequest = { showSwitchSource = false }) {
            Text(
                text = stringResource(R.string.browse_switch_source),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                items(servers.ifEmpty { listOf(current) }, key = { it.key }) { server ->
                    val label = if (server.key == current.key) {
                        "${server.instanceName} (当前)"
                    } else {
                        server.instanceName
                    }
                    ListItem(
                        headlineContent = { Text(label) },
                        supportingContent = { Text("${server.host}:${server.port}") },
                        modifier = Modifier.clickable {
                            showSwitchSource = false
                            viewModel.connect(server)
                        },
                    )
                }
            }
        }
    }

    downloadFile?.let { name ->
        val url = "${current.baseUrl}/files/${Uri.encode(name)}"
        AlertDialog(
            onDismissRequest = { downloadFile = null },
            title = { Text(stringResource(R.string.download_title)) },
            text = {
                Column {
                    Text(name)
                    Text(
                        text = stringResource(R.string.download_message, url),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        enqueueDownload(context, url, name)
                        downloadFile = null
                    },
                ) {
                    Text(stringResource(R.string.download_start))
                }
            },
            dismissButton = {
                TextButton(onClick = { downloadFile = null }) {
                    Text(stringResource(R.string.download_dismiss))
                }
            },
        )
    }

    when (val uploadState = upload) {
        UploadUiState.Idle -> Unit
        is UploadUiState.Uploading -> UploadProgressDialog(
            state = uploadState,
            onCancel = { viewModel.cancelUpload() },
        )
        is UploadUiState.Success -> UploadSuccessDialog(
            state = uploadState,
            onDismiss = { viewModel.dismissUploadResult() },
        )
        is UploadUiState.Failure -> AlertDialog(
            onDismissRequest = { viewModel.dismissUploadResult() },
            title = { Text(stringResource(R.string.upload_failed_title)) },
            text = { Text(uploadState.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissUploadResult() }) {
                    Text(stringResource(R.string.download_dismiss))
                }
            },
        )
    }

    if (showNoPicker) {
        AlertDialog(
            onDismissRequest = { showNoPicker = false },
            title = { Text(stringResource(R.string.upload_action)) },
            text = { Text(stringResource(R.string.upload_no_picker)) },
            confirmButton = {
                TextButton(onClick = { showNoPicker = false }) {
                    Text(stringResource(R.string.download_dismiss))
                }
            },
        )
    }
}

@Composable
private fun LinesPanel(
    data: ListResponse,
    wide: Boolean,
    onCopy: (TextItem) -> Unit,
) {
    if (data.lines.isEmpty()) {
        EmptyText(stringResource(R.string.browse_empty_lines))
        return
    }
    if (wide) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            gridItems(data.lines, key = { "${it.title}|${it.text}" }) { item ->
                TextCard(item = item, onClick = { onCopy(item) })
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(data.lines, key = { "${it.title}|${it.text}" }) { item ->
                TextCard(item = item, onClick = { onCopy(item) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun TextsPanel(
    data: ListResponse,
    navigator: androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator<String>,
    onOpen: (TextItem) -> Unit,
    onCopy: (TextItem) -> Unit,
) {
    val selected = data.texts.firstOrNull { textItemKey(it) == navigator.currentDestination?.contentKey }
    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                if (data.texts.isEmpty()) {
                    EmptyText(stringResource(R.string.browse_empty_texts))
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(data.texts, key = { textItemKey(it) }) { item ->
                            TextCard(item = item, onClick = { onOpen(item) })
                        }
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                if (selected == null) {
                    EmptyText(stringResource(R.string.browse_pick_text))
                } else {
                    TextDetail(item = selected, onCopy = { onCopy(selected) })
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun FilesPanel(
    data: ListResponse,
    wide: Boolean,
    onOpen: (String) -> Unit,
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
        if (wide) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 240.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                gridItems(data.files, key = { it }) { name ->
                    FileCard(name = name, onClick = { onOpen(name) })
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(data.files, key = { it }) { name ->
                    FileCard(name = name, onClick = { onOpen(name) })
                }
            }
        }
    }
}

@Composable
private fun TextCard(
    item: TextItem,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (item.title.isNotBlank()) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = item.text.replace('\n', ' '),
                modifier = Modifier.padding(top = if (item.title.isNotBlank()) 4.dp else 0.dp),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FileCard(
    name: String,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = name,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun TextDetail(
    item: TextItem,
    onCopy: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = item.title.ifBlank { stringResource(R.string.text_detail_title) },
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = item.text,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 12.dp)
                .verticalScroll(scroll),
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = onCopy, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.copy_full_text))
        }
    }
}

@Composable
private fun EmptyText(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun UploadProgressDialog(
    state: UploadUiState.Uploading,
    onCancel: () -> Unit,
) {
    val fraction = if (state.bytesTotal > 0L) {
        (state.bytesSent.coerceAtLeast(0L).toFloat() / state.bytesTotal.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.upload_in_progress)) },
        text = {
            Column {
                Text(stringResource(R.string.upload_file_count, state.index, state.count))
                Text(
                    text = state.fileName,
                    modifier = Modifier.padding(top = 8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (fraction != null) {
                    val percent = ((state.bytesSent.coerceAtLeast(0L) * 100) / state.bytesTotal)
                        .toInt()
                        .coerceIn(0, 100)
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.upload_bytes,
                            formatByteSize(state.bytesSent),
                            formatByteSize(state.bytesTotal),
                            percent,
                        ),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.upload_bytes_sent,
                            formatByteSize(state.bytesSent.coerceAtLeast(0L)),
                        ),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.upload_cancel))
            }
        },
    )
}

@Composable
private fun UploadSuccessDialog(
    state: UploadUiState.Success,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.upload_done_title)) },
        text = {
            Column {
                Text(stringResource(R.string.upload_done_message))
                state.files.take(8).forEach { file ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = file.name,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatByteSize(file.size),
                            modifier = Modifier.padding(start = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val hiddenCount = state.files.size - 8
                if (hiddenCount > 0) {
                    Text(
                        text = stringResource(R.string.upload_done_more, hiddenCount),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.download_dismiss))
            }
        },
    )
}

private fun textItemKey(item: TextItem): String = "${item.title}|${item.text.take(32)}"

private fun copyToClipboard(context: Context, item: TextItem): Boolean {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText(item.title, item.text))
    return true
}

private fun copyPreview(item: TextItem): String {
    val singleLine = item.text.ifBlank { item.title }.replace('\n', ' ').trim()
    return if (singleLine.length <= 40) singleLine else "${singleLine.take(40)}…"
}

private fun enqueueDownload(context: Context, url: String, fileName: String) {
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(fileName)
        .setDescription("AceShare")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
    val dm = context.getSystemService(DownloadManager::class.java) ?: return
    dm.enqueue(request)
    Toast.makeText(context, context.getString(R.string.download_started), Toast.LENGTH_SHORT).show()
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
