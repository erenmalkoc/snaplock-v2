package com.erenium.snaplock.ui.screens.autofill

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.erenium.snaplock.R
import com.erenium.snaplock.autofill.AutofillViewModel
import com.erenium.snaplock.ui.components.ErrorState
import com.erenium.snaplock.ui.components.LoadingSpinner
import com.erenium.snaplock.ui.screens.unlock.UnlockScreen

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
        state.isLoading -> LoadingSpinner(modifier = Modifier.fillMaxSize())
        state.loadFailed -> ErrorState(
            message = stringResource(R.string.autofill_load_error),
            onRetry = viewModel::onRetry
        )
        state.isLocked && database == null -> NoDatabase(onOpenApp = onOpenApp, onCancel = onCancel)
        state.isLocked && database != null -> UnlockScreen(
            uri = database.uri,
            onUnlockSuccess = {},
            onNavigateBack = onCancel
        )
        else -> content()
    }
}
