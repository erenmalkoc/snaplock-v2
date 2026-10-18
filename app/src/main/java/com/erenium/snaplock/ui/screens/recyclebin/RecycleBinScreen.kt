package com.erenium.snaplock.ui.screens.recyclebin

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.presentation.recyclebin.RecycleBinViewModel
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.EmptyState
import com.erenium.snaplock.ui.components.EntryAvatar
import com.erenium.snaplock.ui.components.LoadingSpinner
import com.erenium.snaplock.ui.theme.Dimens

@Composable
fun RecycleBinScreen(
    onNavigateBack: () -> Unit,
    viewModel: RecycleBinViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = state.errorStringId?.let { stringResource(it) }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.onErrorShown()
        }
    }

    AppScaffold(
        title = stringResource(R.string.recycle_bin_title),
        onNavigateBack = onNavigateBack,
        applyContentPadding = false,
        snackbarHostState = snackbarHostState,
        actions = {
            if (state.entries.isNotEmpty()) {
                IconButton(onClick = viewModel::onEmptyRequest) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.recycle_bin_empty_action))
                }
            }
        }
    ) { contentModifier ->
        when {
            state.isLoading -> LoadingSpinner(modifier = contentModifier)
            state.entries.isEmpty() -> EmptyState(
                modifier = contentModifier,
                message = stringResource(R.string.recycle_bin_empty),
                icon = Icons.Outlined.Delete
            )
            else -> LazyColumn(
                modifier = contentModifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = Dimens.spaceSm)
            ) {
                items(items = state.entries, key = { it.uuid }) { entry ->
                    ListItem(
                        leadingContent = { EntryAvatar(title = entry.title, icon = entry.icon) },
                        headlineContent = { Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = entry.username?.takeIf { it.isNotBlank() }?.let { username ->
                            { Text(username, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { viewModel.onRestore(entry.uuid) }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = stringResource(R.string.recycle_bin_restore)
                                    )
                                }
                                IconButton(onClick = { viewModel.onDeleteRequest(entry) }) {
                                    Icon(
                                        Icons.Filled.DeleteForever,
                                        contentDescription = stringResource(R.string.recycle_bin_delete_forever)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    state.pendingDelete?.let { entry ->
        ConfirmDialog(
            title = entry.title,
            message = stringResource(R.string.recycle_bin_delete_confirm),
            confirm = stringResource(R.string.recycle_bin_delete_forever),
            onConfirm = viewModel::onDeleteConfirmed,
            onDismiss = viewModel::onDismissDialog
        )
    }
    if (state.confirmEmpty) {
        ConfirmDialog(
            title = stringResource(R.string.recycle_bin_empty_action),
            message = stringResource(R.string.recycle_bin_empty_confirm),
            confirm = stringResource(R.string.recycle_bin_empty_action),
            onConfirm = viewModel::onEmptyConfirmed,
            onDismiss = viewModel::onDismissDialog
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) } }
    )
}
