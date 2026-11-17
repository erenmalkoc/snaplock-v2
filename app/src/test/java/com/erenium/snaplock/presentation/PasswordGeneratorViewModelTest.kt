package com.erenium.snaplock.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
import com.erenium.snaplock.data.utils.ClipboardManagerHelper
import com.erenium.snaplock.domain.model.PasswordOptions
import com.erenium.snaplock.domain.passwords.PasswordStrengthEstimator
import com.erenium.snaplock.domain.passwords.WordList
import com.erenium.snaplock.domain.usecase.GeneratePassphraseUseCase
import com.erenium.snaplock.domain.usecase.GeneratePasswordUseCase
import com.erenium.snaplock.presentation.generator.GeneratorMode
import com.erenium.snaplock.presentation.generator.PasswordGeneratorViewModel
import com.erenium.snaplock.presentation.generator.isLastActiveSet
import com.erenium.snaplock.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PasswordGeneratorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = SettingsPrefs(context)
    private val words = object : WordList {
        override val words = List(200) { "word$it" }
    }

    private fun viewModel(): PasswordGeneratorViewModel = PasswordGeneratorViewModel(
        GeneratePasswordUseCase(),
        GeneratePassphraseUseCase(words),
        PasswordStrengthEstimator(words),
        ClipboardManagerHelper(context, prefs),
        prefs
    )

    @Test
    fun startsWithTheOriginalDefaultsOnAFreshInstall() {
        val state = viewModel().uiState.value

        assertEquals(GeneratorMode.PASSWORD, state.mode)
        assertEquals(PasswordOptions(), state.options)
    }

    @Test
    fun lastUsedOptionsComeBackInANewViewModel() {
        viewModel().apply {
            onModeChange(GeneratorMode.PASSPHRASE)
            onWordCountChange(9)
            onWordCountChangeFinished()
            onSeparatorChange(".")
            onModeChange(GeneratorMode.PASSWORD)
            onLengthChange(33)
            onLengthChangeFinished()
            onSymbolsChange(true)
            onDigitsChange(false)
        }

        val state = viewModel().uiState.value

        assertEquals(GeneratorMode.PASSWORD, state.mode)
        assertEquals(PasswordOptions(length = 33, useDigits = false, useSymbols = true), state.options)
        assertEquals(9, state.passphraseOptions.wordCount)
        assertEquals(".", state.passphraseOptions.separator)
    }

    @Test
    fun theLastActiveCharacterSetCannotBeSwitchedOff() {
        val viewModel = viewModel()
        viewModel.onUppercaseChange(false)
        viewModel.onDigitsChange(false)
        assertTrue(viewModel.uiState.value.options.isLastActiveSet(viewModel.uiState.value.options.useLowercase))

        viewModel.onLowercaseChange(false)

        val options = viewModel.uiState.value.options
        assertTrue(options.useLowercase)
        assertTrue(options.hasAnyCharSet)
        assertEquals(options.length, viewModel.uiState.value.password.length)
    }

    @Test
    fun onlyTheSingleActiveSetIsMarkedAsTheLastOne() {
        val options = PasswordOptions(useLowercase = true, useUppercase = true, useDigits = false, useSymbols = false)

        assertFalse(options.isLastActiveSet(options.useLowercase))
        assertFalse(options.isLastActiveSet(options.useDigits))
    }

    @Test
    fun draggingTheSliderOnlyRegeneratesWhenItIsReleased() {
        val viewModel = viewModel()
        val before = viewModel.uiState.value.password

        viewModel.onLengthChange(30)
        viewModel.onLengthChange(31)

        assertEquals(before, viewModel.uiState.value.password)
        assertEquals(31, viewModel.uiState.value.options.length)

        viewModel.onLengthChangeFinished()

        assertEquals(31, viewModel.uiState.value.password.length)
        assertNotEquals(before, viewModel.uiState.value.password)
    }

    @Test
    fun storedValuesOutsideTheLimitsAreBroughtBack() {
        context.getSharedPreferences("snaplock_settings", Context.MODE_PRIVATE)
            .edit().putInt("gen_length", 500).putInt("gen_words", 1).commit()
        val freshPrefs = SettingsPrefs(context)
        val viewModel = PasswordGeneratorViewModel(
            GeneratePasswordUseCase(),
            GeneratePassphraseUseCase(words),
            PasswordStrengthEstimator(words),
            ClipboardManagerHelper(context, freshPrefs),
            freshPrefs
        )

        assertEquals(GeneratePasswordUseCase.MAX_LENGTH, viewModel.uiState.value.options.length)
        assertEquals(GeneratePassphraseUseCase.MIN_WORDS, viewModel.uiState.value.passphraseOptions.wordCount)
    }
}
