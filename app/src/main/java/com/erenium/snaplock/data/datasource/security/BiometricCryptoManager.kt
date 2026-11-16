package com.erenium.snaplock.data.datasource.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricCryptoManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) : BiometricKeys {

    private val keyStore by lazy { KeyStore.getInstance(KEYSTORE).apply { load(null) } }

    override fun availability(): BiometricAvailability =
        when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
            else -> BiometricAvailability.UNAVAILABLE
        }

    override fun encryptCipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: createKey())
        return cipher
    }

    override fun decryptCipher(iv: ByteArray): Cipher {
        val key = existingKey() ?: throw BiometricKeyMissingException()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher
    }

    override fun deleteKey() {
        runCatching { if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS) }
    }

    override fun deleteLegacyKey() {
        runCatching { if (keyStore.containsAlias(LEGACY_KEY_ALIAS)) keyStore.deleteEntry(LEGACY_KEY_ALIAS) }
    }

    private fun existingKey(): SecretKey? = keyStore.getKey(KEY_ALIAS, null) as? SecretKey

    private fun createKey(): SecretKey {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return generate(useStrongBox = false)
        return try {
            generate(useStrongBox = true)
        } catch (_: StrongBoxUnavailableException) {
            generate(useStrongBox = false)
        }
    }

    private fun generate(useStrongBox: Boolean): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(true)
            if (useStrongBox) builder.setIsStrongBoxBacked(true)
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(builder.build())
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "snaplock-biometric-key-v2"
        const val LEGACY_KEY_ALIAS = "snaplock-biometric-key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
