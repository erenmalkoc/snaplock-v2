package com.erenium.snaplock.domain.model

import com.erenium.snaplock.domain.otp.OtpParameters
import java.util.UUID

/** An entry's login data, as needed to fill it into other apps and websites. */
data class Credential(
    val uuid: UUID,
    val title: String,
    val username: String?,
    val password: String?,
    /** Web addresses: the URL field plus the extra URL fields KeePassXC and Keepass2Android add. */
    val urls: List<String>,
    /** Android package names linked to the entry. */
    val androidApps: List<String>,
    val otp: OtpParameters? = null
)
