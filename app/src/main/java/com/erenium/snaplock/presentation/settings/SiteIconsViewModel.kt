package com.erenium.snaplock.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.icons.DownloadSiteIconsUseCase
import com.erenium.snaplock.presentation.saveErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SiteIconsUiState(
    /** Entries waiting for the user to allow the download; null when not asking. */
    val confirmCount: Int? = null,
    /** Sites looked up so far and in total while downloading; null otherwise. */
    val progress: Pair<Int, Int>? = null
)

/** Runs the opt-in site icon download from the settings screen. */
@HiltViewModel
class SiteIconsViewModel @Inject constructor(
    private val downloadSiteIcons: DownloadSiteIconsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SiteIconsUiState())
    val uiState = _uiState.asStateFlow()

    private val _messages = Channel<ImportExportMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Nothing goes online yet: this only counts the entries and asks first. */
    fun onDownloadRequested() {
        if (_uiState.value.progress != null) return
        viewModelScope.launch {
            val count = downloadSiteIcons.pendingCount()
            if (count == 0) {
                _messages.send(ImportExportMessage(R.string.site_icons_none_pending))
            } else {
                _uiState.update { it.copy(confirmCount = count) }
            }
        }
    }

    fun onDismiss() = _uiState.update { it.copy(confirmCount = null) }

    fun onConfirmed() {
        _uiState.update { it.copy(confirmCount = null, progress = 0 to 0) }
        viewModelScope.launch {
            val result = downloadSiteIcons { done, total -> _uiState.update { it.copy(progress = done to total) } }
            _uiState.update { it.copy(progress = null) }
            result
                .onSuccess { added ->
                    _messages.send(
                        if (added == 0) ImportExportMessage(R.string.site_icons_none_found)
                        else ImportExportMessage(R.plurals.site_icons_done, count = added)
                    )
                }
                .onFailure { _messages.send(ImportExportMessage(it.saveErrorMessage(R.string.site_icons_error))) }
        }
    }
}
