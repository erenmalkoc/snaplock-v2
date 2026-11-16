package com.erenium.snaplock.ui.components

import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.erenium.snaplock.R
import javax.crypto.Cipher

class FingerprintPrompt internal constructor(
    private val prompt: BiometricPrompt,
    private val enrollInfo: BiometricPrompt.PromptInfo,
    private val unlockInfo: BiometricPrompt.PromptInfo
) {
    fun showEnroll(cipher: Cipher) = prompt.authenticate(enrollInfo, BiometricPrompt.CryptoObject(cipher))

    fun showUnlock(cipher: Cipher) = prompt.authenticate(unlockInfo, BiometricPrompt.CryptoObject(cipher))
}

/** One system fingerprint prompt for the screen, created once and reused for every request. */
@Composable
fun rememberFingerprintPrompt(
    subtitle: String,
    onSucceeded: (Cipher) -> Unit,
    onError: (Int) -> Unit
): FingerprintPrompt {
    val activity = LocalActivity.current as? FragmentActivity
        ?: error("Fingerprint prompts need a FragmentActivity")
    val succeeded by rememberUpdatedState(onSucceeded)
    val failed by rememberUpdatedState(onError)

    val enrollTitle = stringResource(R.string.fingerprint_enroll_title)
    val enrollSubtitle = stringResource(R.string.fingerprint_enroll_subtitle)
    val enrollNegative = stringResource(R.string.fingerprint_enroll_skip)
    val unlockTitle = stringResource(R.string.fingerprint_unlock_title)
    val unlockNegative = stringResource(R.string.fingerprint_use_password)

    val prompt = remember(activity) {
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val cipher = result.cryptoObject?.cipher
                    if (cipher != null) succeeded(cipher) else failed(BiometricPrompt.ERROR_VENDOR)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    failed(errorCode)
                }
            }
        )
    }

    return remember(prompt, subtitle, enrollTitle, enrollSubtitle, enrollNegative, unlockTitle, unlockNegative) {
        FingerprintPrompt(
            prompt = prompt,
            enrollInfo = promptInfo(enrollTitle, enrollSubtitle, enrollNegative),
            unlockInfo = promptInfo(unlockTitle, subtitle, unlockNegative)
        )
    }
}

private fun promptInfo(title: String, subtitle: String, negative: String): BiometricPrompt.PromptInfo =
    BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText(negative)
        .setAllowedAuthenticators(BIOMETRIC_STRONG)
        .setConfirmationRequired(false)
        .build()
