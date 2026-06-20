package com.erenium.snaplock.ui.screens.entrydetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.erenium.snaplock.R
import com.erenium.snaplock.presentation.entrydetail.CopiedField
import com.erenium.snaplock.presentation.entrydetail.EntryDetailViewModel
import com.erenium.snaplock.ui.components.AppCard
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.DetailFieldRow
import com.erenium.snaplock.ui.components.ErrorState
import com.erenium.snaplock.ui.components.LoadingSpinner
import com.erenium.snaplock.ui.theme.Dimens
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun EntryDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (UUID) -> Unit,
    viewModel: EntryDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val entry = state.entry
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    val usernameLabel = stringResource(R.string.entry_username_label)
    val passwordLabel = stringResource(R.string.entry_password_label)
    val copiedTemplate = stringResource(R.string.copied_to_clipboard)
    val openUrlError = stringResource(R.string.open_url_error)
    LaunchedEffect(viewModel) {
        viewModel.copyEvents.collect { field ->
            val fieldName = when (field) {
                CopiedField.USERNAME -> usernameLabel
                CopiedField.PASSWORD -> passwordLabel
            }
            snackbarHostState.showSnackbar(String.format(copiedTemplate, fieldName))
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.deletedEvents.collect { onNavigateBack() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reload()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.entry_delete_title)) },
            text = { Text(stringResource(R.string.entry_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.onDelete()
                }) {
                    Text(stringResource(R.string.entry_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }

    AppScaffold(
        title = entry?.title ?: stringResource(R.string.entry_loading_title),
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        actions = {
            if (entry != null) {
                IconButton(onClick = { onNavigateToEdit(entry.uuid) }) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.entry_form_edit_title)
                    )
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.entry_delete_title)
                    )
                }
            }
        }
    ) { contentModifier ->
        when {
            state.isLoading -> {
                LoadingSpinner(modifier = contentModifier.fillMaxSize())
            }

            state.errorStringId != null -> {
                ErrorState(
                    modifier = contentModifier,
                    message = stringResource(state.errorStringId!!)
                )
            }

            entry != null -> {
                val usernameClipboardLabel = stringResource(R.string.clipboard_label_username)
                val passwordClipboardLabel = stringResource(R.string.clipboard_label_password)
                Column(
                    modifier = contentModifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        if (!entry.username.isNullOrEmpty()) {
                            DetailFieldRow(
                                icon = Icons.Filled.Person,
                                label = usernameLabel,
                                value = entry.username
                            ) {
                                CopyButton(
                                    contentDescription = stringResource(R.string.copy_username_hint),
                                    onClick = { viewModel.onCopyUsername(usernameClipboardLabel) }
                                )
                            }
                            HorizontalDivider()
                        }

                        DetailFieldRow(
                            icon = Icons.Filled.Lock,
                            label = passwordLabel,
                            value = passwordDisplay(entry.password, state.isPasswordVisible)
                        ) {
                            IconButton(onClick = { viewModel.togglePasswordVisibility() }) {
                                Icon(
                                    imageVector = if (state.isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = stringResource(
                                        if (state.isPasswordVisible) R.string.hide_password_hint else R.string.show_password_hint
                                    )
                                )
                            }
                            CopyButton(
                                contentDescription = stringResource(R.string.copy_password_hint),
                                onClick = { viewModel.onCopyPassword(passwordClipboardLabel) }
                            )
                        }

                        if (!entry.url.isNullOrBlank()) {
                            HorizontalDivider()
                            DetailFieldRow(
                                icon = Icons.Filled.Link,
                                label = stringResource(R.string.entry_url_label),
                                value = entry.url
                            ) {
                                IconButton(onClick = {
                                    runCatching { uriHandler.openUri(normalizeUrl(entry.url)) }
                                        .onFailure {
                                            scope.launch { snackbarHostState.showSnackbar(openUrlError) }
                                        }
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = stringResource(R.string.open_url_hint)
                                    )
                                }
                            }
                        }

                        if (!entry.notes.isNullOrBlank()) {
                            HorizontalDivider()
                            DetailFieldRow(
                                icon = Icons.Filled.Description,
                                label = stringResource(R.string.entry_notes_label),
                                value = entry.notes,
                                valueStyle = MaterialTheme.typography.bodyMedium,
                                singleLineValue = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CopyButton(
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = contentDescription,
            modifier = Modifier.size(Dimens.iconSm)
        )
    }
}

private fun passwordDisplay(password: String?, isVisible: Boolean): String {
    if (password.isNullOrEmpty()) return ""
    return if (isVisible) password else "•".repeat(password.length.coerceAtMost(12))
}

private fun normalizeUrl(url: String): String {
    val trimmed = url.trim()
    return if (trimmed.contains("://")) trimmed else "https://$trimmed"
}
