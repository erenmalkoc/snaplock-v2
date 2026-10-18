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
    /** Replaces the entry's custom fields; null leaves them as they are. */
    val customFields: List<CustomField>? = null,
    /**
     * New two-factor settings as an otpauth:// URI; "" removes them and null leaves the
     * existing fields untouched, in whatever format another client wrote them.
     */
    val otpUri: String? = null,
    /** New expiry; null leaves it unchanged. */
    val expiry: Expiry? = null
)

/** An expiry date to set, or [at] = null to make the entry never expire. */
data class Expiry(val at: Instant?)
