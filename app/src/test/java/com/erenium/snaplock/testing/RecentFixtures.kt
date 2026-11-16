package com.erenium.snaplock.testing

import android.content.Context
import com.erenium.snaplock.data.datasource.prefs.EncryptedPrefs
import com.erenium.snaplock.data.datasource.prefs.KeyFilePrefs
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.data.datasource.security.BiometricVault
import com.erenium.snaplock.data.repository.RecentDatabasesRepository

fun testRecentRepository(context: Context, prefs: RecentDatabasesPrefs = RecentDatabasesPrefs(context)) =
    RecentDatabasesRepository(
        context,
        prefs,
        KeyFilePrefs(context),
        BiometricVault(FakeBiometricKeys(), EncryptedPrefs(context))
    )
