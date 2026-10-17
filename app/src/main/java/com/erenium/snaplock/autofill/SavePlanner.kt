package com.erenium.snaplock.autofill

import com.erenium.snaplock.domain.model.Credential

/** What to do with a login the user typed into another app or website. */
sealed interface SaveDecision {
    /** The vault already holds exactly this login. */
    data object AlreadySaved : SaveDecision

    /** Same site and username with a different password: offer to update [credential]. */
    data class Update(val credential: Credential) : SaveDecision

    /** Nothing matches: offer a new entry. */
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

    /**
     * Address for a new entry. Sites only count when a known browser reported them, the
     * same rule [CredentialMatcher] uses, so the saved entry fills again on the next visit.
     */
    fun urlFor(packageName: String, webDomain: String?): String =
        if (webDomain != null && Browsers.isTrusted(packageName)) {
            "https://$webDomain"
        } else {
            "androidapp://$packageName"
        }
}
