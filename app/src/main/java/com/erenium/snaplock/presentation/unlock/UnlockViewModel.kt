package com.erenium.snaplock.presentation.unlock

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.domain.usecase.UnlockDatabaseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UnlockUiState(
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isUnlockSuccessful: Boolean = false
)

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val unlockDatabaseUseCase: UnlockDatabaseUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState = _uiState.asStateFlow()

    fun onPasswordChange(newPassword: String) {
        _uiState.update {
            it.copy(password = newPassword, error = null)
        }
    }

    fun onUnlockClicked(uri: Uri) {
        val currentPassword = _uiState.value.password

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null)}

           val result = unlockDatabaseUseCase(uri, currentPassword)

            result.onSuccess {
                _uiState.update {
                    it.copy(isLoading = false, isUnlockSuccessful = true)
                }
            }
            result.onFailure {
                _uiState.update{
                    it.copy(isLoading = false, error = "Yanlış şifre veya bozuk dosya.")
                }
            }
        }
    }

}