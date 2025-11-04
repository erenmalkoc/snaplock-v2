package com.erenium.snaplock.presentation.unlock

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.data.datasource.prefs.EncryptedPrefs
import com.erenium.snaplock.data.datasource.security.BiometricCryptoManager
import com.erenium.snaplock.domain.usecase.UnlockDatabaseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

data class UnlockUiState(
    val password: String = "",
    val useBiometrics: Boolean = false,
    val showBiometricPrompt: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isUnlockSuccessful: Boolean = false
)

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val unlockDatabaseUseCase: UnlockDatabaseUseCase,
    val cryptoManager: BiometricCryptoManager,
    val encryptedPrefs: EncryptedPrefs
) : ViewModel() {
    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState = _uiState.asStateFlow()


    init {
        if (encryptedPrefs.getEncryptedPassword() != null) {
            _uiState.update { it.copy(showBiometricPrompt = true) }
        }
    }

    fun onUseBiometricsChanged(use: Boolean) {
        _uiState.update { it.copy(useBiometrics = use) }
    }


    fun onBiometricFailed() {
        _uiState.update {
            it.copy(showBiometricPrompt = false, error = "Doğrulama başarısız.")
        }
    }

    fun onBiometricSuccess(cipher: Cipher, uri: Uri) {
        try {
            val encryptedPassword = encryptedPrefs.getEncryptedPassword()
            val decryptedPassword = cipher.doFinal(encryptedPassword).toString(Charsets.UTF_8)

            unlockWithPassword(uri, decryptedPassword)

        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Biyometrik şifre çözme hatası.") }
        }
    }

    fun onPasswordChange(newPassword: String) {
        _uiState.update {
            it.copy(password = newPassword, error = null)
        }
    }

    fun onUnlockClicked(uri: Uri) {
        val currentPassword = _uiState.value.password
        unlockWithPassword(uri, currentPassword)
    }


    private fun unlockWithPassword(uri: Uri, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = unlockDatabaseUseCase(uri, password)

            result.onSuccess {
                if (_uiState.value.useBiometrics) {
                    savePasswordWithBiometrics(password)
                }

                _uiState.update {
                    it.copy(isLoading = false, isUnlockSuccessful = true)
                }
            }
            result.onFailure {
                _uiState.update { it.copy(isLoading = false, error = "Yanlış şifre.") }
            }
        }
    }

    private fun savePasswordWithBiometrics(password: String) {
        try {
            val cipher = cryptoManager.getEncryptCipher()
            val encryptedPassword = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv

            encryptedPrefs.saveEncryptedCredentials(encryptedPassword, iv)
        } catch (e: Exception) {
            Log.e("UnlockViewModel", "Biyometrik şifre kaydı başarısız", e)
        }
    }

}