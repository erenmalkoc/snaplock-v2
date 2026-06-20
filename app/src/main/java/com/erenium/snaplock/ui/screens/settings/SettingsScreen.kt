package com.erenium.snaplock.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.model.ThemeMode
import com.erenium.snaplock.presentation.settings.SettingsViewModel
import com.erenium.snaplock.ui.components.AppCard
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.theme.Dimens

private val ClipboardTimeoutOptions = listOf(15, 30, 60, 120)
private val AutoLockOptions = listOf(0, 30, 60, 300)

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.uiState.collectAsState()

    AppScaffold(
        title = stringResource(R.string.settings_title),
        onNavigateBack = onNavigateBack
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)
        ) {
            SettingsSection(title = stringResource(R.string.settings_appearance)) {
                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.spaceSm),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.onThemeModeChange(mode) },
                            label = { Text(themeModeLabel(mode)) }
                        )
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ToggleRow(
                        label = stringResource(R.string.settings_dynamic_color),
                        description = stringResource(R.string.settings_dynamic_color_desc),
                        checked = settings.dynamicColor,
                        onCheckedChange = viewModel::onDynamicColorChange
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.settings_clipboard)) {
                ToggleRow(
                    label = stringResource(R.string.settings_clipboard_clear),
                    description = stringResource(R.string.settings_clipboard_clear_desc),
                    checked = settings.clipboardAutoClear,
                    onCheckedChange = viewModel::onClipboardAutoClearChange
                )
                if (settings.clipboardAutoClear) {
                    Text(
                        text = stringResource(R.string.settings_clipboard_timeout),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = Dimens.spaceSm)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.spaceSm),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)
                    ) {
                        ClipboardTimeoutOptions.forEach { seconds ->
                            FilterChip(
                                selected = settings.clipboardTimeoutSeconds == seconds,
                                onClick = { viewModel.onClipboardTimeoutChange(seconds) },
                                label = {
                                    Text(stringResource(R.string.settings_seconds_format, seconds))
                                }
                            )
                        }
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.settings_security)) {
                Text(
                    text = stringResource(R.string.settings_auto_lock),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.settings_auto_lock_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.spaceSm),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)
                ) {
                    AutoLockOptions.forEach { seconds ->
                        FilterChip(
                            selected = settings.autoLockSeconds == seconds,
                            onClick = { viewModel.onAutoLockChange(seconds) },
                            label = { Text(autoLockLabel(seconds)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun autoLockLabel(seconds: Int): String = when {
    seconds <= 0 -> stringResource(R.string.auto_lock_immediate)
    seconds < 60 -> stringResource(R.string.settings_seconds_format, seconds)
    else -> stringResource(R.string.settings_minutes_format, seconds / 60)
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
)

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = Dimens.spaceSm)
        )
        AppCard(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spaceXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
