package com.erenium.snaplock.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
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
    maxContentWidth: Dp = Dimens.contentMaxWidth,
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
            .consumeWindowInsets(innerPadding)
            .imePadding()
            .then(
                if (applyContentPadding) Modifier.padding(Dimens.screenPadding) else Modifier
            )
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            content(contentModifier.widthIn(max = maxContentWidth))
        }
    }
}
