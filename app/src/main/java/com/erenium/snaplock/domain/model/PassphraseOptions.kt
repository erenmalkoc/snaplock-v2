package com.erenium.snaplock.domain.model

data class PassphraseOptions(
    val wordCount: Int = 5,
    val separator: String = "-",
    val capitalize: Boolean = true,
    val includeNumber: Boolean = true
)
