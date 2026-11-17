package com.erenium.snaplock.domain.model

import java.time.Instant

data class VaultBackup(
    val id: String,
    val created: Instant,
    val size: Long
)
