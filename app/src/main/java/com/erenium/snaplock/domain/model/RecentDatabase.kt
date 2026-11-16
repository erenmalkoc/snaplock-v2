package com.erenium.snaplock.domain.model

import android.net.Uri

data class RecentDatabase(
    val uri: Uri,
    val name: String,
    val available: Boolean = true,
    val location: String? = null,
    val lastOpened: Long? = null,
    val readOnly: Boolean = false
)
