package com.erenium.snaplock.domain.model

data class PassphraseOptions(
    val wordCount: Int = 5,
    val separator: String = "-",
    val capitalize: Boolean = true,
    /** Adds one digit to a random word, for sites that insist on a number. */
    val includeNumber: Boolean = true
)
