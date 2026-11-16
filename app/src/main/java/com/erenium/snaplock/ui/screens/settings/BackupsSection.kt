package com.erenium.snaplock.ui.screens.settings

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.presentation.settings.BackupsViewModel
import com.erenium.snaplock.ui.components.AppDialog
import com.erenium.snaplock.ui.components.SettingRow
import com.erenium.snaplock.ui.theme.Dimens
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun BackupsRows(
    snackbarHostState: SnackbarHostState,
    viewModel: BackupsViewModel = hiltViewModel()
) {
    val backups by viewModel.backups.collectAsState()
    val resources = LocalResources.current
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) viewModel.onExportTargetPicked(uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(resources.getString(it)) }
    }

    SettingRow(
        title = stringResource(R.string.settings_backups),
        description = stringResource(R.string.settings_backups_desc),
        icon = Icons.Filled.Backup,
        onClick = viewModel::onShowBackups
    )

    backups?.let { list ->
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
        val saveCopy = stringResource(R.string.backup_save_copy)
        AppDialog(
            title = stringResource(R.string.settings_backups),
            confirmText = stringResource(R.string.close_button),
            confirmIcon = Icons.Filled.Close,
            onConfirm = viewModel::onDismiss,
            onDismiss = viewModel::onDismiss,
            dismissText = null
        ) {
            if (list.isEmpty()) {
                Text(stringResource(R.string.backups_empty))
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = stringResource(R.string.backups_restore_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Dimens.spaceSm)
                    )
                    list.forEach { backup ->
                        ListItem(
                            headlineContent = { Text(formatter.format(backup.created)) },
                            supportingContent = { Text(Formatter.formatShortFileSize(context, backup.size)) },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        viewModel.onBackupChosen(backup)
                                        exportLauncher.launch("snaplock-backup-${backup.created.atZone(ZoneId.systemDefault()).toLocalDate()}.kdbx")
                                    }
                                ) {
                                    Icon(imageVector = Icons.Filled.Save, contentDescription = saveCopy)
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}
