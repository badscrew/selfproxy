package com.sshtunnel.android.ui.screens.profiles

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sshtunnel.data.KeyType
import com.sshtunnel.data.ServerProfile
import java.io.InputStream

/**
 * Dialog for creating or editing SSH server profiles.
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
    onSave: (name: String, hostname: String, port: Int, username: String, keyType: KeyType, keyData: ByteArray?) -> Unit
) {
    val context = LocalContext.current
    val isEditing = profile != null
    
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var hostname by remember { mutableStateOf(profile?.hostname ?: "") }
    var port by remember { mutableStateOf(profile?.port?.toString() ?: "22") }
    var username by remember { mutableStateOf(profile?.username ?: "") }
    var keyType by remember { mutableStateOf(profile?.keyType ?: KeyType.ED25519) }
    var keyFileName by remember { mutableStateOf<String?>(null) }
    var keyData by remember { mutableStateOf<ByteArray?>(null) }
    var showKeyTypeMenu by remember { mutableStateOf(false) }
    
    var nameError by remember { mutableStateOf<String?>(null) }
    var hostnameError by remember { mutableStateOf<String?>(null) }
    var portError by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var keyError by remember { mutableStateOf<String?>(null) }
    
    // File picker for SSH key
    val keyFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                keyData = inputStream?.readBytes()
                keyFileName = it.lastPathSegment ?: "key_file"
                keyError = null
            } catch (e: Exception) {
                keyError = "Failed to read key file: ${e.message}"
            }
        }
    }
    
    fun validate(): Boolean {
        var isValid = true
        
        if (name.isBlank()) {
            nameError = "Name is required"
            isValid = false
        } else {
            nameError = null
        }
        
        if (hostname.isBlank()) {
            hostnameError = "Hostname is required"
            isValid = false
        } else {
            hostnameError = null
        }
        
        val portInt = port.toIntOrNull()
        if (portInt == null || portInt !in 1..65535) {
            portError = "Port must be between 1 and 65535"
            isValid = false
        } else {
            portError = null
        }
        
        if (username.isBlank()) {
            usernameError = "Username is required"
            isValid = false
        } else {
            usernameError = null
        }
        
        if (!isEditing && keyData == null) {
            keyError = "SSH key file is required"
            isValid = false
        } else {
            keyError = null
        }
        
        return isValid
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditing) "Edit Profile" else "New Profile")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
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
                    placeholder = { Text("My Server") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Hostname field
                OutlinedTextField(
                    value = hostname,
                    onValueChange = {
                        hostname = it
                        hostnameError = null
                    },
                    label = { Text("Hostname") },
                    placeholder = { Text("example.com") },
                    isError = hostnameError != null,
                    supportingText = hostnameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Port field
                OutlinedTextField(
                    value = port,
                    onValueChange = {
                        port = it
                        portError = null
                    },
                    label = { Text("Port") },
                    placeholder = { Text("22") },
                    isError = portError != null,
                    supportingText = portError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Username field
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        usernameError = null
                    },
                    label = { Text("Username") },
                    placeholder = { Text("user") },
                    isError = usernameError != null,
                    supportingText = usernameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Key type dropdown
                ExposedDropdownMenuBox(
                    expanded = showKeyTypeMenu,
                    onExpandedChange = { showKeyTypeMenu = it }
                ) {
                    OutlinedTextField(
                        value = keyType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Key Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showKeyTypeMenu) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = showKeyTypeMenu,
                        onDismissRequest = { showKeyTypeMenu = false }
                    ) {
                        KeyType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    keyType = type
                                    showKeyTypeMenu = false
                                }
                            )
                        }
                    }
                }
                
                // SSH key file picker
                OutlinedButton(
                    onClick = { keyFilePicker.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = keyFileName ?: if (isEditing) "Change SSH Key (optional)" else "Select SSH Key File"
                    )
                }
                
                if (keyError != null) {
                    Text(
                        text = keyError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (validate()) {
                        onSave(
                            name,
                            hostname,
                            port.toInt(),
                            username,
                            keyType,
                            keyData
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
