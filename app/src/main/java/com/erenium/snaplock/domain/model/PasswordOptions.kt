package com.erenium.snaplock.domain.model

data class PasswordOptions(
    val length: Int = 16,
    val useLowercase: Boolean = true,
    val useUppercase: Boolean = true,
    val useDigits: Boolean = true,
    val useSymbols: Boolean = false
) {
    val hasAnyCharSet: Boolean
        get() = useLowercase || useUppercase || useDigits || useSymbols
}
