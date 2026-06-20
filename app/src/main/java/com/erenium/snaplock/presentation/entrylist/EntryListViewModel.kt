package com.erenium.snaplock.presentation.entrylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.domain.usecase.GetEntriesUseCase
import com.erenium.snaplock.domain.usecase.GetGroupsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EntryListViewModel @Inject constructor(
    getEntriesUseCase: GetEntriesUseCase,
    getGroupsUseCase: GetGroupsUseCase
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _selectedGroupUuid = MutableStateFlow<UUID?>(null)

    val uiState: StateFlow<EntryListUiState> =
        combine(
            getEntriesUseCase(),
            getGroupsUseCase(),
            _query,
            _selectedGroupUuid
        ) { entries, groups, query, selectedGroup ->
            val byGroup = if (selectedGroup == null) {
                entries
            } else {
                entries.filter { it.groupUuid == selectedGroup }
            }
            val filtered = if (query.isBlank()) {
                byGroup
            } else {
                byGroup.filter { entry ->
                    entry.title.contains(query, ignoreCase = true) ||
                        entry.username?.contains(query, ignoreCase = true) == true
                }
            }
            EntryListUiState(
                entries = filtered,
                groups = groups,
                query = query,
                selectedGroupUuid = selectedGroup,
                isLoading = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = EntryListUiState()
        )

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun onGroupSelected(groupUuid: UUID?) {
        _selectedGroupUuid.value = groupUuid
    }
}
