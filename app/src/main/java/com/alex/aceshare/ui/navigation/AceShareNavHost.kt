package com.alex.aceshare.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
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

    NavHost(
        navController = navController,
        startDestination = Routes.Discovery,
    ) {
        composable(Routes.Discovery) {
            val vm: DiscoveryViewModel = viewModel(
                factory = DiscoveryViewModel.Factory(
                    app.discovery,
                    app.preferences,
                    app.updateChecker,
                ),
            )
            DiscoveryScreen(
                viewModel = vm,
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
                    repository = app.repository,
                    discovery = app.discovery,
                    preferences = app.preferences,
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
}
