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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.erenium.snaplock.presentation.unlock.UnlockViewModel
import com.erenium.snaplock.ui.components.LoadingSpinner
import com.erenium.snaplock.ui.components.PasswordTextField

@Composable
fun UnlockScreen(
    uri: Uri,
    onUnlockSuccess: () -> Unit,
    viewModel: UnlockViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = (LocalContext.current as? FragmentActivity)
        ?: throw IllegalStateException("Activity null olamaz")

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Oturum Aç")
        .setSubtitle("Oturum açmak için kimliğinizi doğrulayın")
        .setSubtitle("Oturum açmak için kimliğinizi doğrulayın")
        .setNegativeButtonText("İptal")
        .build()

    val biometricCallback = remember(viewModel, uri) {
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                viewModel.onBiometricSuccess(result.cryptoObject!!.cipher!!, uri)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Veritabanını Aç",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (state.showBiometricPrompt) {
                    LoadingSpinner(message = "Kimlik doğrulanıyor...")
                } else if (state.isLoading) {
                    LoadingSpinner(message = "Veritabanı açılıyor...")
                } else {
                    PasswordTextField(
                        value = state.password,
                        onValueChange = { viewModel.onPasswordChange(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Veritabanı Şifresi",
                        placeholder = "Şifrenizi girin",
                        isError = state.error != null,
                        supportingText = state.error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.onUnlockClicked(uri) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.password.isNotBlank()
                    ) {
                        Text("Aç")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = state.useBiometrics,
                            onCheckedChange = { viewModel.onUseBiometricsChanged(it) }
                        )
                        Text(
                            "Parmak iziyle oturum açmayı etkinleştir",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}