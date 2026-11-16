package com.erenium.snaplock.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.presentation.settings.SiteIconsViewModel
import com.erenium.snaplock.ui.components.AppDialog
import com.erenium.snaplock.ui.components.SettingRow
import com.erenium.snaplock.ui.components.SettingToggleRow
import com.erenium.snaplock.ui.theme.Dimens

@Composable
fun SiteIconsRows(
    snackbarHostState: SnackbarHostState,
    autoDownload: Boolean,
    onAutoDownloadChange: (Boolean) -> Unit,
    viewModel: SiteIconsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val resources = LocalResources.current
    var confirmAuto by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = if (message.count != null) {
                resources.getQuantityString(message.resId, message.count, message.count)
            } else {
                resources.getString(message.resId)
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    SettingToggleRow(
        label = stringResource(R.string.settings_auto_site_icons),
        description = stringResource(R.string.settings_auto_site_icons_desc),
        checked = autoDownload,
        onCheckedChange = { enabled ->
            if (enabled) confirmAuto = true else onAutoDownloadChange(false)
        }
    )

    val progress = state.progress
    if (progress != null) {
        Column(modifier = Modifier.padding(vertical = Dimens.spaceSm)) {
            Text(
                text = stringResource(R.string.site_icons_progress, progress.first, progress.second),
                style = MaterialTheme.typography.bodySmall
            )
            LinearProgressIndicator(
                progress = { if (progress.second == 0) 0f else progress.first.toFloat() / progress.second },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.spaceXs)
            )
        }
    } else {
        SettingRow(
            title = stringResource(R.string.settings_site_icons),
            description = stringResource(R.string.settings_site_icons_desc),
            icon = Icons.Filled.Image,
            onClick = viewModel::onDownloadRequested
        )
    }

    if (confirmAuto) {
        AppDialog(
            title = stringResource(R.string.site_icons_auto_confirm_title),
            confirmText = stringResource(R.string.site_icons_auto_confirm_button),
            confirmIcon = Icons.Filled.Check,
            onConfirm = {
                confirmAuto = false
                onAutoDownloadChange(true)
            },
            onDismiss = { confirmAuto = false }
        ) {
            Text(stringResource(R.string.site_icons_auto_confirm_message))
        }
    }

    state.confirmCount?.let { count ->
        AppDialog(
            title = stringResource(R.string.site_icons_confirm_title),
            confirmText = stringResource(R.string.site_icons_download),
            confirmIcon = Icons.Filled.Download,
            onConfirm = viewModel::onConfirmed,
            onDismiss = viewModel::onDismiss
        ) {
            Text(pluralStringResource(R.plurals.site_icons_confirm_message, count, count))
        }
    }
}
