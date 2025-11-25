package com.sshtunnel.android.di

import com.sshtunnel.logging.Logger
import com.sshtunnel.logging.LoggerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing logging dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {
    
    @Provides
    @Singleton
    fun provideLogger(): Logger {
        return LoggerImpl()
    }
}
