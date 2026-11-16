package com.erenium.snaplock.ui.screens.unlock

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.erenium.snaplock.R
import com.erenium.snaplock.presentation.unlock.BiometricState
import com.erenium.snaplock.presentation.unlock.PromptRequest
import com.erenium.snaplock.presentation.unlock.UnlockViewModel
import com.erenium.snaplock.ui.components.AppCard
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.AppTextButton
import com.erenium.snaplock.ui.components.LoadingSpinner
import com.erenium.snaplock.ui.components.PasswordTextField
import com.erenium.snaplock.ui.components.PrimaryButton
import com.erenium.snaplock.ui.components.rememberFingerprintPrompt
import com.erenium.snaplock.ui.theme.Dimens

@Composable
fun UnlockScreen(
    uri: Uri,
    onUnlockSuccess: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    onReplaceFile: ((Uri) -> Unit)? = null,
    viewModel: UnlockViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val fingerprintPrompt = rememberFingerprintPrompt(
        subtitle = state.databaseName,
        onSucceeded = viewModel::onBiometricSucceeded,
        onError = viewModel::onBiometricError
    )

    LaunchedEffect(state.prompt) {
        val request = state.prompt ?: return@LaunchedEffect
        val cipher = viewModel.takeCipher(request) ?: return@LaunchedEffect
        when (request) {
            PromptRequest.ENROLL -> fingerprintPrompt.showEnroll(cipher)
            PromptRequest.UNLOCK -> fingerprintPrompt.showUnlock(cipher)
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshBiometric()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val keyFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { keyFile ->
        if (keyFile != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(keyFile, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.onKeyFileSelected(keyFile)
        }
    }
    val databaseLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) {
            viewModel.onDatabasePicked(picked)
            onReplaceFile?.invoke(picked)
        }
    }

    LaunchedEffect(uri) {
        viewModel.start(uri)
    }

    LaunchedEffect(state.isUnlockSuccessful) {
        if (state.isUnlockSuccessful) {
            onUnlockSuccess()
        }
    }

    val passwordFocus = remember { FocusRequester() }
    LaunchedEffect(state.started) {
        if (state.started && state.prompt == null && state.biometric != BiometricState.ENABLED) {
            runCatching { passwordFocus.requestFocus() }
        }
    }

    AppScaffold(
        title = stringResource(R.string.unlock_database_title),
        onNavigateBack = onNavigateBack
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                if (state.isLoading) {
                    LoadingSpinner(
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        message = stringResource(R.string.opening_database_message)
                    )
                } else {
                    PasswordTextField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(passwordFocus),
                        isError = state.error != null,
                        keyboardActions = KeyboardActions(
                            onDone = { if (state.canUnlock) viewModel.onUnlockClicked(uri) }
                        )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite }
                    ) {
                        val error = state.error
                        if (error != null) {
                            Text(
                                text = stringResource(error.message),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = Dimens.spaceSm)
                            )
                            if (error.fileActions) {
                                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                                    if (onReplaceFile != null) {
                                        AppTextButton(
                                            icon = Icons.Filled.FolderOpen,
                                            text = stringResource(R.string.unlock_choose_file),
                                            onClick = { databaseLauncher.launch(arrayOf("*/*")) }
                                        )
                                    }
                                    AppTextButton(
                                        icon = Icons.Filled.Delete,
                                        text = stringResource(R.string.unlock_remove_from_list),
                                        onClick = {
                                            viewModel.onRemoveFromList(uri)
                                            onNavigateBack?.invoke()
                                        }
                                    )
                                }
                            }
                        }
                        state.infoStringId?.let { infoId ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(top = Dimens.spaceSm)
                            ) {
                                Icon(
                                    Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = Dimens.spaceSm)
                                )
                                Text(
                                    text = stringResource(infoId),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    KeyFileRow(
                        name = state.keyFileName,
                        onChoose = { keyFileLauncher.launch(arrayOf("*/*")) },
                        onClear = viewModel::onClearKeyFile
                    )

                    Spacer(modifier = Modifier.height(Dimens.spaceMd))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)
                    ) {
                        PrimaryButton(
                            icon = Icons.Filled.LockOpen,
                            text = stringResource(R.string.open_button),
                            onClick = { viewModel.onUnlockClicked(uri) },
                            enabled = state.canUnlock,
                            fillMaxWidth = false,
                            modifier = Modifier.weight(1f)
                        )
                        if (state.biometric == BiometricState.ENABLED) {
                            IconButton(onClick = viewModel::onFingerprintClicked) {
                                Icon(
                                    Icons.Filled.Fingerprint,
                                    contentDescription = stringResource(R.string.fingerprint_use)
                                )
                            }
                        }
                    }

                    FingerprintSection(
                        state = state.biometric,
                        checked = state.useBiometrics,
                        onCheckedChange = viewModel::onUseBiometricsChanged,
                        onTurnOff = viewModel::onTurnOffFingerprint,
                        onOpenSettings = { openEnrollSettings(context) }
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyFileRow(name: String?, onChoose: () -> Unit, onClear: () -> Unit) {
    if (name != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.spaceSm)
        ) {
            Icon(Icons.Filled.Key, contentDescription = null)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Dimens.spaceMd)
            ) {
                Text(
                    text = stringResource(R.string.unlock_key_file),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.unlock_key_file_clear))
            }
        }
    } else {
        AppTextButton(
            icon = Icons.Filled.Key,
            text = stringResource(R.string.unlock_key_file_choose),
            onClick = onChoose
        )
    }
}

@Composable
private fun FingerprintSection(
    state: BiometricState,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onTurnOff: () -> Unit,
    onOpenSettings: () -> Unit
) {
    when (state) {
        BiometricState.UNAVAILABLE -> Unit
        BiometricState.AVAILABLE -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
        ) {
            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(Dimens.spaceMd))
            Text(
                stringResource(R.string.fingerprint_enable_checkbox),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        BiometricState.NOT_ENROLLED -> Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = false, onCheckedChange = null, enabled = false, modifier = Modifier.padding(Dimens.spaceMd))
                Text(
                    text = stringResource(R.string.fingerprint_not_enrolled_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppTextButton(
                icon = Icons.Filled.Settings,
                text = stringResource(R.string.fingerprint_open_settings),
                onClick = onOpenSettings
            )
        }
        BiometricState.ENABLED -> Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.fingerprint_is_on),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.spaceSm)
            )
            AppTextButton(
                icon = Icons.Filled.Fingerprint,
                text = stringResource(R.string.fingerprint_turn_off),
                onClick = onTurnOff
            )
        }
    }
}

private fun openEnrollSettings(context: android.content.Context) {
    val enroll = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
            Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
            BIOMETRIC_STRONG
        )
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    }
    val opened = runCatching { context.startActivity(enroll) }.isSuccess
    if (!opened) runCatching { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
}
