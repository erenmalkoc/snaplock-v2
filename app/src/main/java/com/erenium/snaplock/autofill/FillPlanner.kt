package com.erenium.snaplock.autofill

import com.erenium.snaplock.domain.model.Credential

sealed interface FillItem {
    data object Unlock : FillItem

    data class Fill(val credential: Credential) : FillItem

    data object Search : FillItem
}

object FillPlanner {
    const val MAX_DIRECT_MATCHES = 5

    fun plan(isLocked: Boolean, matches: List<Credential>, maxSuggestions: Int? = null): List<FillItem> {
        if (isLocked) return listOf(FillItem.Unlock)
        val directSlots = minOf(MAX_DIRECT_MATCHES, (maxSuggestions ?: Int.MAX_VALUE) - 1).coerceAtLeast(0)
        return matches.take(directSlots).map(FillItem::Fill) + FillItem.Search
    }
}
