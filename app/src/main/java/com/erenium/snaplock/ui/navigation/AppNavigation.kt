package com.erenium.snaplock.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.erenium.snaplock.presentation.main.AppShortcut
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.erenium.snaplock.ui.screens.changepassword.ChangeMasterPasswordScreen
import com.erenium.snaplock.ui.screens.createdatabase.CreateDatabaseScreen
import com.erenium.snaplock.ui.screens.databasesettings.DatabaseSettingsScreen
import com.erenium.snaplock.ui.screens.selectfile.SelectFileScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.erenium.snaplock.ui.screens.entrydetail.EntryDetailScreen
import com.erenium.snaplock.presentation.main.MainViewModel
import com.erenium.snaplock.ui.screens.entryform.EntryFormScreen
import com.erenium.snaplock.ui.screens.entrylist.EntryListScreen
import com.erenium.snaplock.ui.screens.generator.PasswordGeneratorScreen
import com.erenium.snaplock.ui.screens.health.PasswordHealthScreen
import com.erenium.snaplock.ui.screens.recyclebin.RecycleBinScreen
import com.erenium.snaplock.ui.screens.settings.SettingsScreen
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
    val shortcut by mainViewModel.pendingShortcut.collectAsState()
    var focusSearch by remember { mutableStateOf(false) }
    // A vault that is still open (the activity was recreated before auto-lock) opens on its entries.
    val startDestination = remember { if (mainViewModel.isLocked.value) NavRoutes.SELECT_FILE else NavRoutes.ENTRY_LIST }

    val slideDuration = 300

    LaunchedEffect(shortcut) {
        when (shortcut) {
            AppShortcut.GENERATOR -> {
                mainViewModel.onShortcutHandled()
                navController.navigate(NavRoutes.PASSWORD_GENERATOR) { launchSingleTop = true }
            }
            // While unlocked, return to the entry list, which finishes the shortcut; while
            // locked it waits there until the user has unlocked.
            AppShortcut.SEARCH, AppShortcut.NEW_ENTRY -> navController.popBackStack(NavRoutes.ENTRY_LIST, inclusive = false)
            else -> Unit
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(slideDuration)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(slideDuration)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(slideDuration)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(slideDuration)
            )
        }
    ) {

        composable(route = NavRoutes.SELECT_FILE) {
            SelectFileScreen(
                onFileSelected = { uri ->
                    val encodedUri = URLEncoder.encode(
                        uri.toString(),
                        StandardCharsets.UTF_8.name()
                    )
                    navController.navigate("${NavRoutes.UNLOCK_SCREEN}/$encodedUri")
                },
                onCreateDatabase = { navController.navigate(NavRoutes.CREATE_DATABASE) }
            )

        }
        composable(route = NavRoutes.CREATE_DATABASE) {
            CreateDatabaseScreen(
                onNavigateBack = { navController.popBackStack() },
                onDatabaseCreated = {
                    navController.navigate(NavRoutes.ENTRY_LIST) {
                        popUpTo(NavRoutes.SELECT_FILE) { inclusive = true }
                    }
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
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            } else {
                navController.popBackStack()
            }
        }
        composable(route = NavRoutes.ENTRY_LIST) {
            // Shortcuts that need an open vault are carried out here, once the entries show.
            LaunchedEffect(shortcut, isLocked) {
                if (isLocked) return@LaunchedEffect
                when (shortcut) {
                    AppShortcut.NEW_ENTRY -> {
                        mainViewModel.onShortcutHandled()
                        navController.navigate(NavRoutes.ENTRY_FORM)
                    }
                    AppShortcut.SEARCH -> {
                        mainViewModel.onShortcutHandled()
                        focusSearch = true
                    }
                    else -> Unit
                }
            }
            if (isLocked) {
                LaunchedEffect(Unit) {
                    navController.navigate(NavRoutes.SELECT_FILE) {
                        popUpTo(NavRoutes.ENTRY_LIST) { inclusive = true }
                    }
                }
            } else {
                EntryListScreen(
                    focusSearch = focusSearch,
                    onSearchFocused = { focusSearch = false },
                    onNavigateToLock = {
                        navController.navigate(NavRoutes.SELECT_FILE) {
                            popUpTo(NavRoutes.ENTRY_LIST) { inclusive = true }
                        }
                    },
                    onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) },
                    onNavigateToRecycleBin = { navController.navigate(NavRoutes.RECYCLE_BIN) },
                    onNavigateToHealth = { navController.navigate(NavRoutes.PASSWORD_HEALTH) },
                    onNavigateToGenerator = { navController.navigate(NavRoutes.PASSWORD_GENERATOR) },
                    onAddEntry = { groupUuid ->
                        val route = if (groupUuid != null) "${NavRoutes.ENTRY_FORM}?group=$groupUuid" else NavRoutes.ENTRY_FORM
                        navController.navigate(route)
                    },
                    onEntryClick = { uuid ->
                        navController.navigate("${NavRoutes.ENTRY_DETAIL}/$uuid")
                    }
                )
            }
        }
        composable(route = NavRoutes.PASSWORD_HEALTH) {
            if (isLocked) {
                LaunchedEffect(Unit) {
                    navController.navigate(NavRoutes.SELECT_FILE) {
                        popUpTo(NavRoutes.PASSWORD_HEALTH) { inclusive = true }
                    }
                }
            } else {
                PasswordHealthScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onEntryClick = { uuid -> navController.navigate("${NavRoutes.ENTRY_DETAIL}/$uuid") }
                )
            }
        }
        composable(route = NavRoutes.RECYCLE_BIN) {
            if (isLocked) {
                LaunchedEffect(Unit) {
                    navController.navigate(NavRoutes.SELECT_FILE) {
                        popUpTo(NavRoutes.RECYCLE_BIN) { inclusive = true }
                    }
                }
            } else {
                RecycleBinScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
        composable(route = NavRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChangePassword = { navController.navigate(NavRoutes.CHANGE_PASSWORD) },
                onNavigateToDatabaseSettings = { navController.navigate(NavRoutes.DATABASE_SETTINGS) }
            )
        }
        composable(route = NavRoutes.DATABASE_SETTINGS) {
            if (isLocked) {
                LaunchedEffect(Unit) {
                    navController.navigate(NavRoutes.SELECT_FILE) {
                        popUpTo(NavRoutes.DATABASE_SETTINGS) { inclusive = true }
                    }
                }
            } else {
                DatabaseSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
        composable(route = NavRoutes.CHANGE_PASSWORD) {
            if (isLocked) {
                LaunchedEffect(Unit) {
                    navController.navigate(NavRoutes.SELECT_FILE) {
                        popUpTo(NavRoutes.CHANGE_PASSWORD) { inclusive = true }
                    }
                }
            } else {
                ChangeMasterPasswordScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
        composable(route = NavRoutes.PASSWORD_GENERATOR) {
            PasswordGeneratorScreen(onNavigateBack = { navController.popBackStack() })
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
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { uuid ->
                        navController.navigate("${NavRoutes.ENTRY_FORM}?uuid=$uuid")
                    }
                )
            }

        }
        composable(
            route = "${NavRoutes.ENTRY_FORM}?uuid={uuid}&group={group}",
            arguments = listOf(
                navArgument("uuid") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("group") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            if (isLocked) {
                LaunchedEffect(Unit) {
                    navController.navigate(NavRoutes.SELECT_FILE) {
                        popUpTo(NavRoutes.ENTRY_FORM) { inclusive = true }
                    }
                }
            } else {
                EntryFormScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
