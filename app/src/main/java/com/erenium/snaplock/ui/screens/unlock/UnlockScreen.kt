package com.erenium.snaplock.ui.screens.unlock

import android.net.Uri
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Checkbox
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
import com.erenium.snaplock.R
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.presentation.unlock.UnlockViewModel
import com.erenium.snaplock.ui.components.AppCard
import com.erenium.snaplock.ui.components.AppScaffold
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
    val activity = (LocalContext.current as? FragmentActivity)
        ?: throw IllegalStateException("Activity null olamaz")

    val context = LocalContext.current
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(context.getString(R.string.biometric_login_title))
        .setSubtitle(context.getString(R.string.biometric_login_subtitle))
        .setNegativeButtonText(context.getString(R.string.cancel_button))
        .build()

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
                    Spacer(modifier = Modifier.height(Dimens.spaceMd))

                    PrimaryButton(
                        text = stringResource(R.string.open_button),
                        onClick = { viewModel.onUnlockClicked(uri) },
                        enabled = state.password.isNotBlank()
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
