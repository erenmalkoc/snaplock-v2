package com.erenium.snaplock.presentation.generator

import androidx.lifecycle.ViewModel
import com.erenium.snaplock.data.utils.ClipboardManagerHelper
import com.erenium.snaplock.domain.model.PassphraseOptions
import com.erenium.snaplock.domain.model.PasswordOptions
import com.erenium.snaplock.domain.passwords.PasswordStrengthEstimator
import com.erenium.snaplock.domain.passwords.StrengthEstimate
import com.erenium.snaplock.domain.usecase.GeneratePassphraseUseCase
import com.erenium.snaplock.domain.usecase.GeneratePasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class GeneratorMode { PASSWORD, PASSPHRASE }

data class PasswordGeneratorUiState(
    val mode: GeneratorMode = GeneratorMode.PASSWORD,
    val options: PasswordOptions = PasswordOptions(),
    val passphraseOptions: PassphraseOptions = PassphraseOptions(),
    val password: String = "",
    val strength: StrengthEstimate? = null
)

@HiltViewModel
class PasswordGeneratorViewModel @Inject constructor(
    private val generatePasswordUseCase: GeneratePasswordUseCase,
    private val generatePassphraseUseCase: GeneratePassphraseUseCase,
    private val strengthEstimator: PasswordStrengthEstimator,
    private val clipboardManager: ClipboardManagerHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(PasswordGeneratorUiState())
    val uiState = _uiState.asStateFlow()

    private val _copyEvents = Channel<Unit>(Channel.BUFFERED)
    val copyEvents = _copyEvents.receiveAsFlow()

    init {
        regenerate()
    }

    fun onModeChange(mode: GeneratorMode) {
        _uiState.update { it.copy(mode = mode) }
        regenerate()
    }

    fun onLengthChange(length: Int) {
        updateOptions { it.copy(length = length) }
    }

    fun onLowercaseChange(enabled: Boolean) {
        updateOptions { it.copy(useLowercase = enabled) }
    }

    fun onUppercaseChange(enabled: Boolean) {
        updateOptions { it.copy(useUppercase = enabled) }
    }

    fun onDigitsChange(enabled: Boolean) {
        updateOptions { it.copy(useDigits = enabled) }
    }

    fun onSymbolsChange(enabled: Boolean) {
        updateOptions { it.copy(useSymbols = enabled) }
    }

    fun onWordCountChange(count: Int) = updatePassphrase { it.copy(wordCount = count) }

    fun onSeparatorChange(separator: String) = updatePassphrase { it.copy(separator = separator) }

    fun onCapitalizeChange(enabled: Boolean) = updatePassphrase { it.copy(capitalize = enabled) }

    fun onIncludeNumberChange(enabled: Boolean) = updatePassphrase { it.copy(includeNumber = enabled) }

    fun regenerate() {
        _uiState.update { state ->
            val generated = when (state.mode) {
                GeneratorMode.PASSWORD -> generatePasswordUseCase(state.options)
                GeneratorMode.PASSPHRASE -> generatePassphraseUseCase(state.passphraseOptions)
            }
            state.copy(password = generated, strength = strengthEstimator.estimate(generated))
        }
    }

    fun onCopy(label: String) {
        val password = _uiState.value.password
        if (password.isEmpty()) return
        clipboardManager.copyToClipboard(label = label, text = password)
        _copyEvents.trySend(Unit)
    }

    private fun updateOptions(transform: (PasswordOptions) -> PasswordOptions) {
        val candidate = transform(_uiState.value.options)
        if (!candidate.hasAnyCharSet) return
        _uiState.update { it.copy(options = candidate) }
        regenerate()
    }

    private fun updatePassphrase(transform: (PassphraseOptions) -> PassphraseOptions) {
        _uiState.update { it.copy(passphraseOptions = transform(it.passphraseOptions)) }
        regenerate()
    }
}
