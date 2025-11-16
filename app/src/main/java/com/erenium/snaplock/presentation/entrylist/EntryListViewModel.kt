package com.erenium.snaplock.presentation.entrylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.usecase.GetEntriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class EntryListUiState(
    val entries: List<Entry> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class EntryListViewModel @Inject constructor(
    getEntriesUseCase: GetEntriesUseCase
) : ViewModel() {

    val uiState : StateFlow<List<Entry>> = getEntriesUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )



}