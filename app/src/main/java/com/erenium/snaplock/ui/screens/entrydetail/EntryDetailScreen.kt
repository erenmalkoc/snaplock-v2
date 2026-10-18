package com.erenium.snaplock.ui.screens.entrydetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.erenium.snaplock.R
import com.erenium.snaplock.presentation.entrydetail.EntryDetailViewModel
import com.erenium.snaplock.ui.components.AppCard
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.DetailFieldRow
import com.erenium.snaplock.ui.components.ErrorState
import com.erenium.snaplock.ui.components.LoadingSpinner
import com.erenium.snaplock.ui.theme.Dimens
import com.erenium.snaplock.domain.model.EntryVersion
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
    val restoredMessage = stringResource(R.string.entry_history_restored)
    var selectedVersion by remember { mutableStateOf<EntryVersion?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.copyEvents.collect { fieldName ->
            snackbarHostState.showSnackbar(String.format(copiedTemplate, fieldName))
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.restoredEvents.collect { snackbarHostState.showSnackbar(restoredMessage) }
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

    selectedVersion?.let { version ->
        VersionDialog(
            version = version,
            onRestore = {
                selectedVersion = null
                viewModel.onRestoreVersion(version.index)
            },
            onDismiss = { selectedVersion = null }
        )
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

                    if (entry.customFields.isNotEmpty()) {
                        SectionTitle(stringResource(R.string.entry_custom_fields))
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            entry.customFields.forEachIndexed { index, field ->
                                if (index > 0) HorizontalDivider()
                                val visible = !field.isProtected || field.name in state.revealedFields
                                DetailFieldRow(
                                    icon = if (field.isProtected) Icons.Filled.Lock else Icons.AutoMirrored.Filled.Label,
                                    label = field.name,
                                    value = if (visible) field.value else passwordDisplay(field.value, false),
                                    singleLineValue = !visible
                                ) {
                                    if (field.isProtected) {
                                        IconButton(onClick = { viewModel.toggleFieldVisibility(field.name) }) {
                                            Icon(
                                                imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = stringResource(
                                                    if (visible) R.string.hide_password_hint else R.string.show_password_hint
                                                )
                                            )
                                        }
                                    }
                                    CopyButton(
                                        contentDescription = stringResource(R.string.copy_field_hint, field.name),
                                        onClick = { viewModel.onCopyCustomField(field) }
                                    )
                                }
                            }
                        }
                    }

                    if (entry.history.isNotEmpty()) {
                        SectionTitle(stringResource(R.string.entry_history))
                        AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = false) {
                            entry.history.forEachIndexed { index, version ->
                                if (index > 0) HorizontalDivider()
                                ListItem(
                                    modifier = Modifier.clickable { selectedVersion = version },
                                    leadingContent = { Icon(Icons.Filled.History, contentDescription = null) },
                                    headlineContent = { Text(formatModified(version)) },
                                    supportingContent = {
                                        Text(
                                            text = version.username?.takeIf { it.isNotBlank() } ?: version.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Dimens.spaceXs, top = Dimens.spaceLg, bottom = Dimens.spaceSm)
    )
}

@Composable
private fun VersionDialog(
    version: EntryVersion,
    onRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(formatModified(version)) },
        text = {
            Column {
                Text(version.title, style = MaterialTheme.typography.titleMedium)
                if (!version.username.isNullOrBlank()) {
                    Text(version.username, style = MaterialTheme.typography.bodyMedium)
                }
                if (!version.password.isNullOrEmpty()) {
                    Text(
                        text = passwordDisplay(version.password, passwordVisible),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(top = Dimens.spaceXs)
                            .clickable { passwordVisible = !passwordVisible }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onRestore) { Text(stringResource(R.string.entry_history_restore)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) } }
    )
}

@Composable
private fun formatModified(version: EntryVersion): String {
    val modified = version.modified ?: return stringResource(R.string.entry_history_unknown_date)
    return remember(modified) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
            .format(modified)
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
