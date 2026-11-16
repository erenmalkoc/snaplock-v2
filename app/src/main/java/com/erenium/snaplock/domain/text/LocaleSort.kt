package com.erenium.snaplock.domain.text

import java.text.Collator
import java.util.Locale

fun localeComparator(locale: Locale = Locale.getDefault()): Comparator<String> {
    val collator = Collator.getInstance(locale)
    return Comparator { first, second -> collator.compare(first, second) }
}
