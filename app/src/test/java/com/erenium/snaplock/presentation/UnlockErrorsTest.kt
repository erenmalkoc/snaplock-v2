package com.erenium.snaplock.presentation

import androidx.biometric.BiometricPrompt
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.models.Meta
import com.erenium.snaplock.data.datasource.local.credentialsOf
import com.erenium.snaplock.data.datasource.local.decodeFromByteArray
import com.erenium.snaplock.data.datasource.local.encodeToByteArray
import com.erenium.snaplock.presentation.unlock.BiometricErrorOutcome
import com.erenium.snaplock.presentation.unlock.UnlockError
import com.erenium.snaplock.presentation.unlock.biometricErrorOutcome
import com.erenium.snaplock.presentation.unlock.toUnlockError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class UnlockErrorsTest {
    private val vault = KeePassDatabase.Ver4x.create("r", Meta(name = "r"), credentialsOf("right", null)).encodeToByteArray()

    private fun failureOf(bytes: ByteArray, password: String, keyFile: ByteArray? = null): Throwable =
        runCatching { decodeFromByteArray(bytes, credentialsOf(password, keyFile)) }.exceptionOrNull()
            ?: error("The vault opened")

    @Test
    fun aWrongPasswordFromARealVaultIsReportedAsWrongCredentials() {
        assertEquals(UnlockError.WRONG_CREDENTIALS, failureOf(vault, "wrong").toUnlockError())
    }

    @Test
    fun aWrongKeyFileFromARealVaultIsReportedAsWrongCredentials() {
        assertEquals(UnlockError.WRONG_CREDENTIALS, failureOf(vault, "right", "abc".toByteArray()).toUnlockError())
    }

    @Test
    fun emptyShortAndForeignFilesAreNotKeePassFiles() {
        assertEquals(UnlockError.NOT_KEEPASS, failureOf(ByteArray(0), "x").toUnlockError())
        assertEquals(UnlockError.NOT_KEEPASS, failureOf(vault.copyOf(6), "right").toUnlockError())
        assertEquals(UnlockError.NOT_KEEPASS, failureOf("plain text, not a vault".toByteArray(), "right").toUnlockError())
    }

    @Test
    fun aTruncatedFileIsNotKeePass() {
        assertEquals(UnlockError.NOT_KEEPASS, failureOf(vault.copyOf(vault.size / 2), "right").toUnlockError())
    }

    @Test
    fun aDamagedHeaderIsNotKeePass() {
        val damaged = vault.copyOf().also { it[200] = (it[200] + 1).toByte() }
        assertEquals(UnlockError.NOT_KEEPASS, failureOf(damaged, "right").toUnlockError())
    }

    @Test
    fun storageProblemsAreSeparateFromCorruption() {
        assertEquals(UnlockError.UNREADABLE, IOException("Stream closed").toUnlockError())
        assertEquals(UnlockError.FILE_ACCESS, FileNotFoundException("gone").toUnlockError())
        assertEquals(UnlockError.FILE_ACCESS, SecurityException("denied").toUnlockError())
        assertTrue(UnlockError.FILE_ACCESS.fileActions)
    }

    @Test
    fun outOfMemoryAndUnknownFailuresHaveTheirOwnMessages() {
        assertEquals(UnlockError.TOO_LARGE, OutOfMemoryError().toUnlockError())
        assertEquals(UnlockError.UNKNOWN, IllegalStateException("x").toUnlockError())
    }

    @Test
    fun cancellingTheFingerprintSheetStaysSilent() {
        listOf(
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_CANCELED
        ).forEach { assertEquals(BiometricErrorOutcome.Silent, biometricErrorOutcome(it)) }
    }

    @Test
    fun missingHardwareIsTreatedAsUnavailable() {
        listOf(
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_HW_UNAVAILABLE
        ).forEach { assertEquals(BiometricErrorOutcome.Unavailable, biometricErrorOutcome(it)) }
    }

    @Test
    fun lockoutsAndOtherErrorsGetTheirMessages() {
        assertEquals(
            UnlockError.FINGERPRINT_LOCKOUT,
            (biometricErrorOutcome(BiometricPrompt.ERROR_LOCKOUT) as BiometricErrorOutcome.Show).error
        )
        assertEquals(
            UnlockError.FINGERPRINT_LOCKED,
            (biometricErrorOutcome(BiometricPrompt.ERROR_LOCKOUT_PERMANENT) as BiometricErrorOutcome.Show).error
        )
        assertEquals(
            UnlockError.FINGERPRINT_FAILED,
            (biometricErrorOutcome(BiometricPrompt.ERROR_TIMEOUT) as BiometricErrorOutcome.Show).error
        )
    }
}
