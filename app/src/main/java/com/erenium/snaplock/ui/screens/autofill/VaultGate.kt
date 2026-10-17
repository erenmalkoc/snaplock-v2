package com.erenium.snaplock.ui.screens.autofill

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.erenium.snaplock.autofill.AutofillViewModel
import com.erenium.snaplock.ui.components.LoadingSpinner
import com.erenium.snaplock.ui.screens.unlock.UnlockScreen

/** Shows [content] once the vault is open, unlocking the most recent database first. */
@Composable
fun VaultGate(
    viewModel: AutofillViewModel,
    onOpenApp: () -> Unit,
    onCancel: () -> Unit,
    content: @Composable () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val database = state.database
    when {
        state.isLoading -> LoadingSpinner()
        state.isLocked && database == null -> NoDatabase(onOpenApp = onOpenApp, onCancel = onCancel)
        state.isLocked && database != null -> UnlockScreen(
            uri = database.uri,
            onUnlockSuccess = {},
            onNavigateBack = onCancel
        )
        else -> content()
    }
}
