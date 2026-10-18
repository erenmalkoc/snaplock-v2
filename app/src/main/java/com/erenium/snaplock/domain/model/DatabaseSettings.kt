package com.erenium.snaplock.domain.model

/** Settings stored in the vault file itself, so every KeePass app that opens it shares them. */
data class DatabaseSettings(
    val name: String,
    val recycleBinEnabled: Boolean,
    /** Previous versions kept per entry; -1 keeps every one. */
    val historyMaxItems: Int,
    /** Null for KDBX 3 files, whose encryption Snaplock leaves as it is. */
    val encryption: EncryptionSettings?
)

/** How a KDBX 4 file is encrypted and how its key is derived from the credentials. */
data class EncryptionSettings(
    val cipher: VaultCipher,
    val kdf: KdfAlgorithm,
    /** Argon2 passes, or AES-KDF rounds. */
    val iterations: Long,
    /** Argon2 only. */
    val memoryBytes: Long,
    /** Argon2 only. */
    val parallelism: Int
)

enum class VaultCipher { AES_256, CHACHA20, TWOFISH }

enum class KdfAlgorithm { ARGON2D, ARGON2ID, AES_KDF }

/** Argon2 needs at least 8 KiB of memory per lane. */
const val MIN_ARGON2_MEMORY_PER_LANE = 8 * 1024L
const val MAX_ARGON2_PARALLELISM = 64

/** Why these settings cannot be used, or null when they can. */
fun EncryptionSettings.problem(): String? = when {
    iterations < 1 -> "At least one iteration is needed"
    kdf == KdfAlgorithm.AES_KDF -> null
    parallelism !in 1..MAX_ARGON2_PARALLELISM -> "Parallelism must be between 1 and $MAX_ARGON2_PARALLELISM"
    memoryBytes < MIN_ARGON2_MEMORY_PER_LANE * parallelism -> "Argon2 needs at least 8 KiB of memory per lane"
    else -> null
}
