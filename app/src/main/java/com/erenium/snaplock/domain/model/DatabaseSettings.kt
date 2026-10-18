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
