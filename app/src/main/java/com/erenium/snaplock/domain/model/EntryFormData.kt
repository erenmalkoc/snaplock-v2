package com.erenium.snaplock.domain.model

import java.time.Instant
import java.util.UUID

data class EntryFormData(
    val title: String,
    val username: String?,
    val password: String?,
    val url: String?,
    val notes: String?,
    val groupUuid: UUID?,
    val customFields: List<CustomField>? = null,
    val otpUri: String? = null,
    val expiry: Expiry? = null
)

data class Expiry(val at: Instant?)
