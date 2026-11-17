package com.erenium.snaplock.domain.passkeys

import java.util.UUID

data class Passkey(
    val credentialId: String,
    val privateKeyPem: String,
    val relyingParty: String,
    val username: String,
    val userHandle: String,
    val backupEligible: Boolean = true,
    val backupState: Boolean = true
) {
    fun toFields(): List<PasskeyField> = listOf(
        PasskeyField(FIELD_CREDENTIAL_ID, credentialId, isProtected = false),
        PasskeyField(FIELD_PRIVATE_KEY_PEM, privateKeyPem, isProtected = true),
        PasskeyField(FIELD_RELYING_PARTY, relyingParty, isProtected = false),
        PasskeyField(FIELD_USERNAME, username, isProtected = false),
        PasskeyField(FIELD_USER_HANDLE, userHandle, isProtected = true),
        PasskeyField(FIELD_FLAG_BE, flag(backupEligible), isProtected = false),
        PasskeyField(FIELD_FLAG_BS, flag(backupState), isProtected = false)
    )

    companion object {
        private const val FIELD_PREFIX = "KPEX_PASSKEY_"
        const val FIELD_CREDENTIAL_ID = "KPEX_PASSKEY_CREDENTIAL_ID"
        const val FIELD_PRIVATE_KEY_PEM = "KPEX_PASSKEY_PRIVATE_KEY_PEM"
        const val FIELD_RELYING_PARTY = "KPEX_PASSKEY_RELYING_PARTY"
        const val FIELD_USERNAME = "KPEX_PASSKEY_USERNAME"
        const val FIELD_USER_HANDLE = "KPEX_PASSKEY_USER_HANDLE"
        const val FIELD_FLAG_BE = "KPEX_PASSKEY_FLAG_BE"
        const val FIELD_FLAG_BS = "KPEX_PASSKEY_FLAG_BS"

        fun isPasskeyField(name: String): Boolean = name.startsWith(FIELD_PREFIX)

        fun fromFields(fields: Map<String, String>): Passkey? {
            fun value(name: String) = fields[name]?.trim()?.takeIf { it.isNotEmpty() }
            return Passkey(
                credentialId = value(FIELD_CREDENTIAL_ID) ?: return null,
                privateKeyPem = value(FIELD_PRIVATE_KEY_PEM) ?: return null,
                relyingParty = value(FIELD_RELYING_PARTY) ?: return null,
                username = value(FIELD_USERNAME).orEmpty(),
                userHandle = value(FIELD_USER_HANDLE).orEmpty(),
                backupEligible = value(FIELD_FLAG_BE) != "0",
                backupState = value(FIELD_FLAG_BS) != "0"
            )
        }

        private fun flag(value: Boolean) = if (value) "1" else "0"
    }
}

data class PasskeyField(val name: String, val value: String, val isProtected: Boolean)

data class StoredPasskey(val entryUuid: UUID, val title: String, val passkey: Passkey)
