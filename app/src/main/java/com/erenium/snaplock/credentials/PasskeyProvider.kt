package com.erenium.snaplock.credentials

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialEntry
import androidx.credentials.provider.PublicKeyCredentialEntry
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.repository.KdbxRepository

/** The passkey half of Snaplock's Credential Manager answers; the vault must be open. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PasskeyProvider(
    private val context: Context,
    private val repository: KdbxRepository
) {

    /** One entry per stored passkey the site asked for; signing happens in [CredentialActivity]. */
    suspend fun entries(options: List<BeginGetPublicKeyCredentialOption>): List<CredentialEntry> {
        if (options.isEmpty()) return emptyList()
        val stored = repository.getPasskeys()
        val icon = Icon.createWithResource(context, R.drawable.app_logo)
        return options.flatMap { option ->
            val request = PasskeyAuthenticator.parseAssertion(option.requestJson) ?: return@flatMap emptyList()
            stored.filter { PasskeyAuthenticator.matches(request, it.passkey) }.map { found ->
                PublicKeyCredentialEntry.Builder(
                    context,
                    found.passkey.username.ifEmpty { found.title },
                    CredentialActivity.passkeyIntent(context, found.entryUuid),
                    option
                )
                    .setDisplayName(found.title)
                    .setIcon(icon)
                    .build()
            }
        }
    }

    fun createEntry(request: BeginCreatePublicKeyCredentialRequest): CreateEntry =
        CreateEntry.Builder(context.getString(R.string.app_name), CredentialActivity.createPasskeyIntent(context))
            .setDescription(context.getString(R.string.credentials_passkey_create_description))
            .build()
}
