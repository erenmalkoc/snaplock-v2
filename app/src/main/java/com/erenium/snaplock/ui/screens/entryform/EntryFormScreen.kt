package com.erenium.snaplock.ui.screens.entryform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.presentation.entryform.EntryFormViewModel
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
        }
    }
}
