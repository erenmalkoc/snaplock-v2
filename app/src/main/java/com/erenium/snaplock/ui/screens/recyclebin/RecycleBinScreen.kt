package com.erenium.snaplock.ui.screens.recyclebin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.presentation.recyclebin.RecycleBinViewModel
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.AppTextButton
import com.erenium.snaplock.ui.components.EmptyState
import com.erenium.snaplock.ui.components.EntryAvatar
import com.erenium.snaplock.ui.components.LoadingSpinner
import com.erenium.snaplock.ui.theme.Dimens
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun RecycleBinScreen(
    onNavigateBack: () -> Unit,
    viewModel: RecycleBinViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val errorMessage = state.errorStringId?.let { stringResource(it) }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.onErrorShown()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { (resId, argument) ->
            snackbarHostState.showSnackbar(resources.getString(resId, argument))
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
            state.isLoading -> LoadingSpinner(modifier = contentModifier.fillMaxSize())
            state.entries.isEmpty() -> EmptyState(
                modifier = contentModifier,
                message = stringResource(R.string.recycle_bin_empty),
                icon = Icons.Outlined.Delete
            )
            else -> LazyColumn(
                modifier = contentModifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = Dimens.spaceSm)
            ) {
                item(key = "count") {
                    Text(
                        text = pluralStringResource(R.plurals.recycle_bin_count, state.entries.size, state.entries.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceSm)
                    )
                }
                items(items = state.entries, key = { it.uuid }) { entry ->
                    ListItem(
                        modifier = Modifier.clickable { viewModel.onView(entry) },
                        leadingContent = { EntryAvatar(title = entry.title, icon = entry.icon) },
                        headlineContent = { Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                text = listOfNotNull(
                                    entry.username?.takeIf { it.isNotBlank() },
                                    deletedText(entry)
                                ).joinToString(" · "),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                                IconButton(onClick = { viewModel.onRestore(entry.uuid) }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = stringResource(R.string.recycle_bin_restore)
                                    )
                                }
                                IconButton(onClick = { viewModel.onDeleteRequest(entry) }) {
                                    Icon(
                                        Icons.Filled.DeleteForever,
                                        contentDescription = stringResource(R.string.recycle_bin_delete_forever),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    state.viewing?.let { entry ->
        EntryDialog(
            entry = entry,
            onRestore = { viewModel.onRestore(entry.uuid) },
            onDelete = { viewModel.onDeleteRequest(entry) },
            onDismiss = viewModel::onDismissDialog
        )
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
private fun deletedText(entry: Entry): String {
    val deleted = entry.deletedAt ?: return stringResource(R.string.recycle_bin_deleted_unknown)
    val formatted = remember(deleted) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
            .format(deleted)
    }
    return stringResource(R.string.recycle_bin_deleted_on, formatted)
}

@Composable
private fun EntryDialog(
    entry: Entry,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = deletedText(entry),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!entry.username.isNullOrBlank()) {
                    DialogField(stringResource(R.string.entry_username_label), entry.username)
                }
                if (!entry.url.isNullOrBlank()) {
                    DialogField(stringResource(R.string.entry_url_label), entry.url)
                }
                if (!entry.notes.isNullOrBlank()) {
                    DialogField(stringResource(R.string.entry_notes_label), entry.notes)
                }
                AppTextButton(
                    text = stringResource(R.string.recycle_bin_delete_forever),
                    icon = Icons.Filled.DeleteForever,
                    onClick = onDelete,
                    modifier = Modifier.padding(top = Dimens.spaceMd)
                )
            }
        },
        confirmButton = {
            AppTextButton(
                text = stringResource(R.string.recycle_bin_restore),
                icon = Icons.AutoMirrored.Filled.Undo,
                onClick = onRestore
            )
        },
        dismissButton = {
            AppTextButton(
                text = stringResource(R.string.close_button),
                icon = Icons.Filled.Close,
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun DialogField(label: String, value: String) {
    Column(modifier = Modifier.padding(top = Dimens.spaceSm)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
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
        confirmButton = { AppTextButton(text = confirm, icon = Icons.Filled.Check, onClick = onConfirm) },
        dismissButton = { AppTextButton(
            text = stringResource(R.string.cancel_button),
            icon = Icons.Filled.Close,
            onClick = onDismiss
        ) }
    )
}
