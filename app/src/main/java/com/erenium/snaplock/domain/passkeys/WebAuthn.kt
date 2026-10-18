package com.erenium.snaplock.domain.passkeys

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec

/**
 * The authenticator side of WebAuthn (https://www.w3.org/TR/webauthn-3/): key pairs,
 * authenticator data, "none" attestation and assertion signatures.
 */
object WebAuthn {

    /** COSE algorithm id for ECDSA with P-256 and SHA-256, the one every site accepts. */
    const val ES256 = -7

    const val FLAG_USER_PRESENT = 0x01
    const val FLAG_USER_VERIFIED = 0x04
    const val FLAG_BACKUP_ELIGIBLE = 0x08
    const val FLAG_BACKED_UP = 0x10
    const val FLAG_ATTESTED_DATA = 0x40

    /** Snaplock does not attest, so it uses the all-zero authenticator model id. */
    private val AAGUID = ByteArray(16)
    private const val CREDENTIAL_ID_BYTES = 32
    private const val P256_COORDINATE_BYTES = 32

    fun newKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    fun newCredentialId(): ByteArray = ByteArray(CREDENTIAL_ID_BYTES).also { SecureRandom().nextBytes(it) }

    /** The flags byte for a passkey: user present and verified, plus its backup state. */
    fun flags(userVerified: Boolean, passkey: Passkey, attested: Boolean = false): Int =
        FLAG_USER_PRESENT or
            (if (userVerified) FLAG_USER_VERIFIED else 0) or
            (if (passkey.backupEligible) FLAG_BACKUP_ELIGIBLE else 0) or
            (if (passkey.backupState) FLAG_BACKED_UP else 0) or
            (if (attested) FLAG_ATTESTED_DATA else 0)

    /**
     * rpIdHash, flags and a zero signature counter (synced passkeys cannot count), followed
     * on registration by the AAGUID, credential id and COSE public key.
     */
    fun authenticatorData(rpId: String, flags: Int, attested: AttestedCredential? = null): ByteArray =
        ByteArrayOutputStream().apply {
            write(rpId.encodeUtf8().sha256().toByteArray())
            write(flags)
            write(ByteArray(4))
            if (attested != null) {
                write(AAGUID)
                write(attested.credentialId.size shr 8)
                write(attested.credentialId.size and 0xFF)
                write(attested.credentialId)
                write(coseKey(attested.publicKey))
            }
        }.toByteArray()

    /** A "none" attestation object around [authenticatorData]. */
    fun attestationObject(authenticatorData: ByteArray): ByteArray =
        Cbor.encode(linkedMapOf("fmt" to "none", "attStmt" to emptyMap<String, Any>(), "authData" to authenticatorData))

    /** The EC2 COSE key for a P-256 public key, in CTAP2 canonical key order. */
    fun coseKey(publicKey: ECPublicKey): ByteArray = Cbor.encode(
        linkedMapOf(
            1 to 2,
            3 to ES256,
            -1 to 1,
            -2 to publicKey.w.affineX.unsigned(P256_COORDINATE_BYTES),
            -3 to publicKey.w.affineY.unsigned(P256_COORDINATE_BYTES)
        )
    )

    /** Signs authenticator data and the client data hash, as an assertion does. */
    fun sign(privateKey: PrivateKey, authenticatorData: ByteArray, clientDataHash: ByteArray): ByteArray {
        val algorithm = when (privateKey.algorithm) {
            "EC" -> "SHA256withECDSA"
            "RSA" -> "SHA256withRSA"
            "Ed25519", "EdDSA" -> "Ed25519"
            else -> throw IllegalArgumentException("Unsupported passkey key type ${privateKey.algorithm}")
        }
        return Signature.getInstance(algorithm).run {
            initSign(privateKey)
            update(authenticatorData)
            update(clientDataHash)
            sign()
        }
    }

    fun toPem(privateKey: PrivateKey): String =
        "-----BEGIN PRIVATE KEY-----\n" +
            privateKey.encoded.toByteString().base64().chunked(PEM_LINE).joinToString("\n") +
            "\n-----END PRIVATE KEY-----"

