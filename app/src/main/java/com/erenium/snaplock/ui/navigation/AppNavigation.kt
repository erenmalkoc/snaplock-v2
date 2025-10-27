package com.erenium.snaplock.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.erenium.snaplock.presentation.entrylist.EntryListScreen
import com.erenium.snaplock.ui.screens.selectfile.SelectFileScreen
import androidx.core.net.toUri

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = NavRoutes.SELECT_FILE) {

        composable(route = NavRoutes.SELECT_FILE) {
            SelectFileScreen(
                onFileSelected = { uri ->
                }
            )

        }
        composable(route = "${NavRoutes.UNLOCK_SCREEN}/{uri}") { navBackStackEntry ->
            val uriString = navBackStackEntry.arguments?.getString("uri")
            val uri = uriString?.toUri()
        }
        composable(route = NavRoutes.ENTRY_LIST) {
            EntryListScreen(
                onNavigateToLock = {
                    navController.navigate(NavRoutes.SELECT_FILE) {
                        popUpTo(NavRoutes.ENTRY_LIST) { inclusive = true }
                    }
                }
            )
        }
    }
}