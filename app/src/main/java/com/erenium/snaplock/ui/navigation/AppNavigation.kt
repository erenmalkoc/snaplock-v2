package com.erenium.snaplock.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.erenium.snaplock.ui.screens.selectfile.SelectFileScreen
import androidx.core.net.toUri
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.erenium.snaplock.presentation.entrydetail.EntryDetailScreen
import com.erenium.snaplock.ui.screens.entrylist.EntryListScreen
import com.erenium.snaplock.ui.screens.unlock.UnlockScreen

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
            val encodedUri = navBackStackEntry.arguments?.getString("uri")
            if (encodedUri != null) {
                val uri = encodedUri.toUri()
                UnlockScreen(
                    uri = uri,
                    onUnlockSuccess = {
                        navController.navigate(NavRoutes.ENTRY_LIST) {
                            popUpTo(NavRoutes.SELECT_FILE) { inclusive = true }
                        }
                    }
                )
            } else {
                navController.popBackStack()
            }
        }
        composable(route = NavRoutes.ENTRY_LIST) {
            EntryListScreen(
                onNavigateToLock = {
                    navController.navigate(NavRoutes.SELECT_FILE) {
                        popUpTo(NavRoutes.ENTRY_LIST) { inclusive = true }
                    }
                },
                onEntryClick = { uuid ->
                    navController.navigate("${NavRoutes.ENTRY_DETAIL}/$uuid")
                }
            )
        }
        composable(
            route = "${NavRoutes.ENTRY_DETAIL}/{uuid}",
            arguments = listOf(navArgument("uuid") { type = NavType.StringType })
        ) {
            EntryDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
