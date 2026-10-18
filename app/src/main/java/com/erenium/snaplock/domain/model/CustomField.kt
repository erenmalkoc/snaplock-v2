package com.erenium.snaplock.domain.model

/** An extra entry field beyond title, username, password, URL and notes. */
data class CustomField(
    val name: String,
    val value: String,
    /** Stored encrypted in memory and hidden by default, like the password. */
    val isProtected: Boolean
)
