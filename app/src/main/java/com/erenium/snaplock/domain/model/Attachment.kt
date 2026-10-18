package com.erenium.snaplock.domain.model

/** A file stored inside the vault with an entry. */
data class Attachment(
    val name: String,
    val sizeBytes: Long
)
