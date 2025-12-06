package com.sshtunnel.android.ui.screens.profiles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sshtunnel.data.CipherMethod
import com.sshtunnel.data.ServerProfile

/**
 * Dialog for creating or editing Shadowsocks server profiles.
 * 
 * @param profile Optional profile to edit, null for creating new profile
 * @param onDismiss Callback when dialog is dismissed
 * @param onSave Callback when profile is saved
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileFormDialog(
    profile: ServerProfile?,
    onDismiss: () -> Unit,
    onSave: (name: String, serverHost: String, serverPort: Int, password: String, cipher: CipherMethod) -> Unit
) {
    val isEditing = profile != null
    
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var serverHost by remember { mutableStateOf(profile?.serverHost ?: "") }
    var serverPort by remember { mutableStateOf(profile?.serverPort?.toString() ?: "8388") }
    var password by remember { mutableStateOf("") }
    var cipher by remember { mutableStateOf(profile?.cipher ?: CipherMethod.AES_256_GCM) }
    var showCipherMenu by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    
    var nameError by remember { mutableStateOf<String?>(null) }
    var serverHostError by remember { mutableStateOf<String?>(null) }
    var serverPortError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    
    fun validate(): Boolean {
        var isValid = true
        
        if (name.isBlank()) {
            nameError = "Name is required"
            isValid = false
        } else {
            nameError = null
        }
        
        if (serverHost.isBlank()) {
            serverHostError = "Server host is required"
            isValid = false
        } else {
            serverHostError = null
        }
        
        val portInt = serverPort.toIntOrNull()
        if (portInt == null || portInt !in 1024..65535) {
            serverPortError = "Port must be between 1024 and 65535"
            isValid = false
        } else {
            serverPortError = null
        }
        
        if (!isEditing && password.isBlank()) {
            passwordError = "Password is required"
            isValid = false
        } else {
            passwordError = null
        }
        
        return isValid
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditing) "Edit Profile" else "New Profile")
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text("Profile Name") },
                    placeholder = { Text("My Shadowsocks Server") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Server host field
                OutlinedTextField(
                    value = serverHost,
                    onValueChange = {
                        serverHost = it
                        serverHostError = null
                    },
                    label = { Text("Server Host") },
                    placeholder = { Text("example.com or 192.168.1.1") },
                    isError = serverHostError != null,
                    supportingText = serverHostError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Server port field
                OutlinedTextField(
                    value = serverPort,
                    onValueChange = {
                        serverPort = it
                        serverPortError = null
                    },
                    label = { Text("Server Port") },
                    placeholder = { Text("8388") },
                    isError = serverPortError != null,
                    supportingText = serverPortError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Password field with visibility toggle
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        passwordError = null
                    },
                    label = { Text(if (isEditing) "Password (leave blank to keep current)" else "Password") },
                    placeholder = { Text("Enter server password") },
                    isError = passwordError != null,
                    supportingText = passwordError?.let { { Text(it) } },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "Hide" else "Show")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Cipher selection dropdown
                ExposedDropdownMenuBox(
                    expanded = showCipherMenu,
                    onExpandedChange = { showCipherMenu = it }
                ) {
                    OutlinedTextField(
                        value = when (cipher) {
                            CipherMethod.AES_256_GCM -> "AES-256-GCM"
                            CipherMethod.CHACHA20_IETF_POLY1305 -> "ChaCha20-IETF-Poly1305"
                            CipherMethod.AES_128_GCM -> "AES-128-GCM"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Encryption Cipher") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCipherMenu) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = showCipherMenu,
                        onDismissRequest = { showCipherMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text("AES-256-GCM")
                                    Text(
                                        "Strong security, good performance",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                cipher = CipherMethod.AES_256_GCM
                                showCipherMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text("ChaCha20-IETF-Poly1305")
                                    Text(
                                        "Best for mobile devices",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                cipher = CipherMethod.CHACHA20_IETF_POLY1305
                                showCipherMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text("AES-128-GCM")
                                    Text(
                                        "Faster, still secure",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                cipher = CipherMethod.AES_128_GCM
                                showCipherMenu = false
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (validate()) {
                        onSave(
                            name,
                            serverHost,
                            serverPort.toInt(),
                            password,
                            cipher
                        )
                    }
                }
            ) {
                Text(if (isEditing) "Save" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
