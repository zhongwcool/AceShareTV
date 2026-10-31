package com.alex.aceshare.mobile.ui.discovery

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
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

private val SearchBackground = Color(0xFF0D1117)
private val SearchTextPrimary = Color(0xFFF0F6FC)
private val SearchTextSecondary = Color(0xFF8B949E)
private val SearchAccent = Color(0xFF58A6FF)

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

    val searching = state.servers.isEmpty()
    SearchingSystemBars(enabled = searching)

    Column(
        Modifier
            .fillMaxSize()
            .background(if (searching) SearchBackground else MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (searching) {
                SearchingContent(wide = wide)
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
        if (!searching) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
        AppVersionFooter(onOpenAbout = onOpenAbout, onDark = searching)
    }

    UpdateDialogs(
        updateState = updateState,
        onUpdate = { viewModel.startUpdate() },
        onDismiss = { viewModel.dismissUpdate() },
    )
}

@Composable
private fun SearchingContent(wide: Boolean) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.search_radar),
    )
    val titleSize = if (wide) 32.sp else 22.sp
    val hintSize = if (wide) 20.sp else 15.sp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val preferredArt = if (wide) 280.dp else 220.dp
        val artCap = maxHeight * 0.46f
        val artSize = if (preferredArt < artCap) preferredArt else artCap
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = if (wide) 720.dp else 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = if (wide) 48.dp else 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                modifier = Modifier.size(artSize),
            )
            Spacer(Modifier.height(if (wide) 24.dp else 20.dp))
            Text(
                text = stringResource(R.string.discovery_searching),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = titleSize,
                    lineHeight = if (wide) 40.sp else 30.sp,
                ),
                color = SearchTextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (wide) 12.dp else 10.dp))
            Text(
                text = stringResource(R.string.discovery_hint),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = hintSize,
                    lineHeight = if (wide) 28.sp else 22.sp,
                ),
                color = SearchTextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SearchingSystemBars(enabled: Boolean) {
    val view = LocalView.current
    val systemDark = isSystemInDarkTheme()
    DisposableEffect(enabled, systemDark) {
        val activity = view.context.findActivity() ?: return@DisposableEffect onDispose {}
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, view)
        val previousStatusColor = window.statusBarColor
        val previousNavColor = window.navigationBarColor
        val previousStatusContrast =
            if (Build.VERSION.SDK_INT >= 29) window.isStatusBarContrastEnforced else null
        val previousNavContrast =
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced else null
        if (enabled) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }
        val lightBars = !enabled && !systemDark
        controller.isAppearanceLightStatusBars = lightBars
        controller.isAppearanceLightNavigationBars = lightBars
        onDispose {
            window.statusBarColor = previousStatusColor
            window.navigationBarColor = previousNavColor
            if (Build.VERSION.SDK_INT >= 29) {
                previousStatusContrast?.let { window.isStatusBarContrastEnforced = it }
                previousNavContrast?.let { window.isNavigationBarContrastEnforced = it }
            }
            controller.isAppearanceLightStatusBars = !systemDark
            controller.isAppearanceLightNavigationBars = !systemDark
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
    onDark: Boolean,
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
            color = if (onDark) SearchTextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onOpenAbout,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.about_developer),
                style = MaterialTheme.typography.bodyMedium,
                color = if (onDark) SearchAccent else Color.Unspecified,
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
