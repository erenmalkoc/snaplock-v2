package com.erenium.snaplock.presentation

import com.erenium.snaplock.testing.awaitReal
import android.content.Context
import android.net.Uri
import androidx.biometric.BiometricPrompt
import androidx.test.core.app.ApplicationProvider
import app.keemobile.kotpass.errors.CryptoError
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.local.DocumentFiles
import com.erenium.snaplock.data.datasource.prefs.EncryptedPrefs
import com.erenium.snaplock.data.datasource.prefs.KeyFilePrefs
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.data.datasource.security.BiometricAvailability
import com.erenium.snaplock.data.datasource.security.BiometricVault
import com.erenium.snaplock.data.repository.RecentDatabasesRepository
import com.erenium.snaplock.domain.usecase.UnlockDatabaseUseCase
import com.erenium.snaplock.presentation.main.AppMessages
import com.erenium.snaplock.presentation.unlock.BiometricState
import com.erenium.snaplock.presentation.unlock.PromptRequest
import com.erenium.snaplock.presentation.unlock.UnlockError
import com.erenium.snaplock.presentation.unlock.UnlockViewModel
import com.erenium.snaplock.testing.FakeBiometricKeys
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnlockViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = FakeKdbxRepository()
    private val keys = FakeBiometricKeys()
    private val encryptedPrefs = EncryptedPrefs(context)
    private val vault = BiometricVault(keys, encryptedPrefs)
    private val keyFilePrefs = KeyFilePrefs(context)
    private val recentPrefs = RecentDatabasesPrefs(context)
    private val recent = RecentDatabasesRepository(context, recentPrefs, keyFilePrefs, vault)
    private val messages = AppMessages()
    private val database = Uri.parse("content://test/vault.kdbx")
    private val otherDatabase = Uri.parse("content://test/other.kdbx")

    private fun viewModel() = UnlockViewModel(
        UnlockDatabaseUseCase(repository), vault, keyFilePrefs, DocumentFiles(context), recent, messages
    )

    private fun UnlockViewModel.enrol(password: String = "secret") {
        start(database)
        onUseBiometricsChanged(true)
        onPasswordChange(password)
        onUnlockClicked(database)
        val cipher = takeCipher(PromptRequest.ENROLL)!!
        onBiometricSucceeded(cipher)
    }

    private suspend fun nextMessage(): Int? = withTimeoutOrNull(50) { messages.messages.first().id }

    @Test
    fun enablingFingerprintSealsThePasswordAfterTheVaultOpened() = runTest {
        val viewModel = viewModel()
        viewModel.start(database)
        viewModel.onUseBiometricsChanged(true)
        viewModel.onPasswordChange("secret")

        viewModel.onUnlockClicked(database)

        assertEquals(PromptRequest.ENROLL, viewModel.uiState.value.prompt)
        assertFalse(viewModel.uiState.value.isUnlockSuccessful)

        val cipher = viewModel.takeCipher(PromptRequest.ENROLL)
        assertNotNull(cipher)
        viewModel.onBiometricSucceeded(cipher!!)

        assertTrue(viewModel.uiState.value.isUnlockSuccessful)
        assertTrue(encryptedPrefs.has(database))
        assertNull(nextMessage())
    }

    @Test
    fun skippingTheEnrolPromptStillOpensTheVaultAndExplainsOnTheNextScreen() = runTest {
        val viewModel = viewModel()
        viewModel.start(database)
        viewModel.onUseBiometricsChanged(true)
        viewModel.onPasswordChange("secret")
        viewModel.onUnlockClicked(database)
        viewModel.takeCipher(PromptRequest.ENROLL)

        viewModel.onBiometricError(BiometricPrompt.ERROR_NEGATIVE_BUTTON)

        assertTrue(viewModel.uiState.value.isUnlockSuccessful)
        assertFalse(encryptedPrefs.has(database))
        assertEquals(R.string.fingerprint_not_enabled, nextMessage())
    }

    @Test
    fun anInvalidatedKeyDuringEnrolIsRegeneratedOnce() = runTest {
        keys.invalidated = true
        val viewModel = viewModel()

        viewModel.enrol()

        assertTrue(viewModel.uiState.value.isUnlockSuccessful)
        assertTrue(encryptedPrefs.has(database))
        assertEquals(1, keys.deletedKeys)
    }

    @Test
    fun enrolFailureNeverBlocksTheVault() = runTest {
        val viewModel = viewModel()
        viewModel.start(database)
        viewModel.onUseBiometricsChanged(true)
        viewModel.onPasswordChange("secret")
        viewModel.onUnlockClicked(database)
        val cipher = viewModel.takeCipher(PromptRequest.ENROLL)!!
        cipher.doFinal(byteArrayOf(1))

        viewModel.onBiometricSucceeded(cipher)

        assertTrue(viewModel.uiState.value.isUnlockSuccessful)
        assertFalse(encryptedPrefs.has(database))
        assertEquals(R.string.fingerprint_not_enabled, nextMessage())
    }

    @Test
    fun withoutTheCheckboxNothingIsEnrolled() {
        val viewModel = viewModel()
        viewModel.start(database)
        viewModel.onPasswordChange("secret")

        viewModel.onUnlockClicked(database)

        assertTrue(viewModel.uiState.value.isUnlockSuccessful)
        assertNull(viewModel.uiState.value.prompt)
    }

    @Test
    fun aFailedUnlockDoesNotStartEnrolment() {
        repository.unlockFailure = CryptoError.InvalidKey("Wrong key used for decryption.")
        val viewModel = viewModel()
        viewModel.start(database)
        viewModel.onUseBiometricsChanged(true)
        viewModel.onPasswordChange("wrong")

        viewModel.onUnlockClicked(database)

        assertNull(viewModel.uiState.value.prompt)
        assertEquals(UnlockError.WRONG_CREDENTIALS, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun anEnrolledDatabaseOffersTheFingerprintOnceAndUnlocksWithTheStoredPassword() = runTest {
        viewModel().enrol("stored secret")
        repository.lastPassword = null

        val viewModel = viewModel()
        viewModel.start(database)
        assertEquals(BiometricState.ENABLED, viewModel.uiState.value.biometric)
        assertEquals(PromptRequest.UNLOCK, viewModel.uiState.value.prompt)

        val cipher = viewModel.takeCipher(PromptRequest.UNLOCK)!!
        viewModel.onBiometricSucceeded(cipher)

        assertEquals("stored secret", repository.lastPassword)
        assertTrue(viewModel.uiState.value.isUnlockSuccessful)
    }

    @Test
    fun theAutomaticPromptIsOfferedOnlyOnce() = runTest {
        viewModel().enrol()
        val viewModel = viewModel()
        viewModel.start(database)
        viewModel.takeCipher(PromptRequest.UNLOCK)

        viewModel.onBiometricError(BiometricPrompt.ERROR_USER_CANCELED)
        viewModel.start(database)

        assertNull(viewModel.uiState.value.prompt)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun theFingerprintButtonAsksAgain() = runTest {
        viewModel().enrol()
        val viewModel = viewModel()
        viewModel.start(database)
        viewModel.takeCipher(PromptRequest.UNLOCK)
        viewModel.onBiometricError(BiometricPrompt.ERROR_NEGATIVE_BUTTON)

        viewModel.onFingerprintClicked()

        assertEquals(PromptRequest.UNLOCK, viewModel.uiState.value.prompt)
    }

    @Test
    fun aDatabaseWithoutACopyIsNotEnabledEvenWhenAnotherOneIs() = runTest {
        viewModel().enrol()

        val viewModel = viewModel()
        viewModel.start(otherDatabase)

        assertEquals(BiometricState.AVAILABLE, viewModel.uiState.value.biometric)
        assertNull(viewModel.uiState.value.prompt)
    }

    @Test
    fun newFingerprintsTurnTheFeatureOffEverywhereWithANeutralNotice() = runTest {
        viewModel().enrol()
        encryptedPrefs.save(otherDatabase, byteArrayOf(1), byteArrayOf(2))
        keys.invalidated = true

        val viewModel = viewModel()
        viewModel.start(database)
        assertNull(viewModel.takeCipher(PromptRequest.UNLOCK))

        assertEquals(R.string.fingerprint_invalidated, viewModel.uiState.value.infoStringId)
        assertNull(viewModel.uiState.value.error)
        assertEquals(BiometricState.AVAILABLE, viewModel.uiState.value.biometric)
        assertFalse(encryptedPrefs.has(database))
        assertFalse(encryptedPrefs.has(otherDatabase))
    }

    @Test
    fun aBlobWithoutItsKeyCountsAsInvalidated() = runTest {
        encryptedPrefs.save(database, byteArrayOf(1), byteArrayOf(2))

        val viewModel = viewModel()
        viewModel.start(database)
        assertNull(viewModel.takeCipher(PromptRequest.UNLOCK))

        assertEquals(R.string.fingerprint_invalidated, viewModel.uiState.value.infoStringId)
        assertFalse(encryptedPrefs.has(database))
    }

    @Test
    fun aChangedMasterPasswordClearsOnlyThatCopyAndExplains() = runTest {
        viewModel().enrol()
        viewModel().apply {
            start(otherDatabase)
            onUseBiometricsChanged(true)
            onPasswordChange("x")
            onUnlockClicked(otherDatabase)
            onBiometricSucceeded(takeCipher(PromptRequest.ENROLL)!!)
        }
        repository.unlockFailure = CryptoError.InvalidKey("Wrong key used for decryption.")

        val viewModel = viewModel()
        viewModel.start(database)
        viewModel.onBiometricSucceeded(viewModel.takeCipher(PromptRequest.UNLOCK)!!)

        assertEquals(R.string.fingerprint_password_changed, viewModel.uiState.value.infoStringId)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isUnlockSuccessful)
        assertFalse(encryptedPrefs.has(database))
        assertTrue(encryptedPrefs.has(otherDatabase))
    }

    @Test
    fun aTamperedCopyIsClearedWithoutAnUnlockAttempt() = runTest {
        viewModel().enrol()
        val stored = encryptedPrefs.getEncryptedPassword(database)!!
        val iv = encryptedPrefs.getIv(database)!!
        stored[0] = (stored[0] + 1).toByte()
        encryptedPrefs.save(database, stored, iv)
        repository.lastPassword = null

        val viewModel = viewModel()
        viewModel.start(database)
        viewModel.onBiometricSucceeded(viewModel.takeCipher(PromptRequest.UNLOCK)!!)

        assertNull(repository.lastPassword)
        assertEquals(R.string.fingerprint_password_changed, viewModel.uiState.value.infoStringId)
        assertFalse(encryptedPrefs.has(database))
    }

    @Test
    fun lockoutAndOtherFingerprintErrorsShowTheirOwnMessage() = runTest {
        viewModel().enrol()

        val viewModel = viewModel()
        viewModel.start(database)
        viewModel.takeCipher(PromptRequest.UNLOCK)
        viewModel.onBiometricError(BiometricPrompt.ERROR_LOCKOUT)
        assertEquals(UnlockError.FINGERPRINT_LOCKOUT, viewModel.uiState.value.error)

        viewModel.onFingerprintClicked()
        viewModel.takeCipher(PromptRequest.UNLOCK)
        viewModel.onBiometricError(BiometricPrompt.ERROR_TIMEOUT)
        assertEquals(UnlockError.FINGERPRINT_FAILED, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun turningTheFeatureOffForgetsOnlyThisDatabase() = runTest {
        viewModel().enrol()
        encryptedPrefs.save(otherDatabase, byteArrayOf(1), byteArrayOf(2))

        val viewModel = viewModel()
        viewModel.start(database)
        viewModel.onTurnOffFingerprint()

        assertEquals(BiometricState.AVAILABLE, viewModel.uiState.value.biometric)
        assertFalse(encryptedPrefs.has(database))
        assertTrue(encryptedPrefs.has(otherDatabase))
    }

    @Test
    fun theOptionFollowsWhatTheDeviceCanDo() {
        keys.availability = BiometricAvailability.NOT_ENROLLED
        val notEnrolled = viewModel().apply { start(database) }
        assertEquals(BiometricState.NOT_ENROLLED, notEnrolled.uiState.value.biometric)

        notEnrolled.onUseBiometricsChanged(true)
        assertFalse(notEnrolled.uiState.value.useBiometrics)

        keys.availability = BiometricAvailability.UNAVAILABLE
        val missing = viewModel().apply { start(database) }
        assertEquals(BiometricState.UNAVAILABLE, missing.uiState.value.biometric)
    }

    @Test
    fun enrollingAFingerprintInSettingsEnablesTheOptionOnReturn() {
        keys.availability = BiometricAvailability.NOT_ENROLLED
        val viewModel = viewModel().apply { start(database) }

        keys.availability = BiometricAvailability.AVAILABLE
        viewModel.refreshBiometric()

        assertEquals(BiometricState.AVAILABLE, viewModel.uiState.value.biometric)
    }

    @Test
    fun theOldGlobalCopyAndKeyAreRemovedOnFirstRun() {
        val legacy = context.getSharedPreferences("snaplock_secure_preferences", Context.MODE_PRIVATE)
        legacy.edit()
            .putString("biometric_encrypted_database_password", "x")
            .putString("biometric_database_password_iv", "y")
            .commit()

        viewModel()

        assertTrue(keys.legacyKeyDeleted)
        assertFalse(legacy.contains("biometric_encrypted_database_password"))
        assertFalse(legacy.contains("biometric_database_password_iv"))
    }

    @Test
    fun aKeyFileWhoseAccessWasLostIsForgottenAndRequestedAgain() = runTest {
        val gone = Uri.parse("content://test/gone.key")
        keyFilePrefs.set(database, gone)
        val viewModel = viewModel()
        viewModel.start(database)
        viewModel.onPasswordChange("secret")

        viewModel.onUnlockClicked(database)
        awaitReal { viewModel.uiState.first { it.error != null } }

        assertEquals(UnlockError.KEY_FILE_LOST, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.keyFileUri)
        assertNull(keyFilePrefs.get(database))
    }

    @Test
    fun removingADatabaseFromTheListForgetsItsCopyAndKeyFile() = runTest {
        viewModel().enrol()
        keyFilePrefs.set(database, Uri.parse("content://test/a.key"))
        recentPrefs.add(database)

        viewModel().onRemoveFromList(database)

        assertFalse(encryptedPrefs.has(database))
        assertNull(keyFilePrefs.get(database))
        assertFalse(database in recentPrefs.uris.value)
    }
}
