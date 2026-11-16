package com.erenium.snaplock.testing

import android.security.keystore.KeyPermanentlyInvalidatedException
import com.erenium.snaplock.data.datasource.security.BiometricAvailability
import com.erenium.snaplock.data.datasource.security.BiometricKeyMissingException
import com.erenium.snaplock.data.datasource.security.BiometricKeys
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Software AES-GCM key standing in for the Android Keystore key. */
class FakeBiometricKeys : BiometricKeys {
    var availability = BiometricAvailability.AVAILABLE
    var invalidated = false
    var legacyKeyDeleted = false
    var deletedKeys = 0

    private var key: SecretKey? = null

    override fun availability(): BiometricAvailability = availability

    override fun encryptCipher(): Cipher {
        if (invalidated) throw KeyPermanentlyInvalidatedException()
        val secret = key ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret) }
    }

    override fun decryptCipher(iv: ByteArray): Cipher {
        if (invalidated) throw KeyPermanentlyInvalidatedException()
        val secret = key ?: throw BiometricKeyMissingException()
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, iv)) }
    }

    override fun deleteKey() {
        key = null
        invalidated = false
        deletedKeys++
    }

    override fun deleteLegacyKey() {
        legacyKeyDeleted = true
    }
}