    /** Reads a PKCS #8 PEM key as KeePassXC writes it: EC P-256, RSA or Ed25519. */
    fun privateKeyFromPem(pem: String): PrivateKey {
        val body = pem.lineSequence().map { it.trim() }.filterNot { it.startsWith("-----") }.joinToString("")
        val der = body.decodeBase64()?.toByteArray() ?: throw IllegalArgumentException("Not a PEM key")
        return listOf("EC", "RSA", "Ed25519").firstNotNullOfOrNull { algorithm ->
            runCatching { KeyFactory.getInstance(algorithm).generatePrivate(PKCS8EncodedKeySpec(der)) }.getOrNull()
        } ?: throw IllegalArgumentException("Unsupported passkey key")
    }

    fun base64Url(bytes: ByteArray): String = bytes.toByteString().base64Url().trimEnd('=')

    /** Decodes base64url (or plain base64), with or without padding. */
    fun fromBase64Url(text: String): ByteArray? = text.trim().decodeBase64()?.toByteArray()

    fun sha256(bytes: ByteArray): ByteArray = bytes.toByteString().sha256().toByteArray()

    private fun BigInteger.unsigned(length: Int): ByteArray {
        val bytes = toByteArray()
        return when {
            bytes.size == length -> bytes
            bytes.size > length -> bytes.copyOfRange(bytes.size - length, bytes.size)
            else -> ByteArray(length - bytes.size) + bytes
        }
    }

    private const val PEM_LINE = 64
}

/** The new credential a registration reports in its authenticator data. */
class AttestedCredential(val credentialId: ByteArray, val publicKey: ECPublicKey)

/**
 * Deterministic CBOR (RFC 8949) for the few types WebAuthn needs: integers, byte and text
 * strings, arrays and maps. Map entries are written in the order given.
 */
object Cbor {

    fun encode(value: Any): ByteArray = ByteArrayOutputStream().also { write(it, value) }.toByteArray()

    private fun write(out: ByteArrayOutputStream, value: Any) {
        when (value) {
            is Int -> writeInteger(out, value.toLong())
            is Long -> writeInteger(out, value)
            is ByteArray -> {
                header(out, MAJOR_BYTES, value.size.toLong())
                out.write(value)
            }
            is String -> {
                val bytes = value.toByteArray(Charsets.UTF_8)
                header(out, MAJOR_TEXT, bytes.size.toLong())
                out.write(bytes)
            }
            is List<*> -> {
                header(out, MAJOR_ARRAY, value.size.toLong())
                value.forEach { write(out, requireNotNull(it)) }
            }
            is Map<*, *> -> {
                header(out, MAJOR_MAP, value.size.toLong())
                value.forEach { (key, item) ->
                    write(out, requireNotNull(key))
                    write(out, requireNotNull(item))
                }
            }
            else -> throw IllegalArgumentException("Cannot encode ${value::class.java.simpleName} as CBOR")
        }
    }

    private fun writeInteger(out: ByteArrayOutputStream, value: Long) {
        if (value >= 0) header(out, MAJOR_UNSIGNED, value) else header(out, MAJOR_NEGATIVE, -1 - value)
    }

    private fun header(out: ByteArrayOutputStream, major: Int, argument: Long) {
        val type = major shl 5
        when {
            argument < 24 -> out.write(type or argument.toInt())
            argument < 0x100 -> {
                out.write(type or 24)
                out.write(argument.toInt())
            }
            argument < 0x10000 -> {
                out.write(type or 25)
                writeBigEndian(out, argument, 2)
            }
            argument < 0x100000000 -> {
                out.write(type or 26)
                writeBigEndian(out, argument, 4)
            }
            else -> {
                out.write(type or 27)
                writeBigEndian(out, argument, 8)
            }
        }
    }

    private fun writeBigEndian(out: ByteArrayOutputStream, value: Long, bytes: Int) {
        for (shift in (bytes - 1) downTo 0) out.write((value shr (shift * 8)).toInt() and 0xFF)
    }

    private const val MAJOR_UNSIGNED = 0
    private const val MAJOR_NEGATIVE = 1
    private const val MAJOR_BYTES = 2
    private const val MAJOR_TEXT = 3
    private const val MAJOR_ARRAY = 4
    private const val MAJOR_MAP = 5
}
