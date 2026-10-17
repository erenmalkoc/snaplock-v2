package com.erenium.snaplock.credentials

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.credentials.provider.Action
import androidx.credentials.provider.AuthenticationAction
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePasswordCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPasswordOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.PasswordCredentialEntry
import com.erenium.snaplock.R
import com.erenium.snaplock.autofill.CredentialMatcher
import com.erenium.snaplock.domain.repository.KdbxRepository
import kotlinx.coroutines.flow.first

/**
 * Answers Android 14+ Credential Manager queries. Only names and usernames are shown in
 * the system sheet; the password leaves Snaplock once the user picks an entry, through
 * [CredentialActivity].
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PasswordProvider(
    private val context: Context,
    private val repository: KdbxRepository
) {

    suspend fun beginGet(request: BeginGetCredentialRequest): BeginGetCredentialResponse {
        val options = request.beginGetCredentialOptions.filterIsInstance<BeginGetPasswordOption>()
        if (options.isEmpty()) return BeginGetCredentialResponse()
        if (repository.isLocked().first()) {
            return BeginGetCredentialResponse(
                authenticationActions = listOf(
                    AuthenticationAction(
                        context.getString(R.string.credentials_unlock),
                        CredentialActivity.unlockIntent(context)
                    )
                )
            )
        }
        // Apps ask for their own logins, so match on the calling package like autofill does.
        val callingPackage = request.callingAppInfo?.packageName
        val matches = callingPackage
            ?.let { CredentialMatcher.match(repository.getCredentials(), it, webDomain = null) }
            .orEmpty()
            .filter { !it.password.isNullOrEmpty() }
        val icon = Icon.createWithResource(context, R.drawable.app_logo)
        val entries = options.flatMap { option ->
            matches.map { credential ->
                PasswordCredentialEntry.Builder(
                    context,
                    credential.username?.takeIf { it.isNotEmpty() } ?: credential.title,
                    CredentialActivity.selectIntent(context, credential.uuid),
                    option
                )
                    .setDisplayName(credential.title)
                    .setIcon(icon)
                    .build()
            }
        }
        val search = Action(
            context.getString(R.string.autofill_search_title),
            CredentialActivity.searchIntent(context),
            null
        )
        return BeginGetCredentialResponse(credentialEntries = entries, actions = listOf(search))
    }

    fun beginCreate(request: BeginCreateCredentialRequest): BeginCreateCredentialResponse {
        if (request !is BeginCreatePasswordCredentialRequest) return BeginCreateCredentialResponse()
        val entry = CreateEntry.Builder(
            context.getString(R.string.app_name),
            CredentialActivity.createIntent(context)
        ).build()
        return BeginCreateCredentialResponse(createEntries = listOf(entry))
    }
}
