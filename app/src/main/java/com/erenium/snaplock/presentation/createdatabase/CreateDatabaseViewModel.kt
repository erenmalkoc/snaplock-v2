package com.erenium.snaplock.presentation.createdatabase

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.usecase.CreateDatabaseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateDatabaseUiState(
    val password: String = "",
    val confirmPassword: String = "",
    val isCreating: Boolean = false,
    val errorStringId: Int? = null
) {
    val passwordsMatch: Boolean get() = password == confirmPassword
    val canSubmit: Boolean get() = password.isNotBlank() && passwordsMatch && !isCreating
}

@HiltViewModel
class CreateDatabaseViewModel @Inject constructor(
    private val createDatabaseUseCase: CreateDatabaseUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateDatabaseUiState())
    val uiState = _uiState.asStateFlow()

    private val _createdEvents = Channel<Unit>(Channel.BUFFERED)
    val createdEvents = _createdEvents.receiveAsFlow()

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorStringId = null) }

    fun onConfirmPasswordChange(value: String) =
        _uiState.update { it.copy(confirmPassword = value, errorStringId = null) }

    fun onCreate(uri: Uri) {
        val state = _uiState.value
        if (!state.canSubmit) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, errorStringId = null) }
            createDatabaseUseCase(uri, state.password)
                .onSuccess { _createdEvents.trySend(Unit) }
                .onFailure {
                    _uiState.update { it.copy(isCreating = false, errorStringId = R.string.create_db_error) }
                }
        }
    }
}
