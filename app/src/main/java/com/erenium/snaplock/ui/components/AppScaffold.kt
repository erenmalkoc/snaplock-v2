package com.erenium.snaplock.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.erenium.snaplock.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    floatingActionButton: @Composable () -> Unit = {},
    applyContentPadding: Boolean = true,
    content: @Composable (contentModifier: Modifier) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            if (title != null) {
                AppTopBar(title = title, onNavigateBack = onNavigateBack, actions = actions)
            }
        },
        snackbarHost = {
            if (snackbarHostState != null) SnackbarHost(snackbarHostState)
        },
        floatingActionButton = floatingActionButton
    ) { innerPadding ->
        val contentModifier = Modifier
            .padding(innerPadding)
            .then(
                if (applyContentPadding) Modifier.padding(Dimens.screenPadding) else Modifier
            )
        content(contentModifier)
    }
}
