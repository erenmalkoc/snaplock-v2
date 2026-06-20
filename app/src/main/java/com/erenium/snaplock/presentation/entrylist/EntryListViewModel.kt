package com.erenium.snaplock.presentation.entrylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.domain.usecase.GetEntriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class EntryListViewModel @Inject constructor(
    getEntriesUseCase: GetEntriesUseCase
) : ViewModel() {

    private val _query = MutableStateFlow("")

    val uiState: StateFlow<EntryListUiState> =
        combine(getEntriesUseCase(), _query) { entries, query ->
            val filtered = if (query.isBlank()) {
                entries
            } else {
                entries.filter { entry ->
                    entry.title.contains(query, ignoreCase = true) ||
                        entry.username?.contains(query, ignoreCase = true) == true
                }
            }
            EntryListUiState(entries = filtered, query = query, isLoading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = EntryListUiState()
        )

    fun onQueryChange(query: String) {
        _query.value = query
    }
}
