package com.erenium.snaplock.domain.model

data class DatabaseSettings(
    val name: String,
    val recycleBinEnabled: Boolean,
    val historyMaxItems: Int,
    val encryption: EncryptionSettings?
)

data class EncryptionSettings(
    val cipher: VaultCipher,
    val kdf: KdfAlgorithm,
    val iterations: Long,
    val memoryBytes: Long,
    val parallelism: Int
)

enum class VaultCipher { AES_256, CHACHA20, TWOFISH }

enum class KdfAlgorithm { ARGON2D, ARGON2ID, AES_KDF }

const val MIN_ARGON2_MEMORY_PER_LANE = 8 * 1024L
const val MAX_ARGON2_PARALLELISM = 64

fun EncryptionSettings.problem(): String? = when {
    iterations < 1 -> "At least one iteration is needed"
    kdf == KdfAlgorithm.AES_KDF -> null
    parallelism !in 1..MAX_ARGON2_PARALLELISM -> "Parallelism must be between 1 and $MAX_ARGON2_PARALLELISM"
    memoryBytes < MIN_ARGON2_MEMORY_PER_LANE * parallelism -> "Argon2 needs at least 8 KiB of memory per lane"
    else -> null
}
