package com.erenium.snaplock.credentials

import com.erenium.snaplock.domain.passkeys.AttestedCredential
import com.erenium.snaplock.domain.passkeys.Passkey
import com.erenium.snaplock.domain.passkeys.WebAuthn
import org.json.JSONArray
import org.json.JSONObject
import java.security.interfaces.ECPublicKey

/** What a site sends to register a passkey: PublicKeyCredentialCreationOptionsJSON. */
data class CreationRequest(
    val rpId: String,
    val rpName: String?,
    /** Base64url. */
    val userId: String,
    val userName: String,
    val userDisplayName: String?,
    /** Base64url. */
    val challenge: String,
    val algorithms: List<Int>,
    /** Credential ids, base64url, the site already has for this account. */
    val excludeCredentials: List<String>,
    val userVerification: String?
)

/** What a site sends to sign in with a passkey: PublicKeyCredentialRequestOptionsJSON. */
data class AssertionRequest(
    val rpId: String,
    /** Base64url. */
    val challenge: String,
    /** Credential ids, base64url; empty lets any passkey for the site answer. */
    val allowCredentials: List<String>,
    val userVerification: String?
)

/**
 * Who is asking. Browsers on the privileged list pass the hash of client data they built
 * for the web origin themselves; for any other app Snaplock builds the client data with
 * the app's signing certificate as its origin, which the site checks.
 */
data class PasskeyCaller(
    val origin: String,
    val packageName: String,
    /** Set only for privileged browsers, whose origin Snaplock does not see. */
    val clientDataHash: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean =
        other is PasskeyCaller && other.origin == origin && other.packageName == packageName &&
            other.clientDataHash.contentEquals(clientDataHash)

    override fun hashCode(): Int = origin.hashCode() * 31 + packageName.hashCode()
}

class Registration(val passkey: Passkey, val responseJson: String)

/** Creates passkeys and signs in with them, answering Credential Manager in JSON. */
object PasskeyAuthenticator {

    fun parseCreation(json: String): CreationRequest? = runCatching {
        val options = JSONObject(json)
        val rp = options.getJSONObject("rp")
        val user = options.getJSONObject("user")
        CreationRequest(
            rpId = rp.getString("id"),
            rpName = rp.optString("name").takeIf { it.isNotBlank() },
            userId = user.getString("id"),
            userName = user.optString("name"),
            userDisplayName = user.optString("displayName").takeIf { it.isNotBlank() },
            challenge = options.getString("challenge"),
            algorithms = options.optJSONArray("pubKeyCredParams").objects().map { it.getInt("alg") },
            excludeCredentials = options.optJSONArray("excludeCredentials").objects().map { it.getString("id") },
            userVerification = options.optJSONObject("authenticatorSelection")?.optString("userVerification")
        )
    }.getOrNull()

    fun parseAssertion(json: String): AssertionRequest? = runCatching {
        val options = JSONObject(json)
        AssertionRequest(
            rpId = options.getString("rpId"),
            challenge = options.getString("challenge"),
            allowCredentials = options.optJSONArray("allowCredentials").objects().map { it.getString("id") },
            userVerification = options.optString("userVerification").takeIf { it.isNotBlank() }
        )
    }.getOrNull()

    /** Whether [passkey] may answer [request]: same site, and listed when the site lists ids. */
    fun matches(request: AssertionRequest, passkey: Passkey): Boolean =
        passkey.relyingParty.equals(request.rpId, ignoreCase = true) &&
            (request.allowCredentials.isEmpty() || request.allowCredentials.any { sameId(it, passkey.credentialId) })

    /** Whether the site already registered one of these passkeys for the account. */
    fun isExcluded(request: CreationRequest, passkeys: List<Passkey>): Boolean =
        passkeys.any { passkey -> request.excludeCredentials.any { sameId(it, passkey.credentialId) } }

