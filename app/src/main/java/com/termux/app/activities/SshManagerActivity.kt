package com.termux.app.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.newtermux.compose.NewTermuxComposeTheme
import com.newtermux.features.NewTermuxSettings
import com.newtermux.features.SshProfile
import com.newtermux.features.SshProfileStore

/**
 * SSH connection / port-forward profile manager.
 *
 * Compose migration of the former View-based screen (Phase 1). The data model
 * ([SshProfile] / [SshProfileStore]) is unchanged Java and reused as-is.
 */
class SshManagerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NewTermuxComposeTheme(this) {
                SshManagerScreen(
                    onBack = { finish() },
                    onConnect = { profile ->
                        NewTermuxSettings.setPendingCommand(this, profile.buildCommand() + "\n")
                        finishAffinity()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SshManagerScreen(
    onBack: () -> Unit,
    onConnect: (SshProfile) -> Unit,
) {
    val profiles = remember { mutableStateListOf<SshProfile>().apply { addAll(SshProfileStore.load()) } }

    var editorProfile by remember { mutableStateOf<SshProfile?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var connectTarget by remember { mutableStateOf<SshProfile?>(null) }
    var deleteTarget by remember { mutableStateOf<SshProfile?>(null) }

    fun persist() = SshProfileStore.save(profiles.toMutableList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SSH Manager") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { editorProfile = null; showEditor = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add profile")
                    }
                },
            )
        },
    ) { padding ->
        if (profiles.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No SSH profiles yet.\nTap + to add one.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        onClick = { connectTarget = profile },
                        onEdit = { editorProfile = profile; showEditor = true },
                        onDelete = { deleteTarget = profile },
                    )
                }
            }
        }
    }

    if (showEditor) {
        SshEditorDialog(
            existing = editorProfile,
            onDismiss = { showEditor = false },
            onSave = { result ->
                if (editorProfile == null) profiles.add(result)
                persist()
                showEditor = false
            },
        )
    }

    connectTarget?.let { profile ->
        val tunnel = profile.tunnelLabel()
        AlertDialog(
            onDismissRequest = { connectTarget = null },
            title = { Text("Connect to ${profile.nickname}") },
            text = { Text(profile.displayLabel() + (if (tunnel != null) "\nTunnel: $tunnel" else "")) },
            confirmButton = {
                TextButton(onClick = { connectTarget = null; onConnect(profile) }) { Text("Connect") }
            },
            dismissButton = {
                TextButton(onClick = { connectTarget = null }) { Text("Cancel") }
            },
        )
    }

    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Profile") },
            text = { Text("Delete \"${profile.nickname}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    profiles.remove(profile)
                    persist()
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: SshProfile,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    profile.displayLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                profile.tunnelLabel()?.let {
                    Text(
                        "Tunnel: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun SshEditorDialog(
    existing: SshProfile?,
    onDismiss: () -> Unit,
    onSave: (SshProfile) -> Unit,
) {
    var nickname by remember { mutableStateOf(existing?.nickname ?: "") }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var port by remember { mutableStateOf((existing?.port ?: 22).toString()) }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var keyPath by remember { mutableStateOf(existing?.keyPath ?: "") }

    var tunnelEnabled by remember { mutableStateOf(existing?.tunnelEnabled ?: false) }
    var tunnelRemote by remember { mutableStateOf("remote" == existing?.tunnelType) }
    var localPort by remember { mutableStateOf((existing?.tunnelLocalPort ?: 8080).toString()) }
    var remoteHost by remember { mutableStateOf(existing?.tunnelRemoteHost ?: "localhost") }
    var remotePort by remember { mutableStateOf((existing?.tunnelRemotePort ?: 8080).toString()) }

    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit Profile" else "Add SSH Profile") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(nickname, { nickname = it }, label = { Text("Nickname") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(host, { host = it }, label = { Text("Host") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(port, { port = it }, label = { Text("Port") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(keyPath, { keyPath = it }, label = { Text("Private key path (blank = password)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Port forwarding", modifier = Modifier.weight(1f))
                    Switch(checked = tunnelEnabled, onCheckedChange = { tunnelEnabled = it })
                }

                if (tunnelEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !tunnelRemote, onClick = { tunnelRemote = false })
                        Text("Local (-L)", modifier = Modifier.clickable { tunnelRemote = false })
                        RadioButton(selected = tunnelRemote, onClick = { tunnelRemote = true })
                        Text("Remote (-R)", modifier = Modifier.clickable { tunnelRemote = true })
                    }
                    OutlinedTextField(localPort, { localPort = it }, label = { Text("Local port") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(remoteHost, { remoteHost = it }, label = { Text("Remote host") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(remotePort, { remotePort = it }, label = { Text("Remote port") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (nickname.isBlank() || host.isBlank() || username.isBlank()) {
                    error = "Nickname, host, and username are required"
                    return@TextButton
                }
                val profile = existing ?: SshProfile()
                profile.nickname = nickname.trim()
                profile.host = host.trim()
                profile.port = port.trim().toIntOrNull() ?: 22
                profile.username = username.trim()
                profile.keyPath = keyPath.trim()
                profile.tunnelEnabled = tunnelEnabled
                profile.tunnelType = if (tunnelRemote) "remote" else "local"
                profile.tunnelLocalPort = localPort.trim().toIntOrNull() ?: 8080
                profile.tunnelRemoteHost = remoteHost.trim()
                profile.tunnelRemotePort = remotePort.trim().toIntOrNull() ?: 8080
                onSave(profile)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
