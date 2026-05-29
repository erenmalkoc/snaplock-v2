package com.erenium.snaplock.presentation.entrydetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.erenium.snaplock.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: EntryDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val entry = state.entry

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry?.title ?: "...") },
                navigationIcon = {
                    Button(onClick = onNavigateBack) { Text("Geri") }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            CircularProgressIndicator()
        } else if (entry != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = entry.username ?: "",
                    onValueChange = {},
                    label = { Text("Kullanıcı Adı") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val clipboardLabel = stringResource(R.string.clipboard_label_password)
                OutlinedTextField(
                    value = entry.password ?: "",
                    onValueChange = {},
                    label = { Text("Şifre") },
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