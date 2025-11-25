package com.sshtunnel.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Android implementation of NetworkMonitor using ConnectivityManager.
 * 
 * Monitors network connectivity changes and emits NetworkState updates.
 * Uses NetworkCallback API for real-time network change notifications.
 * 
 * @property context Android application context
 */
class AndroidNetworkMonitor(private val context: Context) : NetworkMonitor {
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    // Track the current network type to detect changes
    private var currentNetworkType: NetworkType? = null
    
    override fun observeNetworkChanges(): Flow<NetworkState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            
            override fun onAvailable(network: Network) {
                val networkType = getNetworkType(network)
                currentNetworkType = networkType
                trySend(NetworkState.Available(networkType))
            }
            
            override fun onLost(network: Network) {
                currentNetworkType = null
                trySend(NetworkState.Lost)
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                val networkType = determineNetworkType(capabilities)
                val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                
                // Check if network type changed
                if (currentNetworkType != null && currentNetworkType != networkType) {
                    trySend(NetworkState.Changed(currentNetworkType!!, networkType))
                    currentNetworkType = networkType
                } else {
                    currentNetworkType = networkType
                }
                
                // Emit capabilities change
                trySend(NetworkState.CapabilitiesChanged(networkType, isMetered))
            }
            
            override fun onUnavailable() {
                trySend(NetworkState.Lost)
            }
        }
        
        // Build network request to monitor all internet-capable networks
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        
        // Register the callback
        connectivityManager.registerNetworkCallback(networkRequest, callback)
        
        // Emit initial state
        val initialNetworkType = getCurrentNetworkType()
        if (initialNetworkType != null) {
            trySend(NetworkState.Available(initialNetworkType))
        } else {
            trySend(NetworkState.Lost)
        }
        
        // Unregister callback when flow is cancelled
        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
    
    /**
     * Gets the network type for a specific network.
     */
    private fun getNetworkType(network: Network): NetworkType {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return if (capabilities != null) {
            determineNetworkType(capabilities)
        } else {
            NetworkType.UNKNOWN
        }
    }
    
    /**
     * Determines the network type from network capabilities.
     */
    private fun determineNetworkType(capabilities: NetworkCapabilities): NetworkType {
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
            else -> NetworkType.UNKNOWN
        }
    }
    
    /**
     * Gets the current network type.
     */
    private fun getCurrentNetworkType(): NetworkType? {
        val activeNetwork = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return null
        return determineNetworkType(capabilities)
    }
}
