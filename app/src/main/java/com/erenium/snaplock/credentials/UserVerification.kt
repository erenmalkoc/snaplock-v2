package com.erenium.snaplock.credentials

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

enum class Verification { VERIFIED, NOT_AVAILABLE, CANCELLED }

/**
 * Asks for the fingerprint, face or screen lock before a passkey is used, which is what
 * lets Snaplock tell sites the user was verified. Devices without a screen lock cannot
 * verify, so the passkey is then used without that flag.
 */
suspend fun verifyUser(activity: FragmentActivity, title: String, subtitle: String): Verification {
    val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
        return Verification.NOT_AVAILABLE
    }
    return suspendCancellableCoroutine { continuation ->
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(Verification.VERIFIED)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) continuation.resume(Verification.CANCELLED)
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .build()
        )
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
    }
}
