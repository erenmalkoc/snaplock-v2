package com.erenium.snaplock.data.datasource.security

import javax.crypto.Cipher

enum class BiometricAvailability { AVAILABLE, NOT_ENROLLED, UNAVAILABLE }

class BiometricKeyMissingException : Exception("The biometric key no longer exists")

interface BiometricKeys {
    fun availability(): BiometricAvailability

    fun encryptCipher(): Cipher

    fun decryptCipher(iv: ByteArray): Cipher

    fun deleteKey()

    fun deleteLegacyKey()
}
