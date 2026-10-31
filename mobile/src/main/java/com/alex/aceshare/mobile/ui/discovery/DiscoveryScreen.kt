package com.alex.aceshare.mobile.ui.discovery

import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.alex.aceshare.data.DiscoveredServer
import com.alex.aceshare.discovery.LocalNetworkAccess
import com.alex.aceshare.mobile.R
import com.alex.aceshare.mobile.ui.about.readAppVersionName
import com.alex.aceshare.ui.discovery.DiscoveryViewModel
import com.alex.aceshare.ui.discovery.UpdateError
import com.alex.aceshare.ui.discovery.UpdateUiState
import kotlinx.coroutines.delay

private const val HintRevealDelayMillis = 10_000L

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

    val context = LocalContext.current
    val localNetworkPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.onLocalNetworkPermissionGranted()
    }

    DisposableEffect(Unit) {
        viewModel.onStart()
        onDispose { viewModel.onStop() }
    }

    LaunchedEffect(Unit) {
        val permission = LocalNetworkAccess.permissionToRequest()
        if (permission != null &&
            context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        ) {
            localNetworkPermission.launch(permission)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToServer.collect { server ->
            onServerSelected(server)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
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
        }
        if (state.servers.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
        AppVersionFooter(onOpenAbout = onOpenAbout)
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
    val hints = listOf(
        R.string.discovery_hint_pc,
        R.string.discovery_hint_wifi,
    )
    var showHints by rememberSaveable { mutableStateOf(false) }
    var hintsStartedAt by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(showHints) {
        if (showHints) return@LaunchedEffect
        if (hintsStartedAt == 0L) hintsStartedAt = SystemClock.elapsedRealtime()
        val remaining = HintRevealDelayMillis - (SystemClock.elapsedRealtime() - hintsStartedAt)
        if (remaining > 0) delay(remaining)
        showHints = true
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(168.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                )
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier.size(200.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.discovery_searching),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            AnimatedVisibility(
                visible = showHints,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(20.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.discovery_hints_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                hints.forEach { hint ->
                                    HintRow(text = stringResource(hint))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HintRow(text: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
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
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
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
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
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
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.discovery_footer_version, version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onOpenAbout,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.about_developer),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun UpdateDialogs(
    updateState: UpdateUiState,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (val update = updateState) {
        is UpdateUiState.Available -> {
            UpdateAvailableDialog(
                currentVersionName = update.currentVersionName,
                versionName = update.info.versionName,
                releaseNotes = update.info.releaseNotes,
                onUpdate = onUpdate,
                onDismiss = onDismiss,
            )
        }
        is UpdateUiState.Downloading -> Unit
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
