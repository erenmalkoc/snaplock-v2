package com.erenium.snaplock.domain.model

import com.erenium.snaplock.domain.otp.OtpParameters
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
    /** Previous versions, newest first. */
    val history: List<EntryVersion> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    /** Two-factor code settings, whichever KeePass field style they are stored in. */
    val otp: OtpParameters? = null
)