    /** A new ES256 passkey and the registration response; fails if the site does not take ES256. */
    fun register(request: CreationRequest, caller: PasskeyCaller, userVerified: Boolean): Result<Registration> = runCatching {
        require(request.algorithms.isEmpty() || WebAuthn.ES256 in request.algorithms) { "The site accepts no ES256 keys" }
        val keys = WebAuthn.newKeyPair()
        val credentialId = WebAuthn.newCredentialId()
        val passkey = Passkey(
            credentialId = WebAuthn.base64Url(credentialId),
            privateKeyPem = WebAuthn.toPem(keys.private),
            relyingParty = request.rpId,
            username = request.userName,
            userHandle = normalized(request.userId)
        )
        val publicKey = keys.public as ECPublicKey
        val authData = WebAuthn.authenticatorData(
            request.rpId,
            WebAuthn.flags(userVerified, passkey, attested = true),
            AttestedCredential(credentialId, publicKey)
        )
        val clientData = clientData("webauthn.create", request.challenge, caller)
        val response = JSONObject()
            .put("clientDataJSON", clientData.json)
            .put("attestationObject", WebAuthn.base64Url(WebAuthn.attestationObject(authData)))
            .put("authenticatorData", WebAuthn.base64Url(authData))
            .put("transports", JSONArray(listOf("internal", "hybrid")))
            .put("publicKey", WebAuthn.base64Url(publicKey.encoded))
            .put("publicKeyAlgorithm", WebAuthn.ES256)
        val json = credentialJson(credentialId, response)
            .put("clientExtensionResults", JSONObject().put("credProps", JSONObject().put("rk", true)))
        Registration(passkey, json.toString())
    }

    /** Signs the site's challenge with [passkey] and returns the assertion response. */
    fun assert(request: AssertionRequest, passkey: Passkey, caller: PasskeyCaller, userVerified: Boolean): Result<String> =
        runCatching {
            val authData = WebAuthn.authenticatorData(request.rpId, WebAuthn.flags(userVerified, passkey))
            val clientData = clientData("webauthn.get", request.challenge, caller)
            val signature = WebAuthn.sign(WebAuthn.privateKeyFromPem(passkey.privateKeyPem), authData, clientData.hash)
            val credentialId = WebAuthn.fromBase64Url(passkey.credentialId) ?: error("Bad credential id")
            val response = JSONObject()
                .put("clientDataJSON", clientData.json)
                .put("authenticatorData", WebAuthn.base64Url(authData))
                .put("signature", WebAuthn.base64Url(signature))
                .put("userHandle", passkey.userHandle)
            credentialJson(credentialId, response).put("clientExtensionResults", JSONObject()).toString()
        }

    private class ClientData(val json: String, val hash: ByteArray)

    /**
     * Client data built here for apps; for a privileged browser its own hash, with a
     * placeholder in the response that the browser replaces with what it hashed.
     */
    private fun clientData(type: String, challenge: String, caller: PasskeyCaller): ClientData {
        caller.clientDataHash?.let { return ClientData(BROWSER_CLIENT_DATA, it) }
        // Key order matters: sites may check the first three keys by position.
        val json = JSONObject()
            .put("type", type)
            .put("challenge", normalized(challenge))
            .put("origin", caller.origin)
            .put("crossOrigin", false)
            .put("androidPackageName", caller.packageName)
            .toString()
            .toByteArray(Charsets.UTF_8)
        return ClientData(WebAuthn.base64Url(json), WebAuthn.sha256(json))
    }

    private fun credentialJson(credentialId: ByteArray, response: JSONObject): JSONObject = JSONObject()
        .put("id", WebAuthn.base64Url(credentialId))
        .put("rawId", WebAuthn.base64Url(credentialId))
        .put("type", "public-key")
        .put("authenticatorAttachment", "platform")
        .put("response", response)

    /** The same bytes written as unpadded base64url, whatever form the site used. */
    private fun normalized(base64: String): String =
        WebAuthn.fromBase64Url(base64)?.let(WebAuthn::base64Url) ?: throw IllegalArgumentException("Bad base64url")

    private fun sameId(a: String, b: String): Boolean =
        WebAuthn.fromBase64Url(a)?.contentEquals(WebAuthn.fromBase64Url(b)) == true

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    // Browsers that pass their own client data hash ignore this field.
    private const val BROWSER_CLIENT_DATA = "e30"
}
