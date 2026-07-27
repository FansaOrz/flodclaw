package com.foldclaw.data.di

import com.foldclaw.data.llm.FakeProviderGateway
import com.foldclaw.data.llm.OpenAiCompatibleGateway
import com.foldclaw.data.llm.ProviderRouter
import com.foldclaw.domain.llm.ProviderGateway
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataNetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OpenAiCompatibleGateway.defaultClient()

    @Provides
    @Singleton
    fun provideFakeProvider(): FakeProviderGateway = FakeProviderGateway()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindModule {
    @Binds
    @Singleton
    abstract fun bindProviderGateway(router: ProviderRouter): ProviderGateway
}
