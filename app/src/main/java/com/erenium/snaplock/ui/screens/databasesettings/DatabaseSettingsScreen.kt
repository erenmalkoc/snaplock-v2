package com.erenium.snaplock.ui.screens.databasesettings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.model.KdfAlgorithm
import com.erenium.snaplock.domain.model.VaultCipher
import com.erenium.snaplock.presentation.databasesettings.DatabaseSettingsUiState
import com.erenium.snaplock.presentation.databasesettings.DatabaseSettingsViewModel
import com.erenium.snaplock.ui.components.AppCard
import com.erenium.snaplock.ui.components.AppDialog
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.ChoiceRow
import com.erenium.snaplock.ui.components.ErrorState
import com.erenium.snaplock.ui.components.LoadingSpinner
import com.erenium.snaplock.ui.components.PrimaryButton
import com.erenium.snaplock.ui.components.SecondaryButton
import com.erenium.snaplock.ui.components.SectionHeader
import com.erenium.snaplock.ui.components.SettingToggleRow
import com.erenium.snaplock.ui.theme.Dimens
import java.text.NumberFormat

private val HistoryOptions = listOf(0, 5, 10, 20, 50, -1)

@Composable
fun DatabaseSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DatabaseSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(resources.getString(it)) }
    }

    val requestBack = { if (state.hasUnsavedChanges) confirmDiscard = true else onNavigateBack() }
    BackHandler(enabled = state.hasUnsavedChanges) { confirmDiscard = true }

    AppScaffold(
        title = stringResource(R.string.database_settings_title),
        onNavigateBack = requestBack,
        snackbarHostState = snackbarHostState
    ) { contentModifier ->
        when {
            state.isLoading -> LoadingSpinner(modifier = contentModifier.fillMaxSize())
            state.loadFailed -> ErrorState(
                message = stringResource(R.string.database_settings_load_error),
                modifier = contentModifier,
                onRetry = viewModel::onRetry
            )
            else -> SettingsForm(state = state, viewModel = viewModel, modifier = contentModifier)
        }
    }

    if (confirmDiscard) {
        AppDialog(
            title = stringResource(R.string.database_discard_title),
            confirmText = stringResource(R.string.database_discard_confirm),
            confirmIcon = Icons.Filled.Delete,
            onConfirm = {
                confirmDiscard = false
                onNavigateBack()
            },
            onDismiss = { confirmDiscard = false },
            dismissText = stringResource(R.string.database_discard_keep),
            dismissIcon = Icons.Filled.Edit
        ) {
            Text(stringResource(R.string.database_discard_message))
        }
    }

    state.lowering?.let { lowering ->
        val format = NumberFormat.getIntegerInstance()
        AppDialog(
            title = stringResource(R.string.database_lower_title),
            confirmText = stringResource(R.string.database_lower_apply),
            confirmIcon = Icons.Filled.Check,
            onConfirm = viewModel::onLoweringConfirmed,
            onDismiss = viewModel::onLoweringDismissed,
            dismissText = stringResource(R.string.database_lower_keep),
            dismissIcon = Icons.Filled.Edit
        ) {
            Text(
                stringResource(
                    R.string.database_lower_message,
                    format.format(lowering.measured),
                    format.format(lowering.current)
                )
            )
        }
    }

    if (state.confirmEncryptionSave) {
        val seconds = state.estimatedUnlockSeconds
        AppDialog(
            title = stringResource(R.string.database_encryption_confirm_title),
            confirmText = stringResource(R.string.entry_form_save),
            confirmIcon = Icons.Filled.Save,
            onConfirm = viewModel::onEncryptionSaveConfirmed,
            onDismiss = viewModel::onEncryptionSaveDismissed
        ) {
            Text(
                if (seconds != null) {
                    pluralStringResource(R.plurals.database_encryption_confirm_seconds, seconds, seconds)
                } else {
                    stringResource(R.string.database_encryption_confirm_generic)
                }
            )
        }
    }
}

@Composable
private fun SettingsForm(
    state: DatabaseSettingsUiState,
    viewModel: DatabaseSettingsViewModel,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)
    ) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.database_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            SettingToggleRow(
                label = stringResource(R.string.database_recycle_bin),
                description = stringResource(R.string.database_recycle_bin_desc),
                checked = state.recycleBinEnabled,
                onCheckedChange = viewModel::onRecycleBinChange,
                modifier = Modifier.padding(top = Dimens.spaceMd)
            )
            if (state.showRecycleBinWarning) {
                Warning(stringResource(R.string.database_recycle_bin_warning))
            }
            Text(
                text = stringResource(R.string.database_history),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = Dimens.spaceMd)
            )
            Text(
                text = stringResource(R.string.database_history_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Dimens.spaceSm)
            )
            ChoiceRow(
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
            if (state.showHistoryWarning) {
                Warning(stringResource(R.string.database_history_warning))
            }
        }

        SectionHeader(
            title = stringResource(R.string.database_encryption),
            subtitle = if (state.saved?.encryption != null) stringResource(R.string.database_encryption_desc) else null
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
        if (state.isSaving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        PrimaryButton(
            icon = Icons.Filled.Save,
            text = stringResource(if (state.isSaving) R.string.database_saving else R.string.entry_form_save),
            onClick = { viewModel.onSave() },
            enabled = state.canSave
        )
    }
}

@Composable
private fun Warning(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = Dimens.spaceSm)
    )
}

@Composable
private fun EncryptionFields(state: DatabaseSettingsUiState, viewModel: DatabaseSettingsViewModel) {
    Text(
        text = stringResource(R.string.database_cipher),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = Dimens.spaceXs)
    )
    ChoiceRow(
        options = VaultCipher.entries,
        selected = state.cipher,
        label = { cipherName(it) },
        onSelect = viewModel::onCipherChange
    )
    cipherHint(state.cipher)?.let { hint ->
        Text(
            text = stringResource(hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.spaceXs)
        )
    }
    Text(
        text = stringResource(R.string.database_kdf),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = Dimens.spaceMd, bottom = Dimens.spaceXs)
    )
    ChoiceRow(
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
        icon = Icons.Filled.Speed,
        text = stringResource(if (state.isBenchmarking) R.string.database_benchmarking else R.string.database_benchmark),
        onClick = viewModel::onBenchmark,
        enabled = !state.isBenchmarking && !state.memoryInvalid && !state.parallelismInvalid,
        modifier = Modifier.padding(top = Dimens.spaceMd)
    )
    if (state.isBenchmarking) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.spaceSm)
        )
    }
    state.benchmarkIterations?.let { measured ->
        Text(
            text = stringResource(
                if (state.isArgon2) R.string.database_benchmark_result_iterations else R.string.database_benchmark_result_rounds,
                NumberFormat.getIntegerInstance().format(measured)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.spaceSm)
        )
    }
}

private fun cipherHint(cipher: VaultCipher): Int? = when (cipher) {
    VaultCipher.AES_256 -> null
    VaultCipher.CHACHA20 -> R.string.database_cipher_hint_chacha
    VaultCipher.TWOFISH -> R.string.database_cipher_hint_twofish
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
