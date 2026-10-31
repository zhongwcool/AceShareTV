package com.alex.aceshare.mobile.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.alex.aceshare.data.DiscoveredServer
import com.alex.aceshare.mobile.R
import com.alex.aceshare.mobile.ui.about.readAppVersionName
import com.alex.aceshare.ui.discovery.DiscoveryViewModel
import com.alex.aceshare.ui.discovery.UpdateError
import com.alex.aceshare.ui.discovery.UpdateUiState

@Composable
fun DiscoveryScreen(
    viewModel: DiscoveryViewModel,
    onServerSelected: (DiscoveredServer) -> Unit,
    onOpenAbout: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateUiState.collectAsStateWithLifecycle()
    val wide = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
    )

    DisposableEffect(Unit) {
        viewModel.onStart()
        onDispose { viewModel.onStop() }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToServer.collect { server ->
            onServerSelected(server)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (state.servers.isEmpty()) {
            SearchingContent()
        } else if (wide) {
            ServerGrid(
                servers = state.servers,
                onSelect = { viewModel.selectServer(it) },
            )
        } else {
            ServerList(
                servers = state.servers,
                onSelect = { viewModel.selectServer(it) },
            )
        }
        AppVersionFooter(
            onOpenAbout = onOpenAbout,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    UpdateDialogs(
        updateState = updateState,
        onUpdate = { viewModel.startUpdate() },
        onDismiss = { viewModel.dismissUpdate() },
    )
}

@Composable
private fun SearchingContent() {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.search_radar),
    )
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LottieAnimation(
            composition = composition,
            iterations = LottieConstants.IterateForever,
            modifier = Modifier.size(200.dp),
        )
        Text(
            text = stringResource(R.string.discovery_searching),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.discovery_hint),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ServerList(
    servers: List<DiscoveredServer>,
    onSelect: (DiscoveredServer) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.discovery_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        LazyColumn(
            contentPadding = PaddingValues(top = 12.dp, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(servers, key = { it.key }) { server ->
                ServerCard(server = server, onClick = { onSelect(server) })
            }
        }
    }
}

@Composable
private fun ServerGrid(
    servers: List<DiscoveredServer>,
    onSelect: (DiscoveredServer) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.discovery_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            gridItems(servers, key = { it.key }) { server ->
                ServerCard(server = server, onClick = { onSelect(server) })
            }
        }
    }
}

@Composable
private fun ServerCard(
    server: DiscoveredServer,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = server.instanceName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${server.host}:${server.port}",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val version = server.version
            if (!version.isNullOrBlank()) {
                Text(
                    text = "v${version.trim().removePrefix("v").removePrefix("V")}",
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AppVersionFooter(
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val version = remember(context) { readAppVersionName(context) }
    val prefix = stringResource(R.string.discovery_footer_version, version)
    val about = stringResource(R.string.about_developer)
    val linkColor = MaterialTheme.colorScheme.primary
    val label = buildAnnotatedString {
        append(prefix)
        withLink(
            LinkAnnotation.Clickable(
                tag = "about_developer",
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
                linkInteractionListener = { onOpenAbout() },
            ),
        ) {
            append(about)
        }
    }
    Text(
        text = label,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun UpdateDialogs(
    updateState: UpdateUiState,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (val update = updateState) {
        is UpdateUiState.Available -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.update_title)) },
                text = {
                    Column {
                        Text(
                            stringResource(
                                R.string.update_message,
                                update.currentVersionName,
                                update.info.versionName,
                            ),
                        )
                        Text(
                            text = update.info.releaseNotes.ifBlank {
                                stringResource(R.string.update_notes_empty)
                            },
                            modifier = Modifier.padding(top = 12.dp),
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = onUpdate) {
                        Text(stringResource(R.string.update_now))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.update_later))
                    }
                },
            )
        }
        is UpdateUiState.Downloading -> {
            Dialog(onDismissRequest = {}) {
                Card {
                    Column(Modifier.padding(24.dp)) {
                        Text(
                            text = stringResource(R.string.update_downloading, update.progress),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        LinearProgressIndicator(
                            progress = { update.progress.coerceIn(0, 100) / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                        )
                    }
                }
            }
        }
        is UpdateUiState.Error -> {
            UpdateErrorDialog(error = update, onDismiss = onDismiss, onUpdate = onUpdate)
        }
        UpdateUiState.Idle -> Unit
    }
}

@Composable
private fun UpdateErrorDialog(
    error: UpdateUiState.Error,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
) {
    val message = when (error.messageRes) {
        UpdateError.DownloadFailed -> stringResource(R.string.update_download_failed)
        UpdateError.InstallFailed -> stringResource(R.string.update_install_failed)
        UpdateError.PermissionRequired -> stringResource(R.string.update_install_permission)
    }
    val retry = error.messageRes == UpdateError.PermissionRequired && error.info != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { if (retry) onUpdate() else onDismiss() }) {
                Text(
                    stringResource(
                        if (retry) R.string.update_now else R.string.download_dismiss,
                    ),
                )
            }
        },
        dismissButton = if (retry) {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_later))
                }
            }
        } else {
            null
        },
    )
}
