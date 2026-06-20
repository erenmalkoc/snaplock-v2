package com.erenium.snaplock.presentation.entryform

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.model.EntryFormData
import com.erenium.snaplock.domain.usecase.AddEntryUseCase
import com.erenium.snaplock.domain.usecase.GetEntryDetailsUseCase
import com.erenium.snaplock.domain.usecase.UpdateEntryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class EntryFormUiState(
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorStringId: Int? = null
) {
    val canSave: Boolean get() = title.isNotBlank() && !isSaving && !isLoading
}

@HiltViewModel
class EntryFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getEntryDetailsUseCase: GetEntryDetailsUseCase,
    private val addEntryUseCase: AddEntryUseCase,
    private val updateEntryUseCase: UpdateEntryUseCase
) : ViewModel() {

    private val entryUuid: UUID? = savedStateHandle.get<String>("uuid")?.let(UUID::fromString)

    private val _uiState = MutableStateFlow(
        EntryFormUiState(isEditMode = entryUuid != null, isLoading = entryUuid != null)
    )
    val uiState = _uiState.asStateFlow()

    private val _savedEvents = Channel<Unit>(Channel.BUFFERED)
    val savedEvents = _savedEvents.receiveAsFlow()

    init {
        if (entryUuid != null) {
            loadEntry(entryUuid)
        }
    }

    private fun loadEntry(uuid: UUID) {
        viewModelScope.launch {
            getEntryDetailsUseCase(uuid)
                .onSuccess { entry ->
                    _uiState.update {
                        it.copy(
                            title = entry.title,
                            username = entry.username.orEmpty(),
                            password = entry.password.orEmpty(),
                            url = entry.url.orEmpty(),
                            notes = entry.notes.orEmpty(),
                            isLoading = false
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, errorStringId = R.string.entry_load_error) }
                }
        }
    }

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value, errorStringId = null) }
    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value) }
    fun onUrlChange(value: String) = _uiState.update { it.copy(url = value) }
    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value) }

    fun onSave() {
        val state = _uiState.value
        if (!state.canSave) return

        val data = EntryFormData(
            title = state.title.trim(),
            username = state.username.trim().ifBlank { null },
            password = state.password.ifBlank { null },
            url = state.url.trim().ifBlank { null },
            notes = state.notes.ifBlank { null }
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorStringId = null) }

            val result = if (entryUuid != null) {
                updateEntryUseCase(entryUuid, data)
            } else {
                addEntryUseCase(data)
            }

            result
                .onSuccess { _savedEvents.trySend(Unit) }
                .onFailure {
                    _uiState.update { it.copy(isSaving = false, errorStringId = R.string.entry_save_error) }
                }
        }
    }
}
