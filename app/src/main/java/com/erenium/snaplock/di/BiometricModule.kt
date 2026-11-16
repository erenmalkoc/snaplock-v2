package com.erenium.snaplock.di

import com.erenium.snaplock.data.datasource.security.BiometricCryptoManager
import com.erenium.snaplock.data.datasource.security.BiometricKeys
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BiometricModule {

    @Binds
    abstract fun bindBiometricKeys(impl: BiometricCryptoManager): BiometricKeys
}
