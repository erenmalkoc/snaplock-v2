package com.erenium.snaplock.presentation.unlock

import androidx.annotation.StringRes
import androidx.biometric.BiometricPrompt
import app.keemobile.kotpass.errors.CryptoError
import app.keemobile.kotpass.errors.FormatError
import app.keemobile.kotpass.errors.KeyfileError
import com.erenium.snaplock.R
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException

enum class UnlockError(@param:StringRes val message: Int, val fileActions: Boolean = false) {
    WRONG_CREDENTIALS(R.string.wrong_password),
    NOT_KEEPASS(R.string.unlock_not_keepass),
    UNREADABLE(R.string.unlock_read_error),
    TOO_LARGE(R.string.unlock_too_large),
    FILE_ACCESS(R.string.unlock_file_access_error, fileActions = true),
    KEY_FILE(R.string.unlock_key_file_error),
    KEY_FILE_LOST(R.string.unlock_key_file_lost),
    UNKNOWN(R.string.unlock_generic_error),
    FINGERPRINT_LOCKOUT(R.string.fingerprint_lockout),
    FINGERPRINT_LOCKED(R.string.fingerprint_locked),
    FINGERPRINT_FAILED(R.string.fingerprint_failed)
}

fun Throwable.toUnlockError(): UnlockError = when (this) {
    is OutOfMemoryError -> UnlockError.TOO_LARGE
    is SecurityException, is FileNotFoundException -> UnlockError.FILE_ACCESS
    is CryptoError.InvalidKey, is KeyfileError -> UnlockError.WRONG_CREDENTIALS
    is FormatError, is CryptoError, is EOFException -> UnlockError.NOT_KEEPASS
    is IOException -> UnlockError.UNREADABLE
    else -> UnlockError.UNKNOWN
}

sealed interface BiometricErrorOutcome {
    data object Silent : BiometricErrorOutcome
    data object Unavailable : BiometricErrorOutcome
    class Show(val error: UnlockError) : BiometricErrorOutcome
}

fun biometricErrorOutcome(code: Int): BiometricErrorOutcome = when (code) {
    BiometricPrompt.ERROR_USER_CANCELED,
    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
    BiometricPrompt.ERROR_CANCELED -> BiometricErrorOutcome.Silent
    BiometricPrompt.ERROR_NO_BIOMETRICS,
    BiometricPrompt.ERROR_HW_NOT_PRESENT,
    BiometricPrompt.ERROR_HW_UNAVAILABLE -> BiometricErrorOutcome.Unavailable
    BiometricPrompt.ERROR_LOCKOUT -> BiometricErrorOutcome.Show(UnlockError.FINGERPRINT_LOCKOUT)
    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricErrorOutcome.Show(UnlockError.FINGERPRINT_LOCKED)
    else -> BiometricErrorOutcome.Show(UnlockError.FINGERPRINT_FAILED)
}
