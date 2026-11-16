package com.erenium.snaplock.presentation.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
import com.erenium.snaplock.domain.passwords.HealthReport
import com.erenium.snaplock.domain.usecase.CheckPasswordHealthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private val _failed = MutableStateFlow(false)
    val failed = _failed.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private var job: Job? = null
    private var runs = 0
    private var lastChecked: Pair<Int, Boolean>? = null

    /** Runs again when the screen returns, since the user may have fixed entries meanwhile. */
    fun refresh(force: Boolean = false) {
        job?.cancel()
        val generation = ++runs
        job = viewModelScope.launch {
            _refreshing.value = true
            try {
                val checkBreaches = settingsPrefs.settings.value.breachCheckEnabled
                val outcome = withContext(Dispatchers.Default) {
                    val entries = checkPasswordHealth.auditEntries()
                    val key = entries.hashCode() to checkBreaches
                    if (!force && key == lastChecked && _report.value != null) {
                        null
                    } else {
                        key to checkPasswordHealth(checkBreaches, entries)
                    }
                }
                if (outcome != null) {
                    lastChecked = outcome.first
                    _report.value = outcome.second
                }
                _failed.value = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _failed.value = true
            } finally {
                if (generation == runs) _refreshing.value = false
            }
        }
    }
}
