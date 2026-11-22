package com.erenium.snaplock.data.datasource.local

import android.content.Context
import com.erenium.snaplock.domain.passwords.TwoFactorDirectory
import com.erenium.snaplock.domain.passwords.TwoFactorDomains
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The list of websites with authenticator app codes that ships inside the app. It never goes online. */
@Singleton
class AssetTwoFactorDirectory @Inject constructor(
    @param:ApplicationContext private val context: Context
) : TwoFactorDirectory {
    private val domains: TwoFactorDomains by lazy {
        context.assets.open(FILE).bufferedReader().useLines { TwoFactorDomains.parse(it) }
    }

    override fun offersTotp(url: String?): Boolean = domains.offersTotp(url)

    private companion object {
        const val FILE = "tfa-totp-domains.txt"
    }
}
