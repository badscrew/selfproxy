package com.sshtunnel.android.di

import android.content.Context
import com.sshtunnel.db.DatabaseDriverFactory
import com.sshtunnel.db.SSHTunnelDatabase
import com.sshtunnel.repository.ProfileRepository
import com.sshtunnel.repository.ProfileRepositoryImpl
import com.sshtunnel.android.data.SettingsRepository
import com.sshtunnel.ssh.AndroidSSHClient
import com.sshtunnel.ssh.AndroidSSHClientFactory
import com.sshtunnel.ssh.SSHClient
import com.sshtunnel.ssh.SSHClientFactory
import com.sshtunnel.ssh.SSHConnectionManager
import com.sshtunnel.ssh.SSHConnectionManagerImpl
import com.sshtunnel.storage.AndroidCredentialStore
import com.sshtunnel.storage.CredentialStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.sshtunnel.repository.AppRoutingRepository
import com.sshtunnel.repository.AppRoutingRepositoryImpl
import com.sshtunnel.vpn.AndroidVpnTunnelProvider
import com.sshtunnel.vpn.VpnTunnelProvider
import com.sshtunnel.testing.ConnectionTestService
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
    fun provideSSHClientFactory(@ApplicationContext context: Context): SSHClientFactory {
        return AndroidSSHClientFactory(context)
    }
    
    @Provides
    @Singleton
    fun provideSSHClient(
        factory: SSHClientFactory,
        settingsRepository: SettingsRepository,
        logger: com.sshtunnel.logging.Logger
    ): SSHClient {
        // Get the user's SSH implementation preference
        // Note: This is a blocking call, but it's only done once at app startup
        val settings = runBlocking { settingsRepository.settingsFlow.first() }
        return factory.create(logger, settings.sshImplementationType)
    }
    
    @Provides
    @Singleton
    fun provideSSHConnectionManager(
        sshClient: SSHClient,
        credentialStore: CredentialStore,
        logger: com.sshtunnel.logging.Logger
    ): SSHConnectionManager {
        return SSHConnectionManagerImpl(
            sshClient = sshClient,
            credentialStore = credentialStore,
            logger = logger
        )
    }
    
    @Provides
    @Singleton
    fun provideAppRoutingRepository(database: SSHTunnelDatabase): AppRoutingRepository {
        return AppRoutingRepositoryImpl(database)
    }
    
    @Provides
    @Singleton
    fun provideVpnTunnelProvider(@ApplicationContext context: Context): AndroidVpnTunnelProvider {
        return AndroidVpnTunnelProvider(context)
    }
    
    @Provides
    @Singleton
    fun provideConnectionTestService(
        connectionManager: SSHConnectionManager,
        logger: com.sshtunnel.logging.Logger
    ): ConnectionTestService {
        // Use Android-specific implementation with HttpURLConnection
        // which has better SOCKS5 support than OkHttp for JSch dynamic port forwarding
        return com.sshtunnel.testing.AndroidConnectionTestService(connectionManager, logger)
    }
    
    @Provides
    @Singleton
    fun provideVpnController(
        @ApplicationContext context: Context,
        connectionManager: SSHConnectionManager
    ): com.sshtunnel.android.vpn.VpnController {
        return com.sshtunnel.android.vpn.VpnController(context, connectionManager)
    }
    
}
