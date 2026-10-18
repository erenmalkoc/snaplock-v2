package com.erenium.snaplock.data.crypto

import app.keemobile.kotpass.cryptography.format.BaseCiphers
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.cryptography.format.TwofishCipher
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.encode
import app.keemobile.kotpass.database.header.DatabaseHeader
import app.keemobile.kotpass.database.header.KdfParameters
import app.keemobile.kotpass.models.Meta
import com.erenium.snaplock.domain.model.EncryptionSettings
import com.erenium.snaplock.domain.model.KdfAlgorithm
import com.erenium.snaplock.domain.model.VaultCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.OutputStream
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject

/** Argon2 1.3, the only version KeePass writes. */
private const val ARGON2_VERSION = 0x13u
private const val KDF_SALT_BYTES = 32

internal val VaultCipher.uuid: UUID
    get() = when (this) {
        VaultCipher.AES_256 -> BaseCiphers.Aes.uuid
        VaultCipher.CHACHA20 -> BaseCiphers.ChaCha20.uuid
        VaultCipher.TWOFISH -> TwofishCipher.uuid
    }

/** The cipher and key derivation of a KDBX 4 header; null for a cipher Snaplock does not know. */
internal fun DatabaseHeader.Ver4x.encryptionSettings(): EncryptionSettings? {
    val cipher = VaultCipher.entries.firstOrNull { it.uuid == cipherId } ?: return null
    return when (val kdf = kdfParameters) {
        is KdfParameters.Argon2 -> EncryptionSettings(
            cipher = cipher,
            kdf = if (kdf.variant == KdfParameters.Argon2.Variant.Argon2id) KdfAlgorithm.ARGON2ID else KdfAlgorithm.ARGON2D,
            iterations = kdf.iterations.toLong(),
            memoryBytes = kdf.memory.toLong(),
            parallelism = kdf.parallelism.toInt()
        )
        is KdfParameters.Aes -> EncryptionSettings(cipher, KdfAlgorithm.AES_KDF, kdf.rounds.toLong(), 0, 0)
    }
}

/** Header parameters for [this] key derivation. The salt is replaced again on every save. */
internal fun EncryptionSettings.kdfParameters(salt: ByteString = randomSalt()): KdfParameters = when (kdf) {
    KdfAlgorithm.AES_KDF -> KdfParameters.Aes(rounds = iterations.toULong(), seed = salt)
    KdfAlgorithm.ARGON2D, KdfAlgorithm.ARGON2ID -> KdfParameters.Argon2(
        variant = if (kdf == KdfAlgorithm.ARGON2ID) KdfParameters.Argon2.Variant.Argon2id else KdfParameters.Argon2.Variant.Argon2d,
        salt = salt,
        parallelism = parallelism.toUInt(),
        memory = memoryBytes.toULong(),
        iterations = iterations.toULong(),
        version = ARGON2_VERSION,
        secretKey = null,
        associatedData = null
    )
}

private fun randomSalt(): ByteString = ByteArray(KDF_SALT_BYTES).also { SecureRandom().nextBytes(it) }.toByteString()

/** Times key derivation on this device, to suggest settings that unlock in a chosen time. */
class KdfBenchmark @Inject constructor() {

    /**
     * Iterations (Argon2 passes or AES-KDF rounds) that make [settings] take about [targetMillis]
     * here. Memory and parallelism stay as they are.
     */
    suspend fun iterationsFor(settings: EncryptionSettings, targetMillis: Long = 1_000): Long =
        withContext(Dispatchers.Default) {
            // Grow the probe until it runs long enough to measure, then scale it to the target.
            var probe = if (settings.kdf == KdfAlgorithm.AES_KDF) AES_PROBE_ROUNDS else 1L
            var millis = millisFor(settings.copy(iterations = probe))
            while (millis < MIN_PROBE_MILLIS && probe < MAX_PROBE) {
                probe *= 2
                millis = millisFor(settings.copy(iterations = probe))
            }
            (probe * targetMillis / millis.coerceAtLeast(1)).coerceAtLeast(1)
        }

    // kotpass keeps its key derivation internal, so this saves an empty vault instead:
    // deriving the key is nearly all of that work.
    private fun millisFor(settings: EncryptionSettings): Long {
        val empty = KeePassDatabase.Ver4x.create(rootName = "", meta = Meta(), credentials = credentials)
        val vault = empty.copy(header = empty.header.copy(kdfParameters = settings.kdfParameters()))
        val started = System.nanoTime()
        vault.encode(DiscardingStream)
        return (System.nanoTime() - started) / NANOS_PER_MILLI
    }

    private val credentials = Credentials.from(EncryptedValue.fromString("benchmark"))

    private object DiscardingStream : OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    private companion object {
        const val AES_PROBE_ROUNDS = 10_000L
        const val MAX_PROBE = 1L shl 32
        const val MIN_PROBE_MILLIS = 100L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
