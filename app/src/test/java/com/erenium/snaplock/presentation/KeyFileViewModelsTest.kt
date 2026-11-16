package com.erenium.snaplock.presentation

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.local.DocumentFiles
import com.erenium.snaplock.data.datasource.prefs.EncryptedPrefs
import com.erenium.snaplock.data.datasource.prefs.KeyFilePrefs
import com.erenium.snaplock.data.datasource.cache.SessionCache
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.data.datasource.security.BiometricVault
import com.erenium.snaplock.data.repository.RecentDatabasesRepository
import com.erenium.snaplock.presentation.main.AppMessages
import com.erenium.snaplock.testing.FakeBiometricKeys
import com.erenium.snaplock.domain.usecase.ChangeCredentialsUseCase
import com.erenium.snaplock.domain.usecase.UnlockDatabaseUseCase
import com.erenium.snaplock.presentation.changepassword.ChangeMasterPasswordViewModel
import com.erenium.snaplock.presentation.unlock.UnlockViewModel
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.errors.CryptoError
import app.keemobile.kotpass.models.Meta
import com.erenium.snaplock.data.datasource.local.credentialsOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyFileViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = FakeKdbxRepository()
    private val keyFilePrefs = KeyFilePrefs(context)
    private val encryptedPrefs = EncryptedPrefs(context)
    private val vault = BiometricVault(FakeBiometricKeys(), encryptedPrefs)
    private val appMessages = AppMessages()
    private val recentPrefs = RecentDatabasesPrefs(context)
    private val recentRepository = RecentDatabasesRepository(context, recentPrefs, keyFilePrefs, vault)
    private val sessionCache = SessionCache()
    private val database = Uri.parse("content://test/vault.kdbx")

    private fun unlockViewModel() = UnlockViewModel(
        UnlockDatabaseUseCase(repository),
        vault,
        keyFilePrefs,
        DocumentFiles(context),
        recentRepository,
        appMessages
    )

    private fun changeViewModel() = ChangeMasterPasswordViewModel(
        ChangeCredentialsUseCase(repository), DocumentFiles(context), keyFilePrefs, sessionCache, vault, appMessages
    )

    private fun emptyDatabase() = KeePassDatabase.Ver4x.create("r", Meta(name = "r"), credentialsOf("x", null))

    private fun keyFile(content: String = "key material"): Uri =
        Uri.fromFile(folder.newFile("vault.key").apply { writeText(content) })

    // DocumentFiles reads on a real IO thread, so wait in real time.
    private suspend fun <T> eventually(block: suspend () -> T): T =
        withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(5_000) { block() } }

    @Test
    fun keyFileIsUsedAndRememberedForTheDatabase() = runTest {
        val key = keyFile()
        val viewModel = unlockViewModel()

        viewModel.onKeyFileSelected(key)
        assertTrue(viewModel.uiState.value.canUnlock)
        viewModel.onUnlockClicked(database)

        eventually { viewModel.uiState.first { it.isUnlockSuccessful } }
        assertArrayEquals("key material".toByteArray(), repository.lastKeyFile)
        assertEquals(key, keyFilePrefs.get(database))

        val next = unlockViewModel()
        next.loadKeyFile(database)
        assertEquals("vault.key", next.uiState.value.keyFileName)
    }

    @Test
    fun lostFileAccessIsReportedAsFileAccessNotAsWrongPassword() = runTest {
        repository.unlockFailure = SecurityException("Permission Denial")
        val viewModel = unlockViewModel()

        viewModel.onPasswordChange("secret")
        viewModel.onUnlockClicked(database)

        eventually { viewModel.uiState.first { it.errorStringId != null } }
        assertEquals(R.string.unlock_file_access_error, viewModel.uiState.value.errorStringId)
    }

    @Test
    fun otherFailuresStillReportAWrongPassword() = runTest {
        repository.unlockFailure = CryptoError.InvalidKey("Wrong key used for decryption.")
        val viewModel = unlockViewModel()

        viewModel.onPasswordChange("secret")
        viewModel.onUnlockClicked(database)

        eventually { viewModel.uiState.first { it.errorStringId != null } }
        assertEquals(R.string.wrong_password, viewModel.uiState.value.errorStringId)
    }

    @Test
    fun aPasswordMadeOfSpacesCanUnlock() {
        val viewModel = unlockViewModel()

        viewModel.onPasswordChange("   ")

        assertTrue(viewModel.uiState.value.canUnlock)
    }

    @Test
    fun noPasswordAndNoKeyFileCannotUnlock() {
        val viewModel = unlockViewModel()

        assertTrue(!viewModel.uiState.value.canUnlock)
    }

    @Test
    fun changingThePasswordRemembersTheKeyFileAndDropsOnlyThisDatabasesFingerprintCopy() = runTest {
        encryptedPrefs.save(database, byteArrayOf(1), byteArrayOf(2))
        val other = Uri.parse("content://test/other.kdbx")
        encryptedPrefs.save(other, byteArrayOf(3), byteArrayOf(4))
        sessionCache.setDatabase(emptyDatabase(), database)
        val key = keyFile()
        val viewModel = changeViewModel()

        viewModel.onPasswordChange("new")
        viewModel.onConfirmationChange("new")
        viewModel.onKeyFileSelected(key)
        viewModel.onSave()
        eventually { viewModel.uiState.first { it.enrollFingerprint } }
        viewModel.takeCipher()
        viewModel.onBiometricError()

        eventually { viewModel.done.first() }
        assertEquals(key, keyFilePrefs.get(database))
        assertNull(encryptedPrefs.getEncryptedPassword(database))
        assertTrue(encryptedPrefs.has(other))
        assertEquals(R.string.change_password_fingerprint_off, appMessages.messages.first().id)
    }

    @Test
    fun changingThePasswordOffersToTurnFingerprintBackOnWithTheNewPassword() = runTest {
        sessionCache.setDatabase(emptyDatabase(), database)
        vault.seal(database, vault.enrollCipher().let { (it as com.erenium.snaplock.data.datasource.security.CipherResult.Ready).cipher }, "old".toCharArray())
        val viewModel = changeViewModel()
        viewModel.onPasswordChange("new")
        viewModel.onConfirmationChange("new")

        viewModel.onSave()
        eventually { viewModel.uiState.first { it.enrollFingerprint } }
        val cipher = viewModel.takeCipher()!!
        viewModel.onBiometricSucceeded(cipher)

        eventually { viewModel.done.first() }
        assertTrue(encryptedPrefs.has(database))
        val opened = vault.open(database, (vault.unlockCipher(database) as com.erenium.snaplock.data.datasource.security.CipherResult.Ready).cipher)
        assertEquals("new", String(opened!!))
    }

    @Test
    fun changingThePasswordWithoutFingerprintAsksNothing() = runTest {
        sessionCache.setDatabase(emptyDatabase(), database)
        val viewModel = changeViewModel()
        viewModel.onPasswordChange("new")
        viewModel.onConfirmationChange("new")

        viewModel.onSave()

        eventually { viewModel.done.first() }
        assertFalse(viewModel.uiState.value.enrollFingerprint)
    }

    @Test
    fun changingThePasswordStartsFromTheKeyFileTheDatabaseUses() = runTest {
        val key = keyFile()
        keyFilePrefs.set(database, key)
        sessionCache.setDatabase(emptyDatabase(), database)
        val viewModel = changeViewModel()

        assertEquals(key, viewModel.uiState.value.keyFileUri)
        assertTrue(!viewModel.uiState.value.dropsKeyFile)

        viewModel.onClearKeyFile()
        assertTrue(viewModel.uiState.value.dropsKeyFile)
    }

    @Test
    fun mismatchedConfirmationIsRejected() = runTest {
        val viewModel = changeViewModel()

        viewModel.onPasswordChange("new")
        viewModel.onConfirmationChange("typo")
        viewModel.onSave()

        assertEquals(R.string.create_db_password_mismatch, viewModel.uiState.value.errorStringId)
    }
}
