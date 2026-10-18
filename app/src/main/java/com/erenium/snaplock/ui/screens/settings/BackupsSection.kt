package com.erenium.snaplock.ui.screens.settings

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.erenium.snaplock.ui.components.SecondaryButton
import com.erenium.snaplock.ui.theme.Dimens
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The daily copies Snaplock keeps of the open vault, each of which can be saved as a file. */
@Composable
fun BackupsSection(
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

    Text(
        text = stringResource(R.string.settings_backups_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Dimens.spaceLg)
    )
    SecondaryButton(
        text = stringResource(R.string.settings_backups),
        onClick = viewModel::onShowBackups,
        modifier = Modifier.padding(top = Dimens.spaceSm)
    )

    backups?.let { list ->
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
        AlertDialog(
            onDismissRequest = viewModel::onDismiss,
            title = { Text(stringResource(R.string.settings_backups)) },
            text = {
                if (list.isEmpty()) {
                    Text(stringResource(R.string.backups_empty))
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        list.forEach { backup ->
                            ListItem(
                                headlineContent = { Text(formatter.format(backup.created)) },
                                supportingContent = { Text(Formatter.formatShortFileSize(context, backup.size)) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    viewModel.onBackupChosen(backup)
                                    exportLauncher.launch("snaplock-backup-${backup.created.atZone(ZoneId.systemDefault()).toLocalDate()}.kdbx")
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::onDismiss) { Text(stringResource(R.string.close_button)) }
            }
        )
    }
}
