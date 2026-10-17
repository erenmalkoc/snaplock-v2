package com.erenium.snaplock.autofill

import com.erenium.snaplock.domain.model.Credential

/** One suggestion in the autofill dropdown or keyboard strip. */
sealed interface FillItem {
    /** The vault is locked: opens Snaplock to unlock, then fills. */
    data object Unlock : FillItem

    /** Fills this entry right away. */
    data class Fill(val credential: Credential) : FillItem

    /** Opens Snaplock to pick any entry. */
    data object Search : FillItem
}

object FillPlanner {

    const val MAX_DIRECT_MATCHES = 5

    /**
     * @param maxSuggestions how many items the keyboard can show inline, if it asked; the
     * search item always takes the last slot so every entry stays reachable.
     */
    fun plan(isLocked: Boolean, matches: List<Credential>, maxSuggestions: Int? = null): List<FillItem> {
        if (isLocked) return listOf(FillItem.Unlock)
        val directSlots = minOf(MAX_DIRECT_MATCHES, (maxSuggestions ?: Int.MAX_VALUE) - 1).coerceAtLeast(0)
        return matches.take(directSlots).map(FillItem::Fill) + FillItem.Search
    }
}
