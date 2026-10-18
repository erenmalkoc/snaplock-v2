package com.erenium.snaplock.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.presentation.settings.SiteIconsViewModel
import com.erenium.snaplock.ui.components.SecondaryButton
import com.erenium.snaplock.ui.theme.Dimens

/** Opt-in download of site icons into the vault, asked for each time. */
@Composable
fun SiteIconsSection(
    snackbarHostState: SnackbarHostState,
    viewModel: SiteIconsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val resources = LocalResources.current

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

    Text(text = stringResource(R.string.settings_site_icons), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Dimens.spaceMd))
    Text(
        text = stringResource(R.string.settings_site_icons_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val progress = state.progress
    if (progress != null) {
        Text(
            text = stringResource(R.string.site_icons_progress, progress.first, progress.second),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Dimens.spaceSm)
        )
        LinearProgressIndicator(
            progress = { if (progress.second == 0) 0f else progress.first.toFloat() / progress.second },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.spaceXs)
        )
    } else {
        SecondaryButton(
            text = stringResource(R.string.settings_site_icons),
            onClick = viewModel::onDownloadRequested,
            modifier = Modifier.padding(top = Dimens.spaceSm)
        )
    }

    state.confirmCount?.let { count ->
        AlertDialog(
            onDismissRequest = viewModel::onDismiss,
            title = { Text(stringResource(R.string.site_icons_confirm_title)) },
            text = { Text(pluralStringResource(R.plurals.site_icons_confirm_message, count, count)) },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmed) { Text(stringResource(R.string.site_icons_download)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismiss) { Text(stringResource(R.string.cancel_button)) }
            }
        )
    }
}
