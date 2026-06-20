package com.erenium.snaplock.ui.screens.generator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.usecase.GeneratePasswordUseCase
import com.erenium.snaplock.presentation.generator.PasswordGeneratorViewModel
import com.erenium.snaplock.ui.components.AppCard
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.theme.Dimens

@Composable
fun PasswordGeneratorScreen(
    onNavigateBack: () -> Unit,
    viewModel: PasswordGeneratorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val copiedTemplate = stringResource(R.string.copied_to_clipboard)
    val passwordLabel = stringResource(R.string.entry_password_label)
    val clipboardLabel = stringResource(R.string.clipboard_label_password)
    LaunchedEffect(viewModel) {
        viewModel.copyEvents.collect {
            snackbarHostState.showSnackbar(String.format(copiedTemplate, passwordLabel))
        }
    }

    AppScaffold(
        title = stringResource(R.string.pwgen_title),
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)
        ) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.password,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.regenerate() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.pwgen_regenerate)
                        )
                    }
                    IconButton(onClick = { viewModel.onCopy(clipboardLabel) }) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.copy_password_hint)
                        )
                    }
                }
            }

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.pwgen_length),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = state.options.length.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = state.options.length.toFloat(),
                    onValueChange = { viewModel.onLengthChange(it.toInt()) },
                    valueRange = GeneratePasswordUseCase.MIN_LENGTH.toFloat()..GeneratePasswordUseCase.MAX_LENGTH.toFloat()
                )
            }

            AppCard(modifier = Modifier.fillMaxWidth()) {
                ToggleRow(
                    label = stringResource(R.string.pwgen_lowercase),
                    checked = state.options.useLowercase,
                    onCheckedChange = viewModel::onLowercaseChange
                )
                ToggleRow(
                    label = stringResource(R.string.pwgen_uppercase),
                    checked = state.options.useUppercase,
                    onCheckedChange = viewModel::onUppercaseChange
                )
                ToggleRow(
                    label = stringResource(R.string.pwgen_digits),
                    checked = state.options.useDigits,
                    onCheckedChange = viewModel::onDigitsChange
                )
                ToggleRow(
                    label = stringResource(R.string.pwgen_symbols),
                    checked = state.options.useSymbols,
                    onCheckedChange = viewModel::onSymbolsChange
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spaceXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
