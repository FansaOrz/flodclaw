package com.foldpods.di

import com.foldpods.bluetooth.AirPodsRepositoryImpl
import com.foldpods.data.DataStoreFoldPodsPrefs
import com.foldpods.domain.AirPodsRepository
import com.foldpods.domain.FoldPodsPrefsStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AirPodsAppModule {
    @Binds
    @Singleton
    abstract fun bindRepository(impl: AirPodsRepositoryImpl): AirPodsRepository

    @Binds
    @Singleton
    abstract fun bindPrefs(impl: DataStoreFoldPodsPrefs): FoldPodsPrefsStore
}
