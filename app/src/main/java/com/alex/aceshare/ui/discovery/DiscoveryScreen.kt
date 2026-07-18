package com.alex.aceshare.ui.discovery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.alex.aceshare.R
import com.alex.aceshare.data.DiscoveredServer
import com.alex.aceshare.ui.components.ServerCard
import com.alex.aceshare.ui.theme.TvBackground
import com.alex.aceshare.ui.theme.TvTextPrimary
import com.alex.aceshare.ui.theme.TvTextSecondary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    viewModel: DiscoveryViewModel,
    onServerSelected: (DiscoveredServer) -> Unit,
    onExitRequest: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        viewModel.onStart()
        onDispose { viewModel.onStop() }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToServer.collect { server ->
            onServerSelected(server)
        }
    }

    BackHandler {
        onExitRequest()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(TvBackground),
    ) {
        if (state.servers.isEmpty()) {
            SearchingContent(
                rootModifier = Modifier.fillMaxSize(),
            )
        } else {
            ServerListContent(
                servers = state.servers,
                onSelect = { viewModel.selectServer(it) },
                rootModifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchingContent(
    rootModifier: Modifier = Modifier,
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.search_radar),
    )
    Column(
        rootModifier.padding(48.dp),
        Arrangement.Center,
        Alignment.CenterHorizontally,
    ) {
        LottieAnimation(
            composition = composition,
            iterations = LottieConstants.IterateForever,
            modifier = Modifier.size(280.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.discovery_searching),
            color = TvTextPrimary,
            fontSize = 32.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.discovery_hint),
            color = TvTextSecondary,
            fontSize = 20.sp,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ServerListContent(
    servers: List<DiscoveredServer>,
    onSelect: (DiscoveredServer) -> Unit,
    rootModifier: Modifier = Modifier,
) {
    Column(rootModifier.padding(horizontal = 48.dp, vertical = 36.dp)) {
        Text(
            text = stringResource(R.string.discovery_title),
            color = TvTextPrimary,
            fontSize = 36.sp,
        )
        Spacer(Modifier.height(24.dp))
        LazyColumn(
            contentPadding = PaddingValues(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(servers, key = { it.key }) { server ->
                ServerCard(
                    name = server.instanceName,
                    address = "${server.host}:${server.port}",
                    version = server.version,
                    onClick = { onSelect(server) },
                    rootModifier = Modifier.fillMaxWidth(0.7f),
                )
            }
        }
    }
}
