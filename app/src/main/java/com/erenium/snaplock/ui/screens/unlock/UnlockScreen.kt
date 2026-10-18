package com.erenium.snaplock.ui.screens.unlock

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.erenium.snaplock.R
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.presentation.unlock.UnlockViewModel
import com.erenium.snaplock.ui.components.AppCard
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.AppTextButton
import com.erenium.snaplock.ui.components.LoadingSpinner
import com.erenium.snaplock.ui.components.PasswordTextField
import com.erenium.snaplock.ui.components.PrimaryButton
import com.erenium.snaplock.ui.theme.Dimens

@Composable
fun UnlockScreen(
    uri: Uri,
    onUnlockSuccess: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: UnlockViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val activity = (LocalActivity.current as? FragmentActivity)
        ?: throw IllegalStateException("Activity null olamaz")

    val title = stringResource(R.string.biometric_login_title)
    val subtitle = stringResource(R.string.biometric_login_subtitle)
    val cancel = stringResource(R.string.cancel_button)
    val promptInfo = remember(title, subtitle, cancel) {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(cancel)
            .build()
    }

    val biometricCallback = remember(viewModel, uri) {
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val cipher = result.cryptoObject?.cipher
                if (cipher != null) {
                    viewModel.onBiometricSuccess(cipher, uri)
                } else {
                    viewModel.onBiometricFailed()
                }
            }

            override fun onAuthenticationError(errCode: Int, errString: CharSequence) {
                viewModel.onBiometricFailed()
            }
        }
    }

    LaunchedEffect(state.showBiometricPrompt, viewModel, activity, promptInfo, biometricCallback) {
        if (state.showBiometricPrompt) {
            try {
                val iv = viewModel.encryptedPrefs.getEncryptionIv()
                if (iv != null) {
                    val cipher = viewModel.cryptoManager.getDecryptCipher(iv)
                    BiometricPrompt(activity, biometricCallback)
                        .authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
                } else {
                    viewModel.onBiometricFailed()
                }
            } catch (e: Exception) {
                viewModel.onBiometricFailed()
            }
        }
    }

    val context = LocalContext.current
    val keyFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { keyFile ->
        if (keyFile != null) {
            // Keep read access so the key file is remembered for this database.
            runCatching {
                context.contentResolver.takePersistableUriPermission(keyFile, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.onKeyFileSelected(keyFile)
        }
    }

    LaunchedEffect(uri) {
        viewModel.loadKeyFile(uri)
    }

    LaunchedEffect(state.isUnlockSuccessful) {
        if (state.isUnlockSuccessful) {
            onUnlockSuccess()
        }
    }

    AppScaffold(
        title = stringResource(R.string.unlock_database_title),
        onNavigateBack = onNavigateBack
    ) { contentModifier ->
        Column(
            modifier = contentModifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                if (state.showBiometricPrompt) {
                    LoadingSpinner(message = stringResource(R.string.authenticating_message))
                } else if (state.isLoading) {
                    LoadingSpinner(message = stringResource(R.string.opening_database_message))
                } else {
                    PasswordTextField(
                        value = state.password,
                        onValueChange = { viewModel.onPasswordChange(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.database_password_label),
                        placeholder = stringResource(R.string.enter_password_placeholder),
                        isError = state.errorStringId != null,
                        supportingText = state.errorStringId?.let { errorId ->
                            {
                                Text(
                                    text = stringResource(id = errorId),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                    val keyFileName = state.keyFileName
                    if (keyFileName != null) {
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
                                    text = keyFileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = viewModel::onClearKeyFile) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.unlock_key_file_clear))
                            }
                        }
                    } else {
                        AppTextButton(
                            text = stringResource(R.string.unlock_key_file_choose),
                            onClick = { keyFileLauncher.launch(arrayOf("*/*")) }
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.spaceMd))

                    PrimaryButton(
                        text = stringResource(R.string.open_button),
                        onClick = { viewModel.onUnlockClicked(uri) },
                        enabled = state.canUnlock
                    )

                    Spacer(modifier = Modifier.height(Dimens.spaceMd))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = state.useBiometrics,
                            onCheckedChange = { viewModel.onUseBiometricsChanged(it) }
                        )
                        Text(
                            stringResource(R.string.enable_biometric_login),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
