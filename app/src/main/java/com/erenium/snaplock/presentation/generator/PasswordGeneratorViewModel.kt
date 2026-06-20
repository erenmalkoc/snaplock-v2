package com.erenium.snaplock.presentation.generator

import androidx.lifecycle.ViewModel
import com.erenium.snaplock.data.utils.ClipboardManagerHelper
import com.erenium.snaplock.domain.model.PasswordOptions
import com.erenium.snaplock.domain.usecase.GeneratePasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class PasswordGeneratorUiState(
    val options: PasswordOptions = PasswordOptions(),
    val password: String = ""
)

@HiltViewModel
class PasswordGeneratorViewModel @Inject constructor(
    private val generatePasswordUseCase: GeneratePasswordUseCase,
    private val clipboardManager: ClipboardManagerHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(PasswordGeneratorUiState())
    val uiState = _uiState.asStateFlow()

    private val _copyEvents = Channel<Unit>(Channel.BUFFERED)
    val copyEvents = _copyEvents.receiveAsFlow()

    init {
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

    fun regenerate() {
        _uiState.update { it.copy(password = generatePasswordUseCase(it.options)) }
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
        _uiState.update { it.copy(options = candidate, password = generatePasswordUseCase(candidate)) }
    }
}
