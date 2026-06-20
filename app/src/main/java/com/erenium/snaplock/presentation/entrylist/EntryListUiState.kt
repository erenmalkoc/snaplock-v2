package com.erenium.snaplock.presentation.entrylist

import com.erenium.snaplock.domain.model.Entry

data class EntryListUiState(
    val entries: List<Entry> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true
) {
    val isEmpty: Boolean get() = entries.isEmpty()
    val isSearching: Boolean get() = query.isNotBlank()
}
