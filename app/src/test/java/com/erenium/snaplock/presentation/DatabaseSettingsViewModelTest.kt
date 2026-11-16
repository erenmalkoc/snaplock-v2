package com.erenium.snaplock.presentation

import com.erenium.snaplock.R
import com.erenium.snaplock.data.crypto.KdfBenchmark
import com.erenium.snaplock.domain.model.DatabaseSettings
import com.erenium.snaplock.domain.model.EncryptionSettings
import com.erenium.snaplock.domain.model.KdfAlgorithm
import com.erenium.snaplock.domain.model.VaultCipher
import com.erenium.snaplock.presentation.databasesettings.BenchmarkLowering
import com.erenium.snaplock.presentation.databasesettings.DatabaseSettingsViewModel
import com.erenium.snaplock.presentation.databasesettings.benchmarkOutcome
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun valuesFarAboveSaneLimitsCannotBeSaved() = runTest {
        val viewModel = viewModel()

        viewModel.onIterationsChange("100000")
        assertTrue(viewModel.uiState.value.iterationsInvalid)
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.onIterationsChange("8")
        viewModel.onMemoryChange("4096")
        assertTrue(viewModel.uiState.value.memoryInvalid)
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.onMemoryChange("512")
        assertFalse(viewModel.uiState.value.memoryInvalid)
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun aVaultAlreadyAboveTheLimitsCanStillSaveOtherChanges() = runTest {
        repository.databaseSettings = DatabaseSettings(
            "Family", true, 10, EncryptionSettings(VaultCipher.AES_256, KdfAlgorithm.ARGON2D, 2000, 2048L * 1024 * 1024, 2)
        )
        val viewModel = viewModel()

        viewModel.onNameChange("Work")

        assertFalse(viewModel.uiState.value.iterationsInvalid)
        assertFalse(viewModel.uiState.value.memoryInvalid)
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
        assertTrue(viewModel.uiState.value.confirmEncryptionSave)
        viewModel.onEncryptionSaveConfirmed()

        assertEquals(R.string.database_settings_saved, viewModel.messages.first())
        assertEquals(
            DatabaseSettings("Work", false, 10, EncryptionSettings(VaultCipher.CHACHA20, KdfAlgorithm.ARGON2ID, 8, 64L * 1024 * 1024, 2)),
            repository.databaseSettings
        )
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        assertFalse(viewModel.uiState.value.canSave)
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

        assertEquals(R.string.database_settings_saved, viewModel.messages.first())
        assertEquals(DatabaseSettings("Older", true, 10, null), repository.databaseSettings)
    }

    @Test
    fun generalChangesSaveWithoutAskingAboutEncryption() = runTest {
        val viewModel = viewModel()
        viewModel.onNameChange("Work")

        viewModel.onSave()

        assertFalse(viewModel.uiState.value.confirmEncryptionSave)
        assertEquals(R.string.database_settings_saved, viewModel.messages.first())
    }

    @Test
    fun encryptionChangesAskBeforeSavingAndCanBeDeclined() = runTest {
        val viewModel = viewModel()
        viewModel.onIterationsChange("9")

        viewModel.onSave()
        assertTrue(viewModel.uiState.value.confirmEncryptionSave)
        assertEquals(argon2, repository.databaseSettings?.encryption)

        viewModel.onEncryptionSaveDismissed()
        assertFalse(viewModel.uiState.value.confirmEncryptionSave)
        assertEquals(argon2, repository.databaseSettings?.encryption)
    }

    @Test
    fun unsavedEditsAreDetectedAndUndoneEditsAreNot() = runTest {
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        viewModel.onIterationsChange("")
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.onIterationsChange("8")
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun warningsAppearWhenTheRecycleBinOrHistoryIsOff() = runTest {
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.value.showRecycleBinWarning)
        assertFalse(viewModel.uiState.value.showHistoryWarning)

        viewModel.onRecycleBinChange(false)
        viewModel.onHistoryMaxItemsChange(0)

        assertTrue(viewModel.uiState.value.showRecycleBinWarning)
        assertTrue(viewModel.uiState.value.showHistoryWarning)
    }

    @Test
    fun missingSettingsShowAnErrorThatRetryCanClear() = runTest {
        repository.databaseSettings = null
        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.loadFailed)
        assertFalse(viewModel.uiState.value.isLoading)

        repository.databaseSettings = DatabaseSettings("Family", true, 10, argon2)
        viewModel.onRetry()

        assertFalse(viewModel.uiState.value.loadFailed)
        assertEquals("Family", viewModel.uiState.value.name)
    }

    @Test
    fun benchmarkThatRaisesIterationsIsAppliedAndRemembered() = runTest {
        val viewModel = viewModel()

        viewModel.onBenchmarkResult(20)

        val state = viewModel.uiState.value
        assertEquals("20", state.iterations)
        assertEquals(20L, state.benchmarkIterations)
        assertNull(state.lowering)
        assertEquals(1, state.estimatedUnlockSeconds)
    }

    @Test
    fun benchmarkThatLowersIterationsWaitsForConfirmation() = runTest {
        val viewModel = viewModel()
        viewModel.onIterationsChange("40")

        viewModel.onBenchmarkResult(10)
        assertEquals("40", viewModel.uiState.value.iterations)
        assertEquals(BenchmarkLowering(10, 40), viewModel.uiState.value.lowering)

        viewModel.onLoweringDismissed()
        assertEquals("40", viewModel.uiState.value.iterations)

        viewModel.onBenchmarkResult(10)
        viewModel.onLoweringConfirmed()
        assertEquals("10", viewModel.uiState.value.iterations)
        assertNull(viewModel.uiState.value.lowering)
    }

    @Test
    fun benchmarkOutcomeCapsAndComparesWithTheCurrentValue() {
        assertEquals(5L to null, benchmarkOutcome(current = null, measured = 5, max = 1000))
        assertEquals(1000L to null, benchmarkOutcome(current = 8, measured = 5000, max = 1000))
        assertEquals(8L to BenchmarkLowering(3, 8), benchmarkOutcome(current = 8, measured = 3, max = 1000))
    }

    @Test
    fun estimatedUnlockTimeFollowsTheEnteredIterations() = runTest {
        val viewModel = viewModel()
        viewModel.onBenchmarkResult(10)
        viewModel.onIterationsChange("30")

        assertEquals(3, viewModel.uiState.value.estimatedUnlockSeconds)
    }
}
