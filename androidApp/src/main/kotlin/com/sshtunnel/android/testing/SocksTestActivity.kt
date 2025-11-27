package com.sshtunnel.android.testing

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sshtunnel.android.ui.theme.SSHTunnelProxyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/**
 * Test activity to verify SOCKS5 proxy is working.
 * This tests SSH tunnel + SOCKS5 without VPN layer.
 */
class SocksTestActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "SocksTestActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            SSHTunnelProxyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SocksTestScreen()
                }
            }
        }
    }
}

@Composable
fun SocksTestScreen() {
    var testResult by remember { mutableStateOf("Ready to test") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SOCKS5 Proxy Test",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = testResult,
            style = MaterialTheme.typography.bodyLarge
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    testResult = testSocksProxy()
                    isLoading = false
                }
            },
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Test SOCKS5 Proxy")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    testResult = testDirectConnection()
                    isLoading = false
                }
            },
            enabled = !isLoading
        ) {
            Text("Test Direct Connection")
        }
    }
}

/**
 * Tests connection through SOCKS5 proxy on localhost:1080
 */
suspend fun testSocksProxy(): String = withContext(Dispatchers.IO) {
    try {
        Log.i("SocksTest", "Testing SOCKS5 proxy on localhost:1080")
        
        // Create SOCKS proxy
        val proxy = Proxy(
            Proxy.Type.SOCKS,
            InetSocketAddress("127.0.0.1", 1080)
        )
        
        // Test connection through proxy
        val url = URL("https://api.ipify.org?format=text")
        val connection = url.openConnection(proxy) as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        val responseCode = connection.responseCode
        if (responseCode == 200) {
            val ip = BufferedReader(InputStreamReader(connection.inputStream)).use {
                it.readText().trim()
            }
            Log.i("SocksTest", "✓ SOCKS5 proxy works! External IP: $ip")
            "✓ SOCKS5 Proxy Works!\nExternal IP: $ip"
        } else {
            Log.e("SocksTest", "✗ HTTP error: $responseCode")
            "✗ HTTP Error: $responseCode"
        }
        
    } catch (e: Exception) {
        Log.e("SocksTest", "✗ SOCKS5 proxy test failed", e)
        "✗ SOCKS5 Proxy Failed:\n${e.message}"
    }
}

/**
 * Tests direct connection (without proxy) for comparison
 */
suspend fun testDirectConnection(): String = withContext(Dispatchers.IO) {
    try {
        Log.i("SocksTest", "Testing direct connection")
        
        val url = URL("https://api.ipify.org?format=text")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        val responseCode = connection.responseCode
        if (responseCode == 200) {
            val ip = BufferedReader(InputStreamReader(connection.inputStream)).use {
                it.readText().trim()
            }
            Log.i("SocksTest", "✓ Direct connection works! External IP: $ip")
            "✓ Direct Connection Works!\nExternal IP: $ip"
        } else {
            Log.e("SocksTest", "✗ HTTP error: $responseCode")
            "✗ HTTP Error: $responseCode"
        }
        
    } catch (e: Exception) {
        Log.e("SocksTest", "✗ Direct connection test failed", e)
        "✗ Direct Connection Failed:\n${e.message}"
    }
}
