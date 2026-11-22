package com.erenium.snaplock.ui.navigation

import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.erenium.snaplock.ui.screens.licenses.LicensesScreen
import com.erenium.snaplock.ui.screens.recyclebin.RecycleBinScreen
import com.erenium.snaplock.ui.screens.settings.SettingsScreen
import com.erenium.snaplock.ui.screens.unlock.UnlockScreen
import java.util.UUID
import com.erenium.snaplock.ui.screens.entrylist.isTwoPaneWidth
import com.erenium.snaplock.ui.screens.entrylist.EntryListDetailLayout
import androidx.compose.runtime.saveable.rememberSaveable
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppNavigation(
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val isLocked by mainViewModel.isLocked.collectAsState()
    val shortcut by mainViewModel.pendingShortcut.collectAsState()
    var focusSearch by remember { mutableStateOf(false) }
    var selectedEntry by rememberSaveable { mutableStateOf<UUID?>(null) }
    LaunchedEffect(isLocked) {
        if (isLocked) selectedEntry = null
    }
    val startDestination = remember { if (mainViewModel.isLocked.value) NavRoutes.SELECT_FILE else NavRoutes.ENTRY_LIST }

    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    val goToSelectFile: () -> Unit = {
        if (navController.currentDestination?.route != NavRoutes.SELECT_FILE) {
            navController.navigate(NavRoutes.SELECT_FILE) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }
    LaunchedEffect(isLocked) {
        if (isLocked) goToSelectFile()
    }

    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(mainViewModel) {
        mainViewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(resources.getString(message.id, *message.args.toTypedArray()))
        }
    }

    LaunchedEffect(shortcut) {
        when (shortcut) {
            AppShortcut.GENERATOR -> {
                mainViewModel.onShortcutHandled()
                navController.navigate(NavRoutes.PASSWORD_GENERATOR) { launchSingleTop = true }
            }
            AppShortcut.SEARCH, AppShortcut.NEW_ENTRY -> navController.popBackStack(NavRoutes.ENTRY_LIST, inclusive = false)
            else -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            modifier = Modifier.fillMaxSize(),
            startDestination = startDestination,
            enterTransition = {
                slideInHorizontally(animationSpec = spatialSpec) { it / AXIS_FRACTION } + fadeIn(effectsSpec)
            },
            exitTransition = {
                slideOutHorizontally(animationSpec = spatialSpec) { -it / AXIS_FRACTION } + fadeOut(effectsSpec)
            },
            popEnterTransition = {
                slideInHorizontally(animationSpec = spatialSpec) { -it / AXIS_FRACTION } + fadeIn(effectsSpec)
            },
            popExitTransition = {
                slideOutHorizontally(animationSpec = spatialSpec) { it / AXIS_FRACTION } + fadeOut(effectsSpec)
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
                val uriString = navBackStackEntry.arguments?.getString("uri")
                if (uriString != null) {
                    val uri = uriString.toUri()
                    UnlockScreen(
                        uri = uri,
                        onUnlockSuccess = {
                            navController.navigate(NavRoutes.ENTRY_LIST) {
                                popUpTo(NavRoutes.SELECT_FILE) { inclusive = true }
                            }
                        },
                        onNavigateBack = { navController.popBackStack() },
                        onReplaceFile = { replacement ->
                            val encoded = URLEncoder.encode(replacement.toString(), StandardCharsets.UTF_8.name())
                            navController.navigate("${NavRoutes.UNLOCK_SCREEN}/$encoded") {
                                popUpTo(navBackStackEntry.destination.id) { inclusive = true }
                            }
                        }
                    )
                } else {
                    navController.popBackStack()
                }
            }
            composable(route = NavRoutes.ENTRY_LIST) {
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
                if (!isLocked) {
                    val twoPane = isTwoPaneWidth()
                    val entryList: @Composable (onEntryClick: (UUID) -> Unit) -> Unit = { onEntryClick ->
                        EntryListScreen(
                            focusSearch = focusSearch,
                            onSearchFocused = { focusSearch = false },
                            onNavigateToLock = goToSelectFile,
                            onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) },
                            onNavigateToRecycleBin = { navController.navigate(NavRoutes.RECYCLE_BIN) },
                            onNavigateToHealth = { navController.navigate(NavRoutes.PASSWORD_HEALTH) },
                            onNavigateToGenerator = { navController.navigate(NavRoutes.PASSWORD_GENERATOR) },
                            onAddEntry = { groupUuid ->
                                val route = if (groupUuid != null) "${NavRoutes.ENTRY_FORM}?group=$groupUuid" else NavRoutes.ENTRY_FORM
                                navController.navigate(route)
                            },
                            onEntryClick = onEntryClick,
                            selectedEntry = selectedEntry.takeIf { twoPane }
                        )
                    }
                    if (twoPane) {
                        EntryListDetailLayout(
                            selectedEntry = selectedEntry,
                            onSelectEntry = { selectedEntry = it },
                            onNavigateToEdit = { uuid -> navController.navigate("${NavRoutes.ENTRY_FORM}?uuid=$uuid") }
                        ) { onEntryClick -> entryList(onEntryClick) }
                    } else {
                        entryList { uuid -> navController.navigate("${NavRoutes.ENTRY_DETAIL}/$uuid") }
                    }
                }
            }
            composable(route = NavRoutes.PASSWORD_HEALTH) {
                if (!isLocked) {
                    PasswordHealthScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onEntryClick = { uuid -> navController.navigate("${NavRoutes.ENTRY_DETAIL}/$uuid") }
                    )
                }
            }
            composable(route = NavRoutes.RECYCLE_BIN) {
                if (!isLocked) {
                    RecycleBinScreen(onNavigateBack = { navController.popBackStack() })
                }
            }
            composable(route = NavRoutes.SETTINGS) {
                if (!isLocked) {
                    SettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToChangePassword = { navController.navigate(NavRoutes.CHANGE_PASSWORD) },
                        onNavigateToDatabaseSettings = { navController.navigate(NavRoutes.DATABASE_SETTINGS) },
                        onNavigateToLicenses = { navController.navigate(NavRoutes.LICENSES) }
                    )
                }
            }
            composable(route = NavRoutes.LICENSES) {
                if (!isLocked) {
                    LicensesScreen(onNavigateBack = { navController.popBackStack() })
                }
            }
            composable(route = NavRoutes.DATABASE_SETTINGS) {
                if (!isLocked) {
                    DatabaseSettingsScreen(onNavigateBack = { navController.popBackStack() })
                }
            }
            composable(route = NavRoutes.CHANGE_PASSWORD) {
                if (!isLocked) {
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
                if (!isLocked) {
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
                if (!isLocked) {
                    EntryFormScreen(onNavigateBack = { navController.popBackStack() })
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
        )
    }
}

private const val AXIS_FRACTION = 8
