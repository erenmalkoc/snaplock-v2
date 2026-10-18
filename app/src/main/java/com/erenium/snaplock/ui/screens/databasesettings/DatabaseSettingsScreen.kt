package com.erenium.snaplock.ui.screens.databasesettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.model.KdfAlgorithm
import com.erenium.snaplock.domain.model.VaultCipher
import com.erenium.snaplock.presentation.databasesettings.DatabaseSettingsUiState
import com.erenium.snaplock.presentation.databasesettings.DatabaseSettingsViewModel
import com.erenium.snaplock.ui.components.AppCard
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.PrimaryButton
import com.erenium.snaplock.ui.components.SecondaryButton
import com.erenium.snaplock.ui.theme.Dimens

private val HistoryOptions = listOf(0, 5, 10, 20, 50, -1)

@Composable
fun DatabaseSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DatabaseSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.done.collect { onNavigateBack() }
    }

    AppScaffold(
        title = stringResource(R.string.database_settings_title),
        onNavigateBack = onNavigateBack
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)
        ) {
            if (state.saved == null) return@Column

            AppCard(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text(stringResource(R.string.database_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.spaceMd)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.database_recycle_bin), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = stringResource(R.string.database_recycle_bin_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = state.recycleBinEnabled, onCheckedChange = viewModel::onRecycleBinChange)
                }
                Text(
                    text = stringResource(R.string.database_history),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = Dimens.spaceMd)
                )
                Text(
                    text = stringResource(R.string.database_history_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ChipRow(
                    options = (HistoryOptions + state.historyMaxItems).distinct().sortedBy { if (it < 0) Int.MAX_VALUE else it },
                    selected = state.historyMaxItems,
                    label = { count ->
                        when {
                            count < 0 -> stringResource(R.string.database_history_unlimited)
                            count == 0 -> stringResource(R.string.database_history_off)
                            else -> count.toString()
                        }
                    },
                    onSelect = viewModel::onHistoryMaxItemsChange
                )
            }

            Text(
                text = stringResource(R.string.database_encryption),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            AppCard(modifier = Modifier.fillMaxWidth()) {
                if (state.saved?.encryption == null) {
                    Text(stringResource(R.string.database_kdbx3), style = MaterialTheme.typography.bodyMedium)
                } else {
                    EncryptionFields(state, viewModel)
                }
            }

            state.errorStringId?.let { errorId ->
                Text(stringResource(errorId), color = MaterialTheme.colorScheme.error)
            }
            PrimaryButton(
                text = stringResource(R.string.entry_form_save),
                onClick = viewModel::onSave,
                enabled = state.canSave
            )
        }
    }
}

@Composable
private fun EncryptionFields(state: DatabaseSettingsUiState, viewModel: DatabaseSettingsViewModel) {
    Text(
        text = stringResource(R.string.database_encryption_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = stringResource(R.string.database_cipher),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = Dimens.spaceMd)
    )
    ChipRow(
        options = VaultCipher.entries,
        selected = state.cipher,
        label = { cipherName(it) },
        onSelect = viewModel::onCipherChange
    )
    Text(
        text = stringResource(R.string.database_kdf),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = Dimens.spaceMd)
    )
    ChipRow(
        options = KdfAlgorithm.entries,
        selected = state.kdf,
        label = { kdfName(it) },
        onSelect = viewModel::onKdfChange
    )
    NumberField(
        value = state.iterations,
        onValueChange = viewModel::onIterationsChange,
        label = stringResource(if (state.isArgon2) R.string.database_iterations else R.string.database_rounds),
        isError = state.iterationsInvalid
    )
    if (state.isArgon2) {
        NumberField(
            value = state.memoryMiB,
            onValueChange = viewModel::onMemoryChange,
            label = stringResource(R.string.database_memory),
            isError = state.memoryInvalid,
            supportingText = if (state.memoryIsHigh) stringResource(R.string.database_memory_warning) else null
        )
        NumberField(
            value = state.parallelism,
            onValueChange = viewModel::onParallelismChange,
            label = stringResource(R.string.database_parallelism),
            isError = state.parallelismInvalid
        )
    }
    SecondaryButton(
        text = stringResource(if (state.isBenchmarking) R.string.database_benchmarking else R.string.database_benchmark),
        onClick = viewModel::onBenchmark,
        enabled = !state.isBenchmarking && !state.memoryInvalid && !state.parallelismInvalid,
        modifier = Modifier.padding(top = Dimens.spaceMd)
    )
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = when {
            isError -> { { Text(stringResource(R.string.database_value_invalid)) } }
            supportingText != null -> { { Text(supportingText) } }
            else -> null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.spaceSm)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.spaceXs)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) }
            )
        }
    }
}

// Algorithm names are the same in every language.
private fun cipherName(cipher: VaultCipher): String = when (cipher) {
    VaultCipher.AES_256 -> "AES-256"
    VaultCipher.CHACHA20 -> "ChaCha20"
    VaultCipher.TWOFISH -> "Twofish"
}

private fun kdfName(kdf: KdfAlgorithm): String = when (kdf) {
    KdfAlgorithm.ARGON2D -> "Argon2d"
    KdfAlgorithm.ARGON2ID -> "Argon2id"
    KdfAlgorithm.AES_KDF -> "AES-KDF"
}
