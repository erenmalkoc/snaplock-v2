package com.erenium.snaplock.domain.model

import com.erenium.snaplock.domain.otp.OtpParameters
import com.erenium.snaplock.domain.passkeys.Passkey
import java.time.Instant
import java.util.UUID

data class EntryDetail(
    val uuid: UUID,
    val title: String,
    val username: String?,
    val password: String?,
    val url: String?,
    val notes: String?,
    val groupUuid: UUID?,
    val customFields: List<CustomField> = emptyList(),
    val history: List<EntryVersion> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val otp: OtpParameters? = null,
    val expires: Instant? = null,
    val passkey: Passkey? = null
)
