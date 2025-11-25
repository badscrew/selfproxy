package com.sshtunnel.vpn

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/**
 * Provides information about installed applications on the device.
 * 
 * Used for per-app routing configuration to allow users to select
 * which apps should use the VPN tunnel.
 */
class InstalledAppsProvider(private val context: Context) {
    
    /**
     * Represents an installed application.
     * 
     * @property packageName The package name
     * @property appName The human-readable app name
     * @property icon The app icon
     * @property isSystemApp Whether this is a system app
     */
    data class InstalledApp(
        val packageName: String,
        val appName: String,
        val icon: Drawable?,
        val isSystemApp: Boolean
    )
    
    /**
     * Gets all installed applications on the device.
     * 
     * @param includeSystemApps Whether to include system apps
     * @return List of installed applications
     */
    fun getInstalledApps(includeSystemApps: Boolean = false): List<InstalledApp> {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        return installedApps
            .filter { appInfo ->
                // Filter out system apps if requested
                if (!includeSystemApps && isSystemApp(appInfo)) {
                    return@filter false
                }
                
                // Filter out our own app
                if (appInfo.packageName == context.packageName) {
                    return@filter false
                }
                
                true
            }
            .map { appInfo ->
                InstalledApp(
                    packageName = appInfo.packageName,
                    appName = getAppName(pm, appInfo),
                    icon = getAppIcon(pm, appInfo),
                    isSystemApp = isSystemApp(appInfo)
                )
            }
            .sortedBy { it.appName.lowercase() }
    }
    
    /**
     * Gets a specific installed app by package name.
     * 
     * @param packageName The package name
     * @return The installed app, or null if not found
     */
    fun getApp(packageName: String): InstalledApp? {
        val pm = context.packageManager
        
        return try {
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            
            InstalledApp(
                packageName = appInfo.packageName,
                appName = getAppName(pm, appInfo),
                icon = getAppIcon(pm, appInfo),
                isSystemApp = isSystemApp(appInfo)
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
    
    /**
     * Checks if an app is installed.
     * 
     * @param packageName The package name
     * @return True if the app is installed
     */
    fun isAppInstalled(packageName: String): Boolean {
        val pm = context.packageManager
        
        return try {
            pm.getApplicationInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    private fun getAppName(pm: PackageManager, appInfo: ApplicationInfo): String {
        return try {
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            appInfo.packageName
        }
    }
    
    private fun getAppIcon(pm: PackageManager, appInfo: ApplicationInfo): Drawable? {
        return try {
            pm.getApplicationIcon(appInfo)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun isSystemApp(appInfo: ApplicationInfo): Boolean {
        return (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }
}
