package com.erenium.snaplock.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.presentation.settings.ImportExportViewModel
import com.erenium.snaplock.ui.components.AppDialog
import com.erenium.snaplock.ui.components.SettingRow

@Composable
fun ImportExportRows(
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

    SettingRow(
        title = stringResource(R.string.settings_import),
        description = stringResource(R.string.settings_import_desc),
        icon = Icons.Filled.FileDownload,
        onClick = { importLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }
    )
    SettingRow(
        title = stringResource(R.string.settings_export),
        description = stringResource(R.string.settings_export_desc),
        icon = Icons.Filled.FileUpload,
        onClick = { confirmExport = true }
    )

    pendingImport?.let { import ->
        val groupName = stringResource(R.string.import_group_name, import.source)
        AppDialog(
            title = stringResource(R.string.import_confirm_title, import.source),
            confirmText = stringResource(R.string.settings_import),
            confirmIcon = Icons.Filled.FileDownload,
            onConfirm = { viewModel.onImportConfirmed(groupName) },
            onDismiss = viewModel::onImportDismissed
        ) {
            Text(pluralStringResource(R.plurals.import_confirm_message, import.entries.size, import.entries.size, groupName))
        }
    }

    if (confirmExport) {
        AppDialog(
            title = stringResource(R.string.export_warning_title),
            confirmText = stringResource(R.string.settings_export),
            confirmIcon = Icons.Filled.FileUpload,
            onConfirm = {
                confirmExport = false
                exportLauncher.launch("snaplock-export.csv")
            },
            onDismiss = { confirmExport = false }
        ) {
            Text(stringResource(R.string.export_warning_message))
        }
    }
}
