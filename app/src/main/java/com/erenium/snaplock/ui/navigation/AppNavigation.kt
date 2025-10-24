package com.erenium.snaplock.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.erenium.snaplock.ui.screens.selectfile.SelectFileScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "select_file") {

        composable(route = "select_file"){
            SelectFileScreen(
                onFileSelected = { uri ->

                }
            )

        }

        composable(route = "unlock_screen/{uri}"){ navBackStackEntry ->
            val uriString = navBackStackEntry.arguments?.getString("uri")
            val uri = Uri.parse(uriString)


        }
    }
}