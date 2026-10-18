package com.erenium.snaplock.di

import com.erenium.snaplock.data.datasource.local.AssetWordList
import com.erenium.snaplock.data.repository.KdbxRepositoryImpl
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
    abstract fun bindWordList(
        impl: AssetWordList
    ): WordList

}