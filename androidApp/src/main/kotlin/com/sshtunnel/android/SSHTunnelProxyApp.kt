package com.sshtunnel.android

import android.app.Application
import com.sshtunnel.android.data.SettingsRepository
import com.sshtunnel.logging.Logger
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SSHTunnelProxyApp : Application() {
    
    @Inject
    lateinit var logger: Logger
    
    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    // Application-scoped coroutine scope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        
        // Log app initialization
        logger.info("SSHTunnelProxyApp", "Application onCreate() called")
        
        // Initialize logger with persisted verbose logging setting
        // and keep it synchronized with settings changes
        applicationScope.launch {
            settingsRepository.settingsFlow
                .map { it.verboseLogging }
                .distinctUntilChanged()
                .collect { verboseEnabled ->
                    logger.setVerboseEnabled(verboseEnabled)
                    logger.info("SSHTunnelProxyApp", "Verbose logging ${if (verboseEnabled) "enabled" else "disabled"}")
                    logger.verbose("SSHTunnelProxyApp", "This is a test verbose log entry to verify verbose logging is working")
                }
        }
    }
}
