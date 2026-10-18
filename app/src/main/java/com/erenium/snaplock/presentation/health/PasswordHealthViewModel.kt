package com.erenium.snaplock.presentation.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
import com.erenium.snaplock.domain.passwords.HealthReport
import com.erenium.snaplock.domain.usecase.CheckPasswordHealthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PasswordHealthViewModel @Inject constructor(
    private val checkPasswordHealth: CheckPasswordHealthUseCase,
    private val settingsPrefs: SettingsPrefs
) : ViewModel() {

    /** Null while the check runs. */
    private val _report = MutableStateFlow<HealthReport?>(null)
    val report = _report.asStateFlow()

    /** Runs again when the screen returns, since the user may have fixed entries meanwhile. */
    fun refresh() {
        viewModelScope.launch {
            // Strength estimates for large vaults are worth keeping off the main thread.
            val checkBreaches = settingsPrefs.settings.value.breachCheckEnabled
            _report.value = withContext(Dispatchers.Default) { checkPasswordHealth(checkBreaches) }
        }
    }
}
