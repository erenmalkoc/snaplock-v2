package com.erenium.snaplock.di

import com.erenium.snaplock.data.datasource.local.AssetWordList
import com.erenium.snaplock.data.network.PwnedPasswordsClient
import com.erenium.snaplock.data.repository.KdbxRepositoryImpl
import com.erenium.snaplock.domain.passwords.BreachChecker
import com.erenium.snaplock.domain.passwords.WordList
import com.erenium.snaplock.domain.repository.KdbxRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindKdbxRepository(
        impl: KdbxRepositoryImpl
    ): KdbxRepository

    @Binds
    abstract fun bindBreachChecker(
        impl: PwnedPasswordsClient
    ): BreachChecker

    @Binds
    abstract fun bindWordList(
        impl: AssetWordList
    ): WordList

}