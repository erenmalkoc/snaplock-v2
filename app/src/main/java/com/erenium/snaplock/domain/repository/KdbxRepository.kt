package com.erenium.snaplock.domain.repository

import android.net.Uri

interface KdbxRepository {

    suspend fun unlockDatabase(uri: Uri, password: CharSequence): Result<Unit>

    suspend fun lockDatabase()

}