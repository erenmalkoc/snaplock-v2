package com.erenium.snaplock.presentation.entrylist

import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.model.Group
import java.util.UUID

data class EntryListUiState(
    val entries: List<Entry> = emptyList(),
    val groups: List<Group> = emptyList(),
    val query: String = "",
    val selectedGroupUuid: UUID? = null,
    val isLoading: Boolean = true
) {
    val isEmpty: Boolean get() = entries.isEmpty()
    val isSearching: Boolean get() = query.isNotBlank()
    val isFiltering: Boolean get() = selectedGroupUuid != null
    val showGroupFilter: Boolean get() = groups.size > 1
}
