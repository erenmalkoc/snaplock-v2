package com.erenium.snaplock.domain.model

import java.time.Instant

/** A copy of the open vault file as it was before a save, kept on this device. */
data class VaultBackup(
    val id: String,
    val created: Instant,
    val size: Long
)
