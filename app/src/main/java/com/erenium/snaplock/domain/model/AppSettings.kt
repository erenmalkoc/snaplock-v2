package com.erenium.snaplock.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val clipboardAutoClear: Boolean = true,
    val clipboardTimeoutSeconds: Int = 30,
    val autoLockSeconds: Int = 0,
    /** Opt-in: look passwords up in Have I Been Pwned (k-anonymity, hash prefix only). */
    val breachCheckEnabled: Boolean = false,
    /** Opt-in: fetch icons for entries that have a website but no icon, as they appear. */
    val autoSiteIcons: Boolean = false
)
