package com.erenium.snaplock.ui.screens.entrydetail

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.model.CustomField
import com.erenium.snaplock.domain.model.EntryVersion
import com.erenium.snaplock.domain.otp.Hotp
import com.erenium.snaplock.domain.otp.OtpParameters
import com.erenium.snaplock.domain.otp.Totp
import com.erenium.snaplock.domain.share.QrShare
import com.erenium.snaplock.presentation.entrydetail.EntryDetailViewModel
import com.erenium.snaplock.presentation.entryform.formatExpiryDate
import com.erenium.snaplock.ui.components.AppCard
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.AppTextButton
import com.erenium.snaplock.ui.components.DetailFieldRow
import com.erenium.snaplock.ui.components.ErrorState
import com.erenium.snaplock.ui.components.LoadingSpinner
import com.erenium.snaplock.ui.theme.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
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
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showQrDialog by rememberSaveable { mutableStateOf(false) }
    val canShareQr = remember(entry) { entry != null && QrShare.options(entry).isNotEmpty() }

    val usernameLabel = stringResource(R.string.entry_username_label)
    val passwordLabel = stringResource(R.string.entry_password_label)
    val openUrlError = stringResource(R.string.open_url_error)
    val restoredMessage = stringResource(R.string.entry_history_restored)
    var selectedVersionIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.copyEvents.collect { event ->
            val text = if (event.clearSeconds != null) {
                resources.getQuantityString(
                    R.plurals.copied_to_clipboard_clears,
                    event.clearSeconds,
                    event.label,
                    event.clearSeconds
                )
            } else {
                resources.getString(R.string.copied_to_clipboard, event.label)
            }
            snackbarHostState.showSnackbar(text)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.restoredEvents.collect { snackbarHostState.showSnackbar(restoredMessage) }
    }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { (resId, argument) ->
            snackbarHostState.showSnackbar(resources.getString(resId, argument))
        }
    }

    var attachmentToSave by rememberSaveable { mutableStateOf<String?>(null) }
    var attachmentToRemove by rememberSaveable { mutableStateOf<String?>(null) }
    val addAttachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onAddAttachment(uri)
    }
    val saveAttachmentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val name = attachmentToSave
        attachmentToSave = null
        if (uri != null && name != null) viewModel.onSaveAttachment(name, uri)
    }

    attachmentToRemove?.let { name ->
        AlertDialog(
            onDismissRequest = { attachmentToRemove = null },
            title = { Text(stringResource(R.string.attachment_remove_title)) },
            text = { Text(stringResource(R.string.attachment_remove_message, name)) },
            confirmButton = {
                AppTextButton(
                    text = stringResource(R.string.attachment_remove),
                    icon = Icons.Filled.Delete,
                    onClick = {
                        attachmentToRemove = null
                        viewModel.onRemoveAttachment(name)
                    }
                )
            },
            dismissButton = {
                AppTextButton(
                    text = stringResource(R.string.cancel_button),
                    icon = Icons.Filled.Close,
                    onClick = { attachmentToRemove = null }
                )
            }
        )
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

    val selectedVersion = entry?.history?.firstOrNull { it.index == selectedVersionIndex }
    selectedVersion?.let { version ->
        VersionDialog(
            version = version,
            onRestore = {
                selectedVersionIndex = null
                viewModel.onRestoreVersion(version.index)
            },
            onDismiss = { selectedVersionIndex = null }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.entry_delete_title)) },
            text = {
                Text(
                    stringResource(
                        if (state.recycleBinEnabled) R.string.entry_delete_message else R.string.entry_delete_message_permanent
                    )
                )
            },
            confirmButton = {
                AppTextButton(
                    text = stringResource(
                        if (state.recycleBinEnabled) R.string.entry_delete_confirm else R.string.recycle_bin_delete_forever
                    ),
                    icon = Icons.Filled.Delete,
                    onClick = {
                        showDeleteDialog = false
                        viewModel.onDelete()
                    }
                )
            },
            dismissButton = {
                AppTextButton(
                    text = stringResource(R.string.cancel_button),
                    icon = Icons.Filled.Close,
                    onClick = { showDeleteDialog = false }
                )
            }
        )
    }

    if (showQrDialog && entry != null) {
        QrShareDialog(entry = entry, onDismiss = { showQrDialog = false })
    }

    AppScaffold(
        title = entry?.title ?: stringResource(R.string.entry_loading_title),
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        actions = {
            if (entry != null) {
                if (canShareQr) {
                    IconButton(onClick = { showQrDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.QrCode2,
                            contentDescription = stringResource(R.string.qr_share_title)
                        )
                    }
                }
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
            entry == null && state.isLoading -> {
                LoadingSpinner(modifier = contentModifier.fillMaxSize())
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
                            value = passwordDisplay(entry.password, state.isPasswordVisible),
                            valueStyle = secretStyle(state.isPasswordVisible)
                        ) {
                            VisibilityButton(
                                visible = state.isPasswordVisible,
                                onClick = viewModel::togglePasswordVisibility
                            )
                            CopyButton(
                                contentDescription = stringResource(R.string.copy_password_hint),
                                onClick = { viewModel.onCopyPassword(passwordClipboardLabel) }
                            )
                        }

                        entry.passkey?.let { passkey ->
                            HorizontalDivider()
                            DetailFieldRow(
                                icon = Icons.Filled.Key,
                                label = stringResource(R.string.entry_passkey_label),
                                value = listOf(passkey.relyingParty, passkey.username)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                            )
                        }

                        entry.otp?.let { otp ->
                            HorizontalDivider()
                            OtpRow(
                                otp = otp,
                                label = stringResource(R.string.entry_otp_label),
                                advancing = state.isAdvancingOtp,
                                onCopy = viewModel::onCopyOtp,
                                onNext = viewModel::onNextOtp
                            )
                        }

                        entry.expires?.let { expires ->
                            HorizontalDivider()
                            val expired = expires.isBefore(Instant.now())
                            DetailFieldRow(
                                icon = Icons.Filled.Event,
                                label = stringResource(if (expired) R.string.entry_expired else R.string.entry_form_expires),
                                value = formatExpiryDate(expires),
                                valueColor = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
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
                                    valueStyle = if (field.isProtected) secretStyle(visible) else MaterialTheme.typography.bodyLarge
                                ) {
                                    if (field.isProtected) {
                                        VisibilityButton(
                                            visible = visible,
                                            onClick = { viewModel.toggleFieldVisibility(field.name) }
                                        )
                                    }
                                    CopyButton(
                                        contentDescription = stringResource(R.string.copy_field_hint, field.name),
                                        onClick = { viewModel.onCopyCustomField(field) }
                                    )
                                }
                            }
                        }
                    }

                    SectionTitle(stringResource(R.string.entry_attachments))
                    AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = false) {
                        entry.attachments.forEach { attachment ->
                            ListItem(
                                leadingContent = { Icon(Icons.Filled.AttachFile, contentDescription = null) },
                                headlineContent = {
                                    Text(attachment.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(Formatter.formatShortFileSize(context, attachment.sizeBytes))
                                },
                                trailingContent = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                                        IconButton(onClick = {
                                            attachmentToSave = attachment.name
                                            saveAttachmentLauncher.launch(attachment.name)
                                        }) {
                                            Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.attachment_save))
                                        }
                                        IconButton(onClick = { attachmentToRemove = attachment.name }) {
                                            Icon(
                                                Icons.Filled.DeleteOutline,
                                                contentDescription = stringResource(R.string.attachment_remove)
                                            )
                                        }
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                        AppTextButton(
                            text = stringResource(R.string.attachment_add),
                            icon = Icons.Filled.Add,
                            onClick = { addAttachmentLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.padding(horizontal = Dimens.spaceSm)
                        )
                    }

                    if (entry.history.isNotEmpty()) {
                        SectionTitle(stringResource(R.string.entry_history))
                        AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = false) {
                            entry.history.forEachIndexed { index, version ->
                                if (index > 0) HorizontalDivider()
                                ListItem(
                                    modifier = Modifier.clickable { selectedVersionIndex = version.index },
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

            state.errorStringId != null -> {
                ErrorState(
                    modifier = contentModifier,
                    message = stringResource(state.errorStringId!!),
                    onRetry = viewModel::reload
                )
            }

            else -> LoadingSpinner(modifier = contentModifier.fillMaxSize())
        }
    }
}

@Composable
private fun OtpRow(
    otp: OtpParameters,
    label: String,
    advancing: Boolean,
    onCopy: (String) -> Unit,
    onNext: () -> Unit
) {
    if (otp.isHotp) {
        HotpRow(otp = otp, label = label, advancing = advancing, onCopy = onCopy, onNext = onNext)
    } else {
        TotpRow(otp = otp, label = label, onCopy = onCopy)
    }
}

@Composable
private fun HotpRow(
    otp: OtpParameters,
    label: String,
    advancing: Boolean,
    onCopy: (String) -> Unit,
    onNext: () -> Unit
) {
    var codeVisible by rememberSaveable { mutableStateOf(true) }
    val code = remember(otp) { Hotp.code(otp) }
    DetailFieldRow(
        icon = Icons.Filled.VerifiedUser,
        label = label + " · " + stringResource(R.string.otp_counter, otp.counter ?: 0L),
        value = if (codeVisible) groupDigits(code) else "•••  •••",
        valueStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
        selectable = codeVisible
    ) {
        IconButton(onClick = onNext, enabled = !advancing) {
            if (advancing) {
                CircularProgressIndicator(modifier = Modifier.size(Dimens.iconSm), strokeWidth = 2.dp)
            } else {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = stringResource(R.string.otp_next))
            }
        }
        IconButton(onClick = { codeVisible = !codeVisible }) {
            Icon(
                imageVector = if (codeVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = stringResource(if (codeVisible) R.string.otp_hide else R.string.otp_show)
            )
        }
        CopyButton(
            contentDescription = stringResource(R.string.copy_field_hint, label),
            onClick = { onCopy(label) }
        )
    }
}

@Composable
private fun TotpRow(
    otp: OtpParameters,
    label: String,
    onCopy: (String) -> Unit
) {
    var codeVisible by rememberSaveable { mutableStateOf(true) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1000 - value % 1000)
            value = System.currentTimeMillis()
        }
    }
    val code = remember(otp, now / 1000 / otp.period) { Totp.code(otp, now) }
    val remaining = Totp.secondsRemaining(otp, now)
    val secondsLeft = pluralStringResource(R.plurals.otp_seconds_left, remaining.toInt(), remaining.toInt())
    DetailFieldRow(
        icon = Icons.Filled.VerifiedUser,
        label = label,
        value = if (codeVisible) groupDigits(code) else "•••  •••",
        valueStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
        selectable = codeVisible
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Dimens.iconLg)
                .semantics { contentDescription = secondsLeft }
        ) {
            CircularProgressIndicator(
                progress = { remaining / otp.period.toFloat() },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 2.dp
            )
            Text(text = remaining.toString(), style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = { codeVisible = !codeVisible }) {
            Icon(
                imageVector = if (codeVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = stringResource(if (codeVisible) R.string.otp_hide else R.string.otp_show)
            )
        }
        CopyButton(
            contentDescription = stringResource(R.string.copy_field_hint, label),
            onClick = { onCopy(label) }
        )
    }
}

