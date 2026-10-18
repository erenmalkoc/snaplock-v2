package com.erenium.snaplock.ui.screens.entryform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.local.QrImageDecoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import com.erenium.snaplock.presentation.entryform.CustomFieldDraft
import com.erenium.snaplock.presentation.entryform.EntryFormViewModel
import com.erenium.snaplock.ui.components.AppCard
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.LoadingSpinner
import com.erenium.snaplock.ui.components.PasswordTextField
import com.erenium.snaplock.ui.components.StrengthMeter
import com.erenium.snaplock.ui.theme.Dimens

@Composable
fun EntryFormScreen(
    onNavigateBack: () -> Unit,
    viewModel: EntryFormViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val qrDecoder = remember { QrImageDecoder(context.applicationContext) }
    val scanPrompt = stringResource(R.string.scan_prompt)
    var qrNotFound by remember { mutableStateOf(false) }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(viewModel::onOtpEntered)
    }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = qrDecoder.decode(uri)
                qrNotFound = text == null
                if (text != null) viewModel.onOtpEntered(text)
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.savedEvents.collect { onNavigateBack() }
    }

    val title = if (state.isEditMode) {
        stringResource(R.string.entry_form_edit_title)
    } else {
        stringResource(R.string.entry_form_add_title)
    }

    AppScaffold(
        title = title,
        onNavigateBack = onNavigateBack,
        actions = {
            IconButton(onClick = { viewModel.onSave() }, enabled = state.canSave) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.entry_form_save)
                )
            }
        }
    ) { contentModifier ->
        if (state.isLoading) {
            LoadingSpinner(modifier = contentModifier.fillMaxSize())
            return@AppScaffold
        }

        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text(stringResource(R.string.entry_form_title_label)) },
                singleLine = true,
                isError = state.errorStringId != null,
                supportingText = state.errorStringId?.let { errorId ->
                    {
                        Text(
                            text = stringResource(errorId),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text(stringResource(R.string.entry_username_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Column {
                PasswordTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = stringResource(R.string.entry_password_label),
                    placeholder = stringResource(R.string.enter_password_placeholder),
                    modifier = Modifier.fillMaxWidth()
                )
                state.passwordStrength?.let { strength ->
                    StrengthMeter(estimate = strength, modifier = Modifier.padding(top = Dimens.spaceSm))
                }
                TextButton(onClick = viewModel::onGeneratePassword) {
                    Icon(Icons.Filled.Password, contentDescription = null)
                    Text(
                        text = stringResource(R.string.entry_form_generate),
                        modifier = Modifier.padding(start = Dimens.spaceSm)
                    )
                }
            }

            OutlinedTextField(
                value = state.url,
                onValueChange = viewModel::onUrlChange,
                label = { Text(stringResource(R.string.entry_url_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text(stringResource(R.string.entry_notes_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.spaceXxl * 2)
            )

            ExpiryEditor(expires = state.expires, onChange = viewModel::onExpiryChange)

            Text(
                text = stringResource(R.string.entry_form_otp_title),
                style = MaterialTheme.typography.titleSmall
            )
            val otp = state.otp
            if (otp != null) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null)
                        Text(
                            text = listOfNotNull(otp.issuer, otp.account).joinToString(" · ")
                                .ifEmpty { stringResource(R.string.entry_form_otp_set) },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = Dimens.spaceMd)
                        )
                        IconButton(onClick = viewModel::onRemoveOtp) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.entry_form_otp_remove))
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = state.otpInput,
                    onValueChange = viewModel::onOtpInputChange,
                    label = { Text(stringResource(R.string.entry_form_otp_input)) },
                    singleLine = true,
                    isError = state.otpInputError || qrNotFound,
                    supportingText = when {
                        state.otpInputError -> {
                            { Text(stringResource(R.string.entry_form_otp_invalid), color = MaterialTheme.colorScheme.error) }
                        }
                        qrNotFound -> {
                            { Text(stringResource(R.string.entry_form_otp_qr_not_found), color = MaterialTheme.colorScheme.error) }
                        }
                        else -> null
                    },
                    trailingIcon = {
                        if (state.otpInput.isNotBlank()) {
                            IconButton(onClick = { viewModel.onOtpEntered() }) {
                                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.entry_form_otp_add))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                    OutlinedButton(onClick = {
                        qrNotFound = false
                        scanLauncher.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt(scanPrompt)
                                .setBeepEnabled(false)
                                .setOrientationLocked(false)
                        )
                    }) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                        Text(stringResource(R.string.entry_form_otp_scan), modifier = Modifier.padding(start = Dimens.spaceSm))
                    }
                    OutlinedButton(onClick = {
                        qrNotFound = false
                        imageLauncher.launch("image/*")
                    }) {
                        Icon(Icons.Filled.Image, contentDescription = null)
                        Text(stringResource(R.string.entry_form_otp_image), modifier = Modifier.padding(start = Dimens.spaceSm))
                    }
                }
            }

            Text(
                text = stringResource(R.string.entry_custom_fields),
                style = MaterialTheme.typography.titleSmall
            )
            state.customFields.forEach { field ->
                CustomFieldEditor(
                    field = field,
                    onChange = viewModel::onCustomFieldChange,
                    onRemove = { viewModel.onRemoveCustomField(field.key) }
                )
            }
            OutlinedButton(onClick = viewModel::onAddCustomField) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.entry_form_add_field),
                    modifier = Modifier.padding(start = Dimens.spaceSm)
                )
            }

            if (state.groups.size > 1) {
                Text(
                    text = stringResource(R.string.entry_form_group_label),
                    style = MaterialTheme.typography.titleSmall
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)
                ) {
                    items(items = state.groups, key = { it.uuid }) { group ->
                        FilterChip(
                            selected = state.groupUuid == group.uuid,
                            onClick = { viewModel.onGroupSelected(group.uuid) },
                            label = { Text(group.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomFieldEditor(
    field: CustomFieldDraft,
    onChange: (CustomFieldDraft) -> Unit,
    onRemove: () -> Unit
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = field.name,
                onValueChange = { onChange(field.copy(name = it)) },
                label = { Text(stringResource(R.string.entry_form_field_name)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.entry_form_remove_field))
            }
        }
        if (field.isProtected) {
            PasswordTextField(
                value = field.value,
                onValueChange = { onChange(field.copy(value = it)) },
                label = stringResource(R.string.entry_form_field_value),
                placeholder = "",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.spaceSm)
            )
        } else {
            OutlinedTextField(
                value = field.value,
                onValueChange = { onChange(field.copy(value = it)) },
                label = { Text(stringResource(R.string.entry_form_field_value)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.spaceSm)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.spaceXs)
        ) {
            Checkbox(
                checked = field.isProtected,
                onCheckedChange = { onChange(field.copy(isProtected = it)) }
            )
            Text(
                text = stringResource(R.string.entry_form_field_protected),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryEditor(expires: Instant?, onChange: (Instant?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Event, contentDescription = null)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Dimens.spaceMd)
        ) {
            Text(stringResource(R.string.entry_form_expires), style = MaterialTheme.typography.bodyLarge)
            if (expires != null) {
                TextButton(onClick = { picking = true }, contentPadding = PaddingValues(0.dp)) {
                    Text(formatDate(expires))
                }
            }
        }
        Switch(
            checked = expires != null,
            onCheckedChange = { enabled ->
                // A new expiry starts a year out, the usual rotation for important passwords.
                onChange(if (enabled) LocalDate.now().plusYears(1).atStartOfDay(ZoneOffset.UTC).toInstant() else null)
            }
        )
    }
    if (picking && expires != null) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = expires.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it)) }
                    picking = false
                }) { Text(stringResource(R.string.entry_form_save)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel_button)) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Expiry dates are whole days (the date picker works in UTC days). */
private fun formatDate(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneOffset.UTC).format(instant)
