package com.sshtunnel.android.di

import android.content.Context
import com.sshtunnel.connection.ConnectionManager
import com.sshtunnel.connection.ConnectionManagerImpl
import com.sshtunnel.db.DatabaseDriverFactory
import com.sshtunnel.db.SSHTunnelDatabase
import com.sshtunnel.logging.Logger
import com.sshtunnel.network.AndroidNetworkMonitor
import com.sshtunnel.network.NetworkMonitor
import com.sshtunnel.reconnection.ReconnectionPolicy
import com.sshtunnel.repository.AppRoutingRepository
import com.sshtunnel.repository.AppRoutingRepositoryImpl
import com.sshtunnel.repository.ProfileRepository
import com.sshtunnel.repository.ProfileRepositoryImpl
import com.sshtunnel.shadowsocks.AndroidShadowsocksClient
import com.sshtunnel.shadowsocks.ShadowsocksClient
import com.sshtunnel.storage.AndroidCredentialStore
import com.sshtunnel.storage.CredentialStore
import com.sshtunnel.vpn.AndroidVpnTunnelProvider
import com.sshtunnel.vpn.VpnTunnelProvider
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
    
    // TODO: SSH-related providers removed - will be replaced with Shadowsocks implementations
    // in subsequent tasks
    
    @Provides
    @Singleton
    fun provideAppRoutingRepository(database: SSHTunnelDatabase): AppRoutingRepository {
        return AppRoutingRepositoryImpl(database)
    }
    
    @Provides
    @Singleton
    fun provideVpnTunnelProvider(@ApplicationContext context: Context): VpnTunnelProvider {
        return AndroidVpnTunnelProvider(context)
    }
    
    @Provides
    @Singleton
    fun provideShadowsocksClient(
        @ApplicationContext context: Context,
        logger: Logger
    ): ShadowsocksClient {
        return AndroidShadowsocksClient(context, logger)
    }
    
    @Provides
    @Singleton
    fun provideNetworkMonitor(@ApplicationContext context: Context): NetworkMonitor {
        return AndroidNetworkMonitor(context)
    }
    
    @Provides
    @Singleton
    fun provideConnectionManager(
        shadowsocksClient: ShadowsocksClient,
        vpnTunnelProvider: VpnTunnelProvider,
        credentialStore: CredentialStore,
        networkMonitor: NetworkMonitor,
        logger: Logger
    ): ConnectionManager {
        return ConnectionManagerImpl(
            shadowsocksClient = shadowsocksClient,
            vpnTunnelProvider = vpnTunnelProvider,
            credentialStore = credentialStore,
            networkMonitor = networkMonitor,
            reconnectionPolicy = ReconnectionPolicy.default(),
            logger = logger
        )
    }
    
}
