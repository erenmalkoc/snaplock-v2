package com.erenium.snaplock.presentation.entrydetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

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

                OutlinedTextField(
                    value = entry.password ?: "",
                    onValueChange = {},
                    label = { Text("Şifre") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (state.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Button(onClick = { viewModel.togglePasswordVisibility() }) {
                            Text(if (state.isPasswordVisible) "Gizle" else "Göster")
                        }
                    }
                )

            }
        }
    }
}