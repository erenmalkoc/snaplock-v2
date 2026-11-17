package com.erenium.snaplock.domain.model

import com.erenium.snaplock.domain.otp.OtpParameters
import java.util.UUID

data class Credential(
    val uuid: UUID,
    val title: String,
    val username: String?,
    val password: String?,
    val urls: List<String>,
    val androidApps: List<String>,
    val otp: OtpParameters? = null,
    val icon: EntryIcon? = null
)
