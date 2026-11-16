package com.erenium.snaplock.data.datasource.security

import android.net.Uri
import com.erenium.snaplock.data.datasource.prefs.EncryptedPrefs
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.InvalidKeyException
import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CipherResult {
    class Ready(val cipher: Cipher) : CipherResult
    data object Invalidated : CipherResult
    data object Failed : CipherResult
}

@Singleton
class BiometricVault @Inject constructor(
    private val keys: BiometricKeys,
    private val prefs: EncryptedPrefs
) {

    fun availability(): BiometricAvailability = runCatching { keys.availability() }
        .getOrDefault(BiometricAvailability.UNAVAILABLE)

    fun isEnabled(database: Uri): Boolean = prefs.has(database)

    fun migrateLegacy() {
        runCatching {
            prefs.dropLegacy()
            keys.deleteLegacyKey()
        }
    }

    fun enrollCipher(): CipherResult {
        repeat(2) {
            try {
                return CipherResult.Ready(keys.encryptCipher())
            } catch (e: Exception) {
                if (!e.invalidatesKey()) return CipherResult.Failed
                resetKeys()
            }
        }
        return CipherResult.Failed
    }

    fun unlockCipher(database: Uri): CipherResult {
        val iv = prefs.getIv(database)
        if (iv == null || prefs.getEncryptedPassword(database) == null) {
            prefs.clear(database)
            return CipherResult.Failed
        }
        return try {
            CipherResult.Ready(keys.decryptCipher(iv))
        } catch (e: Exception) {
            if (e.invalidatesKey()) {
                resetKeys()
                CipherResult.Invalidated
            } else {
                CipherResult.Failed
            }
        }
    }

    fun seal(database: Uri, cipher: Cipher, password: CharArray): Boolean {
        val plain = password.toUtf8()
        return try {
            prefs.save(database, cipher.doFinal(plain), cipher.iv)
            true
        } catch (_: Exception) {
            false
        } finally {
            plain.fill(0)
        }
    }

    fun open(database: Uri, cipher: Cipher): CharArray? {
        val sealed = prefs.getEncryptedPassword(database) ?: return null
        return try {
            val plain = cipher.doFinal(sealed)
            try {
                plain.toChars()
            } finally {
                plain.fill(0)
            }
        } catch (_: Exception) {
            prefs.clear(database)
            null
        }
    }

    fun disable(database: Uri) = prefs.clear(database)

    private fun resetKeys() {
        keys.deleteKey()
        prefs.clearAll()
    }

    private fun Exception.invalidatesKey(): Boolean =
        this is InvalidKeyException || this is UnrecoverableKeyException ||
            this is KeyStoreException || this is BiometricKeyMissingException

    private fun CharArray.toUtf8(): ByteArray {
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(this))
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        buffer.clear()
        if (buffer.hasArray()) buffer.array().fill(0)
        return bytes
    }

    private fun ByteArray.toChars(): CharArray {
        val buffer: CharBuffer = Charsets.UTF_8.decode(ByteBuffer.wrap(this))
        val chars = CharArray(buffer.remaining())
        buffer.get(chars)
        if (buffer.hasArray()) buffer.array().fill('\u0000')
        return chars
    }
}
