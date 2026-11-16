package com.erenium.snaplock.ui.screens.settings

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.erenium.snaplock.R
import com.erenium.snaplock.autofill.AutofillSetup
import com.erenium.snaplock.autofill.AutofillStatus
import com.erenium.snaplock.domain.model.ThemeMode
import com.erenium.snaplock.presentation.settings.SettingsViewModel
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.ChoiceRow
import com.erenium.snaplock.ui.components.SettingRow
import com.erenium.snaplock.ui.components.SettingToggleRow
import com.erenium.snaplock.ui.components.SettingsGroup
import com.erenium.snaplock.ui.theme.Dimens
import kotlinx.coroutines.launch

private val ClipboardTimeoutOptions = listOf(15, 30, 60, 120)
private val AutoLockOptions = listOf(0, 30, 60, 300)
private const val SOURCE_URL = "https://github.com/erenmalkoc/snaplock-v2"

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChangePassword: () -> Unit,
    onNavigateToDatabaseSettings: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    AppScaffold(
        title = stringResource(R.string.settings_title),
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)
        ) {
            if (AutofillStatus.isSupported()) {
                AutofillGroup(snackbarHostState = snackbarHostState)
            }

            SettingsGroup(title = stringResource(R.string.settings_appearance)) {
                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = Dimens.spaceSm)
                )
                ChoiceRow(
                    options = ThemeMode.entries,
                    selected = settings.themeMode,
                    label = { themeModeLabel(it) },
                    onSelect = viewModel::onThemeModeChange
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingToggleRow(
                        label = stringResource(R.string.settings_dynamic_color),
                        description = stringResource(R.string.settings_dynamic_color_desc),
                        checked = settings.dynamicColor,
                        onCheckedChange = viewModel::onDynamicColorChange,
                        modifier = Modifier.padding(top = Dimens.spaceSm)
                    )
                }
            }

            SettingsGroup(title = stringResource(R.string.settings_security)) {
                Text(
                    text = stringResource(R.string.settings_auto_lock),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.settings_auto_lock_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Dimens.spaceSm)
                )
                ChoiceRow(
                    options = AutoLockOptions,
                    selected = settings.autoLockSeconds,
                    label = { durationLabel(it) },
                    onSelect = viewModel::onAutoLockChange
                )
                SettingToggleRow(
                    label = stringResource(R.string.settings_clipboard_clear),
                    description = stringResource(R.string.settings_clipboard_clear_desc),
                    checked = settings.clipboardAutoClear,
                    onCheckedChange = viewModel::onClipboardAutoClearChange,
                    modifier = Modifier.padding(top = Dimens.spaceMd)
                )
                if (settings.clipboardAutoClear) {
                    Text(
                        text = stringResource(R.string.settings_clipboard_timeout),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(vertical = Dimens.spaceSm)
                    )
                    ChoiceRow(
                        options = (ClipboardTimeoutOptions + settings.clipboardTimeoutSeconds).distinct().sorted(),
                        selected = settings.clipboardTimeoutSeconds,
                        label = { durationLabel(it) },
                        onSelect = viewModel::onClipboardTimeoutChange
                    )
                }
                SettingRow(
                    title = stringResource(R.string.settings_fingerprint),
                    description = stringResource(R.string.settings_fingerprint_desc),
                    icon = Icons.Filled.Fingerprint,
                    modifier = Modifier.padding(top = Dimens.spaceSm)
                )
            }

            SettingsGroup(title = stringResource(R.string.settings_data)) {
                SettingRow(
                    title = stringResource(R.string.change_password_title),
                    description = stringResource(R.string.settings_change_password_desc),
                    icon = Icons.Filled.Password,
                    onClick = onNavigateToChangePassword
                )
                ImportExportRows(snackbarHostState = snackbarHostState)
                BackupsRows(snackbarHostState = snackbarHostState)
            }

            SettingsGroup(title = stringResource(R.string.settings_database)) {
                SettingRow(
                    title = stringResource(R.string.database_settings_title),
                    description = stringResource(R.string.settings_database_settings_desc),
                    icon = Icons.Filled.Tune,
                    onClick = onNavigateToDatabaseSettings
                )
            }

            SettingsGroup(title = stringResource(R.string.settings_online)) {
                SettingToggleRow(
                    label = stringResource(R.string.settings_breach_check),
                    description = stringResource(R.string.settings_breach_check_desc),
                    checked = settings.breachCheckEnabled,
                    onCheckedChange = viewModel::onBreachCheckChange
                )
                SiteIconsRows(
                    snackbarHostState = snackbarHostState,
                    autoDownload = settings.autoSiteIcons,
                    onAutoDownloadChange = viewModel::onAutoSiteIconsChange
                )
            }

            AboutGroup(snackbarHostState = snackbarHostState)
        }
    }
}

@Composable
private fun AutofillGroup(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var setup by remember { mutableStateOf(AutofillStatus.setup(context)) }
    LifecycleResumeEffect(Unit) {
        setup = AutofillStatus.setup(context)
        onPauseOrDispose { }
    }

    SettingsGroup(title = stringResource(R.string.settings_autofill)) {
        if (setup == AutofillSetup.ENABLED) {
            SettingRow(
                title = stringResource(R.string.settings_autofill_enabled),
                description = stringResource(R.string.settings_autofill_chrome_hint),
                icon = Icons.Filled.Settings
            )
        } else {
            SettingRow(
                title = stringResource(
                    if (setup == AutofillSetup.PASSKEYS_OFF) R.string.settings_autofill_make_preferred else R.string.settings_autofill_enable
                ),
                description = stringResource(
                    if (setup == AutofillSetup.PASSKEYS_OFF) R.string.settings_autofill_passkeys_off else R.string.settings_autofill_disabled
                ),
                icon = Icons.Filled.Settings,
                onClick = {
                    val opened = AutofillStatus.enableIntents(context).any { intent ->
                        runCatching { context.startActivity(intent) }.isSuccess
                    }
                    if (!opened) {
                        scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.settings_autofill_manual)) }
                    }
                }
            )
        }
    }
}

@Composable
private fun AboutGroup(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    SettingsGroup(title = stringResource(R.string.settings_about)) {
        SettingRow(
            title = stringResource(R.string.settings_version),
            description = version,
            icon = Icons.Filled.Info
        )
        SettingRow(
            title = stringResource(R.string.settings_license),
            description = stringResource(R.string.settings_license_value),
            icon = Icons.Filled.Gavel
        )
        SettingRow(
            title = stringResource(R.string.settings_source),
            description = stringResource(R.string.settings_source_desc),
            icon = Icons.Filled.Code,
            onClick = {
                val opened = runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri()))
                }.isSuccess
                if (!opened) {
                    scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.settings_link_error)) }
                }
            }
        )
        SettingRow(
            title = stringResource(R.string.settings_privacy),
            description = stringResource(R.string.settings_privacy_desc),
            icon = Icons.Filled.PrivacyTip
        )
    }
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
)
