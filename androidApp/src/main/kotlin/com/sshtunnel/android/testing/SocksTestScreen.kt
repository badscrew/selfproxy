package com.sshtunnel.android.testing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Test screen to verify SOCKS5 proxy functionality.
 * 
 * This tests if a SOCKS5 proxy on a given port works correctly.
 */
@Composable
fun SocksTestScreen(
    socksPort: Int,
    onBack: () -> Unit
) {
    var testLog by remember { mutableStateOf("Ready to test SOCKS5 proxy on port $socksPort\n\n") }
    var isRunning by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    fun log(message: String) {
        testLog += "$message\n"
        android.util.Log.i("SocksTest", message)
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "SOCKS5 Proxy Test",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Port: $socksPort",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        isRunning = true
                        testLog = "Starting SOCKS5 test on port $socksPort...\n\n"
                        
                        try {
                            testSocksProxy(socksPort, ::log)
                        } catch (e: Exception) {
                            log("❌ Test failed: ${e.message}")
                            e.printStackTrace()
                        } finally {
                            isRunning = false
                        }
                    }
                },
                enabled = !isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isRunning) "Testing..." else "Run Test")
            }
            
            Button(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Text(
                text = testLog,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private suspend fun testSocksProxy(socksPort: Int, log: (String) -> Unit) = withContext(Dispatchers.IO) {
    try {
        log("Test 1: Connecting to SOCKS5 port $socksPort...")
        val socket = Socket()
        socket.soTimeout = 5000
        socket.connect(InetSocketAddress("127.0.0.1", socksPort), 5000)
        log("✅ Connected to SOCKS5 port")
        
        // Test 2: SOCKS5 handshake
        log("\nTest 2: Performing SOCKS5 handshake...")
        
        // Send SOCKS5 greeting: [version=5, nmethods=1, method=0 (no auth)]
        val greeting = byteArrayOf(0x05, 0x01, 0x00)
        socket.getOutputStream().write(greeting)
        socket.getOutputStream().flush()
        log("Sent SOCKS5 greeting: ${greeting.joinToString(" ") { "%02X".format(it) }}")
        
        // Read response: [version=5, method=0]
        val response = ByteArray(2)
        val bytesRead = socket.getInputStream().read(response)
        
        if (bytesRead == 2) {
            log("Received SOCKS5 response: ${response.joinToString(" ") { "%02X".format(it) }}")
            
            if (response[0] == 0x05.toByte() && response[1] == 0x00.toByte()) {
                log("✅ SOCKS5 handshake successful")
                
                // Test 3: SOCKS5 CONNECT request to google.com:80
                log("\nTest 3: Sending CONNECT request to google.com:80...")
                
                // CONNECT request
                val domain = "google.com"
                val connectRequest = byteArrayOf(
                    0x05, // version
                    0x01, // cmd: CONNECT
                    0x00, // reserved
                    0x03, // atyp: domain name
                    domain.length.toByte() // domain length
                ) + domain.toByteArray() + byteArrayOf(
                    0x00, // port high byte (80 = 0x0050)
                    0x50  // port low byte
                )
                
                socket.getOutputStream().write(connectRequest)
                socket.getOutputStream().flush()
                log("Sent CONNECT request")
                
                // Read CONNECT response
                val connectResponse = ByteArray(256)
                val connectBytesRead = socket.getInputStream().read(connectResponse)
                
                if (connectBytesRead >= 2) {
                    log("Received CONNECT response (${connectBytesRead} bytes): ${connectResponse.take(connectBytesRead).joinToString(" ") { "%02X".format(it) }}")
                    
                    if (connectResponse[0] == 0x05.toByte() && connectResponse[1] == 0x00.toByte()) {
                        log("✅ SOCKS5 CONNECT successful")
                        
                        // Test 4: Send HTTP request through tunnel
                        log("\nTest 4: Sending HTTP request...")
                        
                        val httpRequest = "GET / HTTP/1.1\r\nHost: google.com\r\nConnection: close\r\n\r\n"
                        socket.getOutputStream().write(httpRequest.toByteArray())
                        socket.getOutputStream().flush()
                        log("Sent HTTP request")
                        
                        // Read HTTP response
                        socket.soTimeout = 10000
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        val firstLine = reader.readLine()
                        
                        if (firstLine != null) {
                            log("✅ Received HTTP response: $firstLine")
                            log("\n✅✅✅ ALL TESTS PASSED!")
                            log("sshj SOCKS5 proxy is working correctly!")
                        } else {
                            log("❌ No HTTP response received")
                        }
                        
                    } else {
                        log("❌ SOCKS5 CONNECT failed: reply=${connectResponse[1]}")
                    }
                } else {
                    log("❌ Failed to read CONNECT response (bytes: $connectBytesRead)")
                }
                
            } else {
                log("❌ SOCKS5 handshake failed: version=${response[0]}, method=${response[1]}")
            }
        } else {
            log("❌ Failed to read SOCKS5 response (bytes read: $bytesRead)")
        }
        
        socket.close()
        log("\nSocket closed")
        
    } catch (e: java.net.SocketTimeoutException) {
        log("❌ Timeout: ${e.message}")
        log("This suggests the SOCKS5 proxy is not responding")
    } catch (e: java.net.ConnectException) {
        log("❌ Connection refused: ${e.message}")
        log("This suggests the SOCKS5 proxy is not listening on port $socksPort")
    } catch (e: java.net.SocketException) {
        log("❌ Socket error: ${e.message}")
        log("This suggests the SOCKS5 proxy closed the connection")
    } catch (e: Exception) {
        log("❌ Test failed: ${e.javaClass.simpleName}: ${e.message}")
        e.printStackTrace()
    }
}
