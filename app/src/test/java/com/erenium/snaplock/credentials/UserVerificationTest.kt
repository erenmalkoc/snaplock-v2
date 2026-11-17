package com.erenium.snaplock.credentials

import androidx.biometric.BiometricPrompt
import org.junit.Assert.assertEquals
import org.junit.Test

class UserVerificationTest {
    @Test
    fun backingOutIsACancellation() {
        listOf(
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_CANCELED
        ).forEach { assertEquals(Verification.CANCELLED, verificationFor(it)) }
    }

    @Test
    fun lockoutsAndHardwareProblemsAreRealFailures() {
        listOf(
            BiometricPrompt.ERROR_LOCKOUT,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
            BiometricPrompt.ERROR_HW_UNAVAILABLE,
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_TIMEOUT,
            BiometricPrompt.ERROR_VENDOR
        ).forEach { assertEquals(Verification.FAILED, verificationFor(it)) }
    }
}
