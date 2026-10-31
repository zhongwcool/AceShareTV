package com.alex.aceshare.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alex.aceshare.AceShareApp
import com.alex.aceshare.data.DiscoveredServer
import com.alex.aceshare.ui.browse.BrowseScreen
import com.alex.aceshare.ui.browse.BrowseViewModel
import com.alex.aceshare.ui.discovery.DiscoveryScreen
import com.alex.aceshare.ui.discovery.DiscoveryViewModel
import com.alex.aceshare.ui.discovery.UpdateDownloadBar
import com.alex.aceshare.ui.discovery.UpdateUiState

object Routes {
    const val Discovery = "discovery"
    const val Browse = "browse/{host}/{port}/{name}"

    fun browse(server: DiscoveredServer): String {
        val name = Uri.encode(server.instanceName)
        return "browse/${Uri.encode(server.host)}/${server.port}/$name"
    }
}

@Composable
fun AceShareNavHost(
    onExit: () -> Unit,
) {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as AceShareApp
    val container = app.container
    val discoveryViewModel: DiscoveryViewModel = viewModel(
        factory = DiscoveryViewModel.Factory(
            container.discovery,
            container.preferences,
            container.appUpdater,
        ),
    )
    val updateState by discoveryViewModel.updateUiState.collectAsStateWithLifecycle()
    val downloading = updateState as? UpdateUiState.Downloading

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.Discovery,
            modifier = Modifier.fillMaxSize(),
        ) {
        composable(Routes.Discovery) {
            DiscoveryScreen(
                viewModel = discoveryViewModel,
                onServerSelected = { server ->
                    navController.navigate(Routes.browse(server)) {
                        launchSingleTop = true
                    }
                },
                onExitRequest = onExit,
            )
        }
        composable(
            route = Routes.Browse,
            arguments = listOf(
                navArgument("host") { type = NavType.StringType },
                navArgument("port") { type = NavType.IntType },
                navArgument("name") { type = NavType.StringType },
            ),
        ) { entry ->
            val host = Uri.decode(entry.arguments?.getString("host").orEmpty())
            val port = entry.arguments?.getInt("port") ?: 0
            val name = Uri.decode(entry.arguments?.getString("name").orEmpty())
            val server = DiscoveredServer(
                instanceName = name.ifBlank { "AceShare" },
                host = host,
                port = port,
            )
            val vm: BrowseViewModel = viewModel(
                factory = BrowseViewModel.Factory(
                    app = app,
                    repository = container.repository,
                    discovery = container.discovery,
                    preferences = container.preferences,
                    initialServer = server,
                ),
            )
            BrowseScreen(
                viewModel = vm,
                onBackToDiscovery = {
                    navController.popBackStack(Routes.Discovery, inclusive = false)
                },
            )
        }
        }
        downloading?.let {
            UpdateDownloadBar(
                progress = it.progress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom)),
            )
        }
    }
}
