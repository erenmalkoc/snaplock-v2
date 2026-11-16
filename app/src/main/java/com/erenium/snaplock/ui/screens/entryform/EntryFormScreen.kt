package com.erenium.snaplock.ui.screens.entryform

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.local.QrImageDecoder
import com.erenium.snaplock.presentation.entryform.CustomFieldDraft
import com.erenium.snaplock.presentation.entryform.EntryFormViewModel
import com.erenium.snaplock.presentation.entryform.formatExpiryDate
import com.erenium.snaplock.presentation.entryform.pickerMillisToDate
import com.erenium.snaplock.presentation.entryform.toExpiryDate
import com.erenium.snaplock.presentation.entryform.toExpiryInstant
import com.erenium.snaplock.presentation.entryform.toPickerMillis
import com.erenium.snaplock.ui.components.AppCard
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.AppTextButton
import com.erenium.snaplock.ui.components.ErrorState
import com.erenium.snaplock.ui.components.LoadingSpinner
import com.erenium.snaplock.ui.components.PasswordTextField
import com.erenium.snaplock.ui.components.SecondaryButton
import com.erenium.snaplock.ui.components.StrengthMeter
import com.erenium.snaplock.ui.theme.Dimens
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryFormScreen(
    onNavigateBack: () -> Unit,
    viewModel: EntryFormViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val qrDecoder = remember { QrImageDecoder(context.applicationContext) }
    val scanPrompt = stringResource(R.string.scan_prompt)
    var qrNotFound by rememberSaveable { mutableStateOf(false) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmGenerate by rememberSaveable { mutableStateOf(false) }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(viewModel::onOtpEntered)
    }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
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
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(resources.getString(it)) }
    }

    val guardBack = state.isDirty && !state.isSaving
    BackHandler(enabled = guardBack) { showDiscard = true }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.entry_form_discard_title)) },
            text = { Text(stringResource(R.string.entry_form_discard_message)) },
            confirmButton = {
                AppTextButton(
                    text = stringResource(R.string.entry_form_discard_confirm),
                    icon = Icons.Filled.Delete,
                    onClick = {
                        showDiscard = false
                        onNavigateBack()
                    }
                )
            },
            dismissButton = {
                AppTextButton(
                    text = stringResource(R.string.entry_form_keep_editing),
                    icon = Icons.Filled.Edit,
                    onClick = { showDiscard = false }
                )
            }
        )
    }

    if (confirmGenerate) {
        AlertDialog(
            onDismissRequest = { confirmGenerate = false },
            title = { Text(stringResource(R.string.entry_form_replace_title)) },
            text = { Text(stringResource(R.string.entry_form_replace_message)) },
            confirmButton = {
                AppTextButton(
                    text = stringResource(R.string.entry_form_replace_confirm),
                    icon = Icons.Filled.SwapHoriz,
                    onClick = {
                        confirmGenerate = false
                        viewModel.onGeneratePassword()
                    }
                )
            },
            dismissButton = {
                AppTextButton(
                    text = stringResource(R.string.cancel_button),
                    icon = Icons.Filled.Close,
                    onClick = { confirmGenerate = false }
                )
            }
        )
    }

    val title = if (state.isEditMode) {
        stringResource(R.string.entry_form_edit_title)
    } else {
        stringResource(R.string.entry_form_add_title)
    }

    AppScaffold(
        title = title,
        onNavigateBack = { if (guardBack) showDiscard = true else onNavigateBack() },
        snackbarHostState = snackbarHostState,
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
        if (state.loadFailed) {
            ErrorState(
                modifier = contentModifier,
                message = stringResource(R.string.entry_load_error),
                onRetry = viewModel::onRetryLoad
            )
            return@AppScaffold
        }

        val editable = !state.isSaving
        val savingDescription = stringResource(R.string.entry_form_saving)

        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)
        ) {
            if (state.isSaving) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = savingDescription }
                )
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text(stringResource(R.string.entry_form_title_label)) },
                singleLine = true,
                enabled = editable,
                isError = state.titleError,
                supportingText = if (state.titleError) {
                    { Text(stringResource(R.string.entry_form_title_required), color = MaterialTheme.colorScheme.error) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (state.groups.size > 1) {
                GroupPicker(
                    paths = state.groupPaths,
                    groupOrder = state.groups.map { it.uuid },
                    selected = state.groupUuid,
                    enabled = editable,
                    onSelected = viewModel::onGroupSelected
                )
            }

            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text(stringResource(R.string.entry_username_label)) },
                singleLine = true,
                enabled = editable,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Column {
                PasswordTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = stringResource(R.string.entry_password_label),
                    placeholder = stringResource(R.string.enter_password_placeholder),
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.passwordGenerated) {
                    Column(modifier = Modifier.padding(top = Dimens.spaceSm)) {
                        Text(
                            text = stringResource(R.string.entry_form_generated_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SelectionContainer {
                            Text(
                                text = state.password,
                                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                            )
                        }
                    }
                }
                state.passwordStrength?.let { strength ->
                    StrengthMeter(estimate = strength, modifier = Modifier.padding(top = Dimens.spaceSm))
                }
                AppTextButton(
                    text = stringResource(R.string.entry_form_generate),
                    icon = Icons.Filled.Password,
                    enabled = editable,
                    onClick = {
                        if (state.password.isEmpty() || state.passwordGenerated) {
                            viewModel.onGeneratePassword()
                        } else {
                            confirmGenerate = true
                        }
                    }
                )
            }

            OutlinedTextField(
                value = state.url,
                onValueChange = viewModel::onUrlChange,
                label = { Text(stringResource(R.string.entry_url_label)) },
                singleLine = true,
                enabled = editable,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text(stringResource(R.string.entry_notes_label)) },
                enabled = editable,
                minLines = 3,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.spaceXxl * 2)
            )

            ExpiryEditor(expires = state.expires, enabled = editable, onChange = viewModel::onExpiryChange)

            SectionTitle(stringResource(R.string.entry_form_otp_title))
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
                        IconButton(onClick = viewModel::onRemoveOtp, enabled = editable) {
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
                    enabled = editable,
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
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { viewModel.onOtpEntered() }),
                    trailingIcon = {
                        if (state.otpInput.isNotBlank()) {
                            IconButton(onClick = { viewModel.onOtpEntered() }) {
                                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.entry_form_otp_add))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)
                ) {
                    SecondaryButton(
                        text = stringResource(R.string.entry_form_otp_scan),
                        icon = Icons.Filled.QrCodeScanner,
                        enabled = editable,
                        onClick = {
                            qrNotFound = false
                            scanLauncher.launch(
                                ScanOptions()
                                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    .setPrompt(scanPrompt)
                                    .setBeepEnabled(false)
                                    .setOrientationLocked(false)
                            )
                        },
                        fillMaxWidth = false
                    )
                    SecondaryButton(
                        text = stringResource(R.string.entry_form_otp_image),
                        icon = Icons.Filled.Image,
                        enabled = editable,
                        onClick = {
                            qrNotFound = false
                            imageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        fillMaxWidth = false
                    )
                }
            }

            SectionTitle(stringResource(R.string.entry_custom_fields))
            state.customFields.forEachIndexed { index, field ->
                CustomFieldEditor(
                    field = field,
                    isLast = index == state.customFields.lastIndex,
                    enabled = editable,
                    onChange = viewModel::onCustomFieldChange,
                    onRemove = { viewModel.onRemoveCustomField(field.key) }
                )
            }
            SecondaryButton(
                text = stringResource(R.string.entry_form_add_field),
                icon = Icons.Filled.Add,
                onClick = viewModel::onAddCustomField,
                enabled = editable,
                fillMaxWidth = false
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.semantics { heading() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupPicker(
    paths: Map<UUID, String>,
    groupOrder: List<UUID>,
    selected: UUID?,
    enabled: Boolean,
    onSelected: (UUID) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = paths[selected].orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.entry_form_group_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            groupOrder.forEach { uuid ->
                DropdownMenuItem(
                    text = { Text(paths[uuid].orEmpty()) },
                    onClick = {
                        expanded = false
                        onSelected(uuid)
                    }
                )
            }
        }
    }
}

