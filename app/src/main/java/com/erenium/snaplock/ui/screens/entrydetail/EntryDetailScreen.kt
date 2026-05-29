package com.erenium.snaplock.ui.screens.entrydetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.presentation.entrydetail.EntryDetailViewModel
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.ErrorState
import com.erenium.snaplock.ui.components.LoadingSpinner
import com.erenium.snaplock.ui.theme.Dimens

@Composable
fun EntryDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: EntryDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val entry = state.entry

    AppScaffold(
        title = entry?.title ?: stringResource(R.string.entry_loading_title),
        onNavigateBack = onNavigateBack
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
                val clipboardLabel = stringResource(R.string.clipboard_label_password)
                Column(
                    modifier = contentModifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)
                ) {
                    OutlinedTextField(
                        value = entry.username ?: "",
                        onValueChange = {},
                        label = { Text(stringResource(R.string.entry_username_label)) },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = entry.password ?: "",
                        onValueChange = {},
                        label = { Text(stringResource(R.string.entry_password_label)) },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (state.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { viewModel.togglePasswordVisibility() }) {
                                    Icon(
                                        imageVector = if (state.isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = stringResource(
                                            if (state.isPasswordVisible) R.string.hide_password_hint else R.string.show_password_hint
                                        )
                                    )
                                }
                                IconButton(onClick = { viewModel.onCopyPassword(clipboardLabel) }) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = stringResource(R.string.copy_password_hint)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
