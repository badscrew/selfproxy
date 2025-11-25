package com.sshtunnel.android.di

import android.content.Context
import com.sshtunnel.db.DatabaseDriverFactory
import com.sshtunnel.db.SSHTunnelDatabase
import com.sshtunnel.repository.ProfileRepository
import com.sshtunnel.repository.ProfileRepositoryImpl
import com.sshtunnel.ssh.AndroidSSHClient
import com.sshtunnel.ssh.SSHClient
import com.sshtunnel.ssh.SSHConnectionManager
import com.sshtunnel.ssh.SSHConnectionManagerImpl
import com.sshtunnel.storage.AndroidCredentialStore
import com.sshtunnel.storage.CredentialStore
import com.sshtunnel.testing.ConnectionTestService
import com.sshtunnel.testing.ConnectionTestServiceImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing database and repository dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SSHTunnelDatabase {
        val driverFactory = DatabaseDriverFactory(context)
        return SSHTunnelDatabase(driverFactory.createDriver())
    }
    
    @Provides
    @Singleton
    fun provideProfileRepository(database: SSHTunnelDatabase): ProfileRepository {
        return ProfileRepositoryImpl(database)
    }
    
    @Provides
    @Singleton
    fun provideCredentialStore(@ApplicationContext context: Context): CredentialStore {
        return AndroidCredentialStore(context)
    }
    
    @Provides
    @Singleton
    fun provideSSHClient(): SSHClient {
        return AndroidSSHClient()
    }
    
    @Provides
    @Singleton
    fun provideSSHConnectionManager(
        sshClient: SSHClient,
        credentialStore: CredentialStore
    ): SSHConnectionManager {
        return SSHConnectionManagerImpl(
            sshClient = sshClient,
            credentialStore = credentialStore
        )
    }
    
    @Provides
    @Singleton
    fun provideHttpClient(): io.ktor.client.HttpClient {
        return io.ktor.client.HttpClient()
    }
    
    @Provides
    @Singleton
    fun provideConnectionTestService(
        httpClient: io.ktor.client.HttpClient,
        connectionManager: SSHConnectionManager
    ): ConnectionTestService {
        return ConnectionTestServiceImpl(
            httpClient = httpClient,
            connectionManager = connectionManager
        )
    }
}
