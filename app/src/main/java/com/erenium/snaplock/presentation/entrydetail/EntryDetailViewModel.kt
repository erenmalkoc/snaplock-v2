package com.erenium.snaplock.presentation.entrydetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.data.utils.ClipboardManagerHelper
import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.usecase.GetEntryDetailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class EntryDetailUiState(
    val entry: EntryDetail? = null,
    val isLoading: Boolean = true,
    val errorStringId: Int? = null,
    val isPasswordVisible: Boolean = false
)

@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getEntryDetailsUseCase: GetEntryDetailsUseCase,
    private val clipboardManager: ClipboardManagerHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntryDetailUiState())
    val uiState = _uiState.asStateFlow()

    private val entryUuid: String = checkNotNull(savedStateHandle["uuid"])

    init {
        loadEntryDetails()
    }

    private fun loadEntryDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val result = getEntryDetailsUseCase(UUID.fromString(entryUuid))

            result.onSuccess { entry ->
                _uiState.update { it.copy(isLoading = false, entry = entry) }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false, errorStringId = R.string.entry_load_error) }
            }
        }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun onCopyPassword(label: String) {
        val password = _uiState.value.entry?.password
        if (password.isNullOrEmpty()) return
        clipboardManager.copyToClipboard(label = label, text = password)
    }
}
