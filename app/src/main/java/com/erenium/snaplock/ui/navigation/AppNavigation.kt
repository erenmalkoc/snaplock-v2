package com.erenium.snaplock.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.erenium.snaplock.ui.screens.selectfile.SelectFileScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.erenium.snaplock.presentation.entrydetail.EntryDetailScreen
import com.erenium.snaplock.presentation.main.MainViewModel
import com.erenium.snaplock.ui.screens.entrylist.EntryListScreen
import com.erenium.snaplock.ui.screens.unlock.UnlockScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.core.net.toUri

@Composable
fun AppNavigation(
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val isLocked by mainViewModel.isLocked.collectAsState()

    NavHost(navController = navController, startDestination = NavRoutes.SELECT_FILE) {

        composable(route = NavRoutes.SELECT_FILE) {
            SelectFileScreen(
                onFileSelected = { uri ->
                    val encodedUri = URLEncoder.encode(
                        uri.toString(),
                        StandardCharsets.UTF_8.name()
                    )
                    navController.navigate("${NavRoutes.UNLOCK_SCREEN}/$encodedUri")
                }
            )

        }
        composable(
            route = "${NavRoutes.UNLOCK_SCREEN}/{uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType })
        ) { navBackStackEntry ->
            val encodedUri = navBackStackEntry.arguments?.getString("uri")
            if (encodedUri != null) {
                val uriString = URLDecoder.decode(
                    encodedUri,
                    StandardCharsets.UTF_8.name()
                )
                val uri = uriString.toUri()
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

            if (isLocked) {
                LaunchedEffect(Unit) {
                    navController.navigate(NavRoutes.SELECT_FILE) {
                        popUpTo(NavRoutes.ENTRY_LIST) { inclusive = true }
                    }
                }
            } else {
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
        }
        composable(
            route = "${NavRoutes.ENTRY_DETAIL}/{uuid}",
            arguments = listOf(navArgument("uuid") { type = NavType.StringType })
        ) {
            if (isLocked) {
                LaunchedEffect(Unit) {
                    navController.navigate(NavRoutes.SELECT_FILE) {
                        popUpTo(NavRoutes.ENTRY_DETAIL) { inclusive = true }
                    }
                }
            } else {
                EntryDetailScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

        }
    }
}