@Composable
private fun CustomFieldEditor(
    field: CustomFieldDraft,
    isLast: Boolean,
    enabled: Boolean,
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
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.entry_form_remove_field))
            }
        }
        if (field.isProtected) {
            PasswordTextField(
                value = field.value,
                onValueChange = { onChange(field.copy(value = it)) },
                label = stringResource(R.string.entry_form_field_value),
                placeholder = "",
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.spaceSm)
            )
        } else {
            OutlinedTextField(
                value = field.value,
                onValueChange = { onChange(field.copy(value = it)) },
                label = { Text(stringResource(R.string.entry_form_field_value)) },
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = if (isLast) ImeAction.Done else ImeAction.Next
                ),
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
                .toggleable(
                    value = field.isProtected,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = { onChange(field.copy(isProtected = it)) }
                )
        ) {
            Checkbox(checked = field.isProtected, onCheckedChange = null, enabled = enabled)
            Text(
                text = stringResource(R.string.entry_form_field_protected),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = Dimens.spaceSm)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryEditor(expires: Instant?, enabled: Boolean, onChange: (Instant?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = expires != null,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = { on ->
                        onChange(if (on) LocalDate.now().plusYears(1).toExpiryInstant() else null)
                    }
                )
        ) {
            Icon(Icons.Filled.Event, contentDescription = null)
            Text(
                text = stringResource(R.string.entry_form_expires),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Dimens.spaceMd)
            )
            Switch(checked = expires != null, onCheckedChange = null, enabled = enabled)
        }
        if (expires != null) {
            AppTextButton(
                text = formatExpiryDate(expires),
                icon = Icons.Filled.CalendarMonth,
                enabled = enabled,
                onClick = { picking = true }
            )
        }
    }
    if (picking && expires != null) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = expires.toExpiryDate().toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                AppTextButton(
                    text = stringResource(R.string.entry_form_save),
                    icon = Icons.Filled.Save,
                    onClick = {
                        pickerState.selectedDateMillis?.let { onChange(pickerMillisToDate(it).toExpiryInstant()) }
                        picking = false
                    }
                )
            },
            dismissButton = {
                AppTextButton(
                    text = stringResource(R.string.cancel_button),
                    icon = Icons.Filled.Close,
                    onClick = { picking = false }
                )
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
