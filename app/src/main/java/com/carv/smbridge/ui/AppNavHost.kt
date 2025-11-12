package com.carv.smbridge.ui

import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.carv.smbridge.ui.screens.FileListScreen
import com.carv.smbridge.ui.screens.SambaConnectScreen
import com.carv.smbridge.viewmodels.SambaSessionViewModel
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "session",
        enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }) + fadeIn() },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -1000 }) + fadeOut() },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }) + fadeIn() },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }) + fadeOut() }
    ) {
        navigation(
            startDestination = "connect",
            route = "session"
        ) {
            composable("connect") { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry("session")
                }
                val sessionViewModel: SambaSessionViewModel = viewModel(viewModelStoreOwner = parentEntry)

                SambaConnectScreen(
                    viewModel = sessionViewModel,
                    onConnected = { path ->
                        val encoded = URLEncoder.encode(path, "UTF-8")
                        navController.navigate("files/$encoded")
                    }
                )
            }

            composable("files/{encodedPath}") { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry("session")
                }
                val sessionViewModel: SambaSessionViewModel = viewModel(viewModelStoreOwner = parentEntry)

                val encoded = backStackEntry.arguments?.getString("encodedPath") ?: ""
                val decoded = URLDecoder.decode(encoded, "UTF-8")

                FileListScreen(
                    currentPath = decoded,
                    viewModel = sessionViewModel,
                    onNavigate = { newPath ->
                        val encodedPath = URLEncoder.encode(newPath, "UTF-8")
                        navController.navigate("files/$encodedPath")
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}