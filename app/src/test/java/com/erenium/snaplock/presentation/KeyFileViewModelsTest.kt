package com.erenium.snaplock.presentation

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.local.DocumentFiles
import com.erenium.snaplock.data.datasource.prefs.EncryptedPrefs
import com.erenium.snaplock.data.datasource.prefs.KeyFilePrefs
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.data.datasource.security.BiometricCryptoManager
import com.erenium.snaplock.domain.usecase.ChangeCredentialsUseCase
import com.erenium.snaplock.domain.usecase.UnlockDatabaseUseCase
import com.erenium.snaplock.presentation.changepassword.ChangeMasterPasswordViewModel
import com.erenium.snaplock.presentation.unlock.UnlockViewModel
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
    private val database = Uri.parse("content://test/vault.kdbx")

    private fun unlockViewModel() = UnlockViewModel(
        UnlockDatabaseUseCase(repository),
        BiometricCryptoManager(),
        encryptedPrefs,
        keyFilePrefs,
        DocumentFiles(context)
    )

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
    fun noPasswordAndNoKeyFileCannotUnlock() {
        val viewModel = unlockViewModel()

        assertTrue(!viewModel.uiState.value.canUnlock)
    }

    @Test
    fun changingThePasswordClearsTheBiometricCopyAndRemembersTheKeyFile() = runTest {
        encryptedPrefs.saveEncryptedCredentials(byteArrayOf(1), byteArrayOf(2))
        val recent = RecentDatabasesPrefs(context).apply { add(database) }
        val key = keyFile()
        val viewModel = ChangeMasterPasswordViewModel(
            ChangeCredentialsUseCase(repository), DocumentFiles(context), keyFilePrefs, encryptedPrefs, recent
        )

        viewModel.onPasswordChange("new")
        viewModel.onConfirmationChange("new")
        viewModel.onKeyFileSelected(key)
        viewModel.onSave()

        eventually { viewModel.done.first() }
        assertNull(encryptedPrefs.getEncryptedPassword())
        assertEquals(key, keyFilePrefs.get(database))
    }

    @Test
    fun mismatchedConfirmationIsRejected() = runTest {
        val viewModel = ChangeMasterPasswordViewModel(
            ChangeCredentialsUseCase(repository), DocumentFiles(context), keyFilePrefs, encryptedPrefs, RecentDatabasesPrefs(context)
        )

        viewModel.onPasswordChange("new")
        viewModel.onConfirmationChange("typo")
        viewModel.onSave()

        assertEquals(R.string.create_db_password_mismatch, viewModel.uiState.value.errorStringId)
    }
}