private fun groupDigits(code: String): String =
    if (code.all(Char::isDigit) && code.length >= 6) {
        code.substring(0, code.length / 2) + " " + code.substring(code.length / 2)
    } else {
        code
    }

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = Dimens.spaceXs, top = Dimens.spaceLg, bottom = Dimens.spaceSm)
            .semantics { heading() }
    )
}

@Composable
private fun secretStyle(visible: Boolean) =
    if (visible) {
        MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
    } else {
        MaterialTheme.typography.bodyLarge
    }

@Composable
private fun VisibilityButton(visible: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription = stringResource(if (visible) R.string.hide_password_hint else R.string.show_password_hint)
        )
    }
}

@Composable
private fun VersionDialog(
    version: EntryVersion,
    onRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    var secretsVisible by rememberSaveable { mutableStateOf(false) }
    val hasSecrets = !version.password.isNullOrEmpty() || version.customFields.any { it.isProtected }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(formatModified(version)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(version.title, style = MaterialTheme.typography.titleMedium)
                if (!version.username.isNullOrBlank()) {
                    VersionField(stringResource(R.string.entry_username_label), version.username)
                }
                if (!version.password.isNullOrEmpty()) {
                    VersionField(
                        label = stringResource(R.string.entry_password_label),
                        value = passwordDisplay(version.password, secretsVisible),
                        monospace = secretsVisible
                    )
                }
                if (!version.url.isNullOrBlank()) {
                    VersionField(stringResource(R.string.entry_url_label), version.url)
                }
                if (!version.notes.isNullOrBlank()) {
                    VersionField(stringResource(R.string.entry_notes_label), version.notes)
                }
                version.customFields.forEach { field ->
                    val shown = !field.isProtected || secretsVisible
                    VersionField(
                        label = field.name,
                        value = if (shown) field.value else passwordDisplay(field.value, false),
                        monospace = field.isProtected && shown
                    )
                }
                if (hasSecrets) {
                    VisibilityButton(visible = secretsVisible, onClick = { secretsVisible = !secretsVisible })
                }
            }
        },
        confirmButton = { AppTextButton(
            text = stringResource(R.string.entry_history_restore),
            icon = Icons.Filled.Restore,
            onClick = onRestore
        ) },
        dismissButton = { AppTextButton(
            text = stringResource(R.string.cancel_button),
            icon = Icons.Filled.Close,
            onClick = onDismiss
        ) }
    )
}

@Composable
private fun VersionField(label: String, value: String, monospace: Boolean = false) {
    Column(modifier = Modifier.padding(top = Dimens.spaceSm)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.let {
                if (monospace) it.copy(fontFamily = FontFamily.Monospace) else it
            }
        )
    }
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
