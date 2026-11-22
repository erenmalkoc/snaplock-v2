package com.erenium.snaplock.ui.screens.licenses

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.erenium.snaplock.R
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.SettingRow
import com.erenium.snaplock.ui.components.SettingsGroup
import com.erenium.snaplock.ui.theme.Dimens
import kotlinx.coroutines.launch

@Composable
fun LicensesScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val open: (String) -> Unit = { url ->
        val opened = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }.isSuccess
        if (!opened) {
            scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.settings_link_error)) }
        }
    }

    AppScaffold(
        title = stringResource(R.string.settings_licenses),
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)
        ) {
            Text(
                text = stringResource(R.string.licenses_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SettingsGroup(title = stringResource(R.string.licenses_libraries)) {
                OpenSourceLibraries.forEach { LicenseRow(item = it, icon = Icons.Filled.Code, onOpen = open) }
            }
            SettingsGroup(title = stringResource(R.string.licenses_data)) {
                OpenSourceData.forEach { LicenseRow(item = it, icon = Icons.Filled.Dataset, onOpen = open) }
            }
        }
    }
}

@Composable
private fun LicenseRow(item: OpenSourceItem, icon: ImageVector, onOpen: (String) -> Unit) {
    SettingRow(
        title = item.name,
        description = item.license,
        icon = icon,
        onClick = { onOpen(item.url) },
        trailing = { Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) }
    )
}
