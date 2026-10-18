package com.erenium.snaplock.ui.screens.entryform

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.presentation.entryform.CustomFieldDraft
import com.erenium.snaplock.presentation.entryform.EntryFormViewModel
import com.erenium.snaplock.ui.components.AppCard
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.LoadingSpinner
import com.erenium.snaplock.ui.components.PasswordTextField
import com.erenium.snaplock.ui.theme.Dimens

@Composable
fun EntryFormScreen(
    onNavigateBack: () -> Unit,
    viewModel: EntryFormViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

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

            PasswordTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = stringResource(R.string.entry_password_label),
                placeholder = stringResource(R.string.enter_password_placeholder),
                modifier = Modifier.fillMaxWidth()
            )

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
