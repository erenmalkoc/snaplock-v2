import android.net.Uri
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.presentation.unlock.UnlockViewModel

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
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!state.showBiometricPrompt) {

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.useBiometrics,
                    onCheckedChange = { viewModel.onUseBiometricsChanged(it) }
                )
                Text("Parmak iziyle oturum açmayı etkinleştir")
            }

        } else {
            CircularProgressIndicator()
            Text("Kimlik doğrulanıyor...")
        }
    }
}