package com.sshtunnel.android.logging

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.sshtunnel.logging.LogExporter
import com.sshtunnel.logging.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android service for exporting logs to files and sharing.
 */
@Singleton
class LogExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
) {
    /**
     * Export logs as text file and return share intent.
     * 
     * @return Intent for sharing the log file
     */
    suspend fun exportLogsAsText(): Result<Intent> = withContext(Dispatchers.IO) {
        try {
            val entries = logger.getLogEntries()
            val content = LogExporter.exportAsText(entries)
            
            val file = createLogFile("ssh_tunnel_logs.txt")
            file.writeText(content)
            
            val intent = createShareIntent(file, "text/plain")
            Result.success(intent)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Export logs as JSON file and return share intent.
     * 
     * @return Intent for sharing the log file
     */
    suspend fun exportLogsAsJson(): Result<Intent> = withContext(Dispatchers.IO) {
        try {
            val entries = logger.getLogEntries()
            val content = LogExporter.exportAsJson(entries)
            
            val file = createLogFile("ssh_tunnel_logs.json")
            file.writeText(content)
            
            val intent = createShareIntent(file, "application/json")
            Result.success(intent)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Creates a log file in the cache directory.
     */
    private fun createLogFile(filename: String): File {
        val logsDir = File(context.cacheDir, "logs")
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        
        return File(logsDir, filename)
    }
    
    /**
     * Creates a share intent for the log file.
     */
    private fun createShareIntent(file: File, mimeType: String): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SSH Tunnel Proxy Logs")
            putExtra(Intent.EXTRA_TEXT, "Diagnostic logs from SSH Tunnel Proxy")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
