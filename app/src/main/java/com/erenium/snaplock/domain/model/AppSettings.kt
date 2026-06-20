package com.erenium.snaplock.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val clipboardAutoClear: Boolean = true,
    val clipboardTimeoutSeconds: Int = 30,
    val autoLockSeconds: Int = 0
)
