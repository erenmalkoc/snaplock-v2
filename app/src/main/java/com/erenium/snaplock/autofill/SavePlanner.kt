package com.erenium.snaplock.autofill

import com.erenium.snaplock.domain.model.Credential

sealed interface SaveDecision {
    data object AlreadySaved : SaveDecision

    data class Update(val credential: Credential) : SaveDecision

    data object New : SaveDecision
}

object SavePlanner {
    fun decide(
        credentials: List<Credential>,
        packageName: String,
        webDomain: String?,
        username: String?,
        password: String
    ): SaveDecision {
        val sameUser = CredentialMatcher.match(credentials, packageName, webDomain)
            .firstOrNull { it.username.orEmpty() == username.orEmpty() }
        return when {
            sameUser == null -> SaveDecision.New
            sameUser.password == password -> SaveDecision.AlreadySaved
            else -> SaveDecision.Update(sameUser)
        }
    }

    fun urlFor(packageName: String, webDomain: String?): String =
        if (webDomain != null && Browsers.isTrusted(packageName)) {
            "https://$webDomain"
        } else {
            "androidapp://$packageName"
        }
}
