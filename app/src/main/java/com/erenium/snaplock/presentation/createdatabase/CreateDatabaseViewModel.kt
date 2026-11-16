package com.erenium.snaplock.presentation.createdatabase

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.local.DocumentFiles
import com.erenium.snaplock.domain.passwords.PasswordStrengthEstimator
import com.erenium.snaplock.domain.passwords.StrengthEstimate
import com.erenium.snaplock.domain.usecase.CreateDatabaseUseCase
import com.erenium.snaplock.presentation.main.AppMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject

const val SHORT_PASSWORD_LENGTH = 8

data class CreateDatabaseUiState(
    val password: String = "",
    val confirmPassword: String = "",
    val confirmTouched: Boolean = false,
    val strength: StrengthEstimate? = null,
    val isCreating: Boolean = false,
    @param:StringRes val errorStringId: Int? = null
) {
    val passwordsMatch: Boolean get() = password == confirmPassword
    val showMismatch: Boolean get() = confirmTouched && !passwordsMatch
    val isShort: Boolean get() = password.isNotEmpty() && password.length < SHORT_PASSWORD_LENGTH
    val canSubmit: Boolean get() = password.isNotEmpty() && confirmPassword.isNotEmpty() && !isCreating
}

@StringRes
fun Throwable.createErrorMessage(): Int = when {
    this is SecurityException -> R.string.create_db_read_only
    this is FileNotFoundException && message.orEmpty().let {
        it.contains("EROFS", true) || it.contains("read-only", true) ||
            it.contains("EACCES", true) || it.contains("permission denied", true)
    } -> R.string.create_db_read_only
    this is IOException -> R.string.create_db_write_error
    else -> R.string.create_db_error
}

@HiltViewModel
class CreateDatabaseViewModel @Inject constructor(
    private val createDatabaseUseCase: CreateDatabaseUseCase,
    private val documentFiles: DocumentFiles,
    private val strengthEstimator: PasswordStrengthEstimator,
    private val appMessages: AppMessages
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateDatabaseUiState())
    val uiState = _uiState.asStateFlow()

    private val _createdEvents = Channel<Unit>(Channel.BUFFERED)
    val createdEvents = _createdEvents.receiveAsFlow()

    fun onPasswordChange(value: String) = _uiState.update {
        it.copy(
            password = value,
            strength = value.takeIf { password -> password.isNotEmpty() }?.let(strengthEstimator::estimate),
            errorStringId = null
        )
    }

    fun onConfirmPasswordChange(value: String) =
        _uiState.update { it.copy(confirmPassword = value, errorStringId = null) }

    fun onConfirmFocusLost() {
        if (_uiState.value.confirmPassword.isNotEmpty()) _uiState.update { it.copy(confirmTouched = true) }
    }

    /** Whether the location picker may open now. A mismatch is reported here, once the user commits. */
    fun onSubmit(): Boolean {
        val state = _uiState.value
        if (!state.canSubmit) return false
        if (!state.passwordsMatch) {
            _uiState.update { it.copy(confirmTouched = true) }
            return false
        }
        return true
    }

    fun onCreate(uri: Uri) {
        val state = _uiState.value
        if (!state.canSubmit || !state.passwordsMatch) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, errorStringId = null) }
            createDatabaseUseCase(uri, state.password)
                .onSuccess {
                    appMessages.post(R.string.create_db_saved, documentFiles.displayName(uri))
                    _createdEvents.trySend(Unit)
                }
                .onFailure { failure ->
                    documentFiles.delete(uri)
                    _uiState.update { it.copy(isCreating = false, errorStringId = failure.createErrorMessage()) }
                }
        }
    }
}
