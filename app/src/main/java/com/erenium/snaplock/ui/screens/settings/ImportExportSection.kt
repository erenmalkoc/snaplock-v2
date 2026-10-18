package com.erenium.snaplock.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.presentation.settings.ImportExportViewModel
import com.erenium.snaplock.ui.components.SecondaryButton
import com.erenium.snaplock.ui.theme.Dimens

/** Buttons for moving logins in from and out to other password managers. */
@Composable
fun ImportExportSection(
    snackbarHostState: SnackbarHostState,
    viewModel: ImportExportViewModel = hiltViewModel()
) {
    val pendingImport by viewModel.pendingImport.collectAsState()
    var confirmExport by remember { mutableStateOf(false) }
    val resources = LocalResources.current

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onImportFilePicked(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.onExportTargetPicked(uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = if (message.count != null) {
                resources.getQuantityString(message.resId, message.count, message.count)
            } else {
                resources.getString(message.resId, message.argument)
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    Text(
        text = stringResource(R.string.settings_import_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Dimens.spaceLg)
    )
    SecondaryButton(
        text = stringResource(R.string.settings_import),
        onClick = { importLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) },
        modifier = Modifier.padding(top = Dimens.spaceSm)
    )
    SecondaryButton(
        text = stringResource(R.string.settings_export),
        onClick = { confirmExport = true },
        modifier = Modifier.padding(top = Dimens.spaceSm)
    )

    pendingImport?.let { import ->
        val groupName = stringResource(R.string.import_group_name, import.source)
        AlertDialog(
            onDismissRequest = viewModel::onImportDismissed,
            title = { Text(stringResource(R.string.import_confirm_title, import.source)) },
            text = {
                Text(pluralStringResource(R.plurals.import_confirm_message, import.entries.size, import.entries.size, groupName))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onImportConfirmed(groupName) }) {
                    Text(stringResource(R.string.settings_import))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onImportDismissed) { Text(stringResource(R.string.cancel_button)) }
            }
        )
    }

    if (confirmExport) {
        AlertDialog(
            onDismissRequest = { confirmExport = false },
            title = { Text(stringResource(R.string.export_warning_title)) },
            text = { Text(stringResource(R.string.export_warning_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmExport = false
                    exportLauncher.launch("snaplock-export.csv")
                }) { Text(stringResource(R.string.settings_export)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmExport = false }) { Text(stringResource(R.string.cancel_button)) }
            }
        )
    }
}
