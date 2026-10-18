package com.erenium.snaplock.presentation

import com.erenium.snaplock.data.crypto.KdfBenchmark
import com.erenium.snaplock.domain.model.DatabaseSettings
import com.erenium.snaplock.domain.model.EncryptionSettings
import com.erenium.snaplock.domain.model.KdfAlgorithm
import com.erenium.snaplock.domain.model.VaultCipher
import com.erenium.snaplock.presentation.databasesettings.DatabaseSettingsViewModel
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DatabaseSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val argon2 = EncryptionSettings(VaultCipher.AES_256, KdfAlgorithm.ARGON2D, 8, 32L * 1024 * 1024, 2)
    private val repository = FakeKdbxRepository().apply {
        databaseSettings = DatabaseSettings("Family", true, 10, argon2)
    }

    private fun viewModel() = DatabaseSettingsViewModel(repository, KdfBenchmark())

    @Test
    fun nothingToSaveUntilSomethingChanges() = runTest {
        val viewModel = viewModel()

        assertFalse(viewModel.uiState.value.canSave)

        viewModel.onHistoryMaxItemsChange(-1)
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun savesTheEditedSettingsAndCloses() = runTest {
        val viewModel = viewModel()
        viewModel.onNameChange("  Work  ")
        viewModel.onRecycleBinChange(false)
        viewModel.onCipherChange(VaultCipher.CHACHA20)
        viewModel.onKdfChange(KdfAlgorithm.ARGON2ID)
        viewModel.onMemoryChange("64")

        viewModel.onSave()

        viewModel.done.first()
        assertEquals(
            DatabaseSettings("Work", false, 10, EncryptionSettings(VaultCipher.CHACHA20, KdfAlgorithm.ARGON2ID, 8, 64L * 1024 * 1024, 2)),
            repository.databaseSettings
        )
    }

    @Test
    fun switchingToAesKdfStartsFromRoundsNotPasses() = runTest {
        val viewModel = viewModel()

        viewModel.onKdfChange(KdfAlgorithm.AES_KDF)

        val state = viewModel.uiState.value
        assertEquals("600000", state.iterations)
        assertEquals(EncryptionSettings(VaultCipher.AES_256, KdfAlgorithm.AES_KDF, 600_000, 0, 0), state.edited?.encryption)
    }

    @Test
    fun invalidNumbersCannotBeSaved() = runTest {
        val viewModel = viewModel()

        viewModel.onIterationsChange("0")
        assertTrue(viewModel.uiState.value.iterationsInvalid)
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.onIterationsChange("4")
        viewModel.onParallelismChange("")
        assertTrue(viewModel.uiState.value.parallelismInvalid)
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun kdbx3FilesOnlyEditTheGeneralSettings() = runTest {
        repository.databaseSettings = DatabaseSettings("Old", true, 10, null)
        val viewModel = viewModel()

        viewModel.onNameChange("Older")
        viewModel.onSave()

        viewModel.done.first()
        assertEquals(DatabaseSettings("Older", true, 10, null), repository.databaseSettings)
    }
}
