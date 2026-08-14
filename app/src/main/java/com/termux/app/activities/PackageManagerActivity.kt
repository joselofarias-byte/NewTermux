package com.termux.app.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.newtermux.compose.NewTermuxComposeTheme
import com.newtermux.features.NewTermuxSettings
import com.termux.app.TermuxActivity
import com.termux.shared.termux.TermuxConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Browse installed (dpkg) and available (bundled catalog) packages, with
 * install / uninstall handoff to the terminal. Compose migration (Phase 3).
 *
 * Kept under a distinct implementation class while Java entry points remain
 * stable during the staged migration.
 */
open class ComposePackageManagerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NewTermuxComposeTheme(this) {
                PackageManagerScreen(
                    onBack = { finish() },
                    onRunCommand = { cmd ->
                        NewTermuxSettings.setPendingCommand(this, cmd)
                        val intent = Intent(this, TermuxActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(intent)
                    },
                )
            }
        }
    }
}

private data class PkgEntry(val name: String, val version: String?, val description: String?)

private const val TAB_INSTALLED = 0
private const val TAB_AVAILABLE = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackageManagerScreen(
    onBack: () -> Unit,
    onRunCommand: (String) -> Unit,
) {
    val context = LocalContext.current

    var installed by remember { mutableStateOf<List<PkgEntry>>(emptyList()) }
    var available by remember { mutableStateOf<List<PkgEntry>>(emptyList()) }
    var installedNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var installedLoaded by remember { mutableStateOf(false) }
    var availableLoaded by remember { mutableStateOf(false) }

    var tab by remember { mutableStateOf(TAB_INSTALLED) }
    var query by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<PkgEntry?>(null) }
    var confirmUninstall by remember { mutableStateOf<PkgEntry?>(null) }

    LaunchedEffect(Unit) {
        val inst = withContext(Dispatchers.IO) { loadInstalled() }
        installed = inst
        installedNames = inst.map { it.name }.toHashSet()
        installedLoaded = true
    }
    LaunchedEffect(Unit) {
        val avail = withContext(Dispatchers.IO) { loadAvailable(context) }
        available = avail
        availableLoaded = true
    }

    val source = if (tab == TAB_INSTALLED) installed else available
    val loaded = if (tab == TAB_INSTALLED) installedLoaded else availableLoaded
    val q = query.trim().lowercase(Locale.ROOT)
    val filtered = remember(source, q) {
        if (q.isEmpty()) source else source.filter { it.name.lowercase(Locale.ROOT).contains(q) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Package Manager") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == TAB_INSTALLED, onClick = { tab = TAB_INSTALLED; query = "" },
                    text = { Text("Installed (${if (installedLoaded) installed.size else "…"})") })
                Tab(selected = tab == TAB_AVAILABLE, onClick = { tab = TAB_AVAILABLE; query = "" },
                    text = { Text("Available (${if (availableLoaded) available.size else "…"})") })
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search packages") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )

            val status = when {
                !loaded -> "Loading…"
                filtered.isEmpty() -> if (q.isEmpty()) "No packages found." else "No results for \"$q\"."
                else -> "${filtered.size} package${if (filtered.size == 1) "" else "s"}"
            }
            Text(
                status,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.name }) { entry ->
                    PackageRow(
                        entry = entry,
                        subtitle = if (tab == TAB_INSTALLED) {
                            if (entry.version != null) "v${entry.version}" else "installed"
                        } else {
                            if (entry.name in installedNames) "Installed · Termux repo" else "Termux repo"
                        },
                        onClick = { detail = entry },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    detail?.let { entry ->
        val installedTab = tab == TAB_INSTALLED
        val alreadyInstalled = installedTab || entry.name in installedNames
        val message = buildString {
            entry.version?.let { append("Version: $it\n\n") }
            entry.description?.takeIf { it.isNotEmpty() }?.let { append("$it\n\n") }
            append("Repository: Termux Package Repository\npkg.termux.dev\n\n")
            append(if (installedTab) "Command: pkg uninstall ${entry.name}" else "Command: pkg install ${entry.name}")
        }
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(entry.name) },
            text = { Text(message) },
            confirmButton = {
                if (installedTab) {
                    TextButton(onClick = { detail = null; confirmUninstall = entry }) { Text("Uninstall") }
                } else {
                    val label = if (alreadyInstalled) "Reinstall" else "Install"
                    TextButton(onClick = { detail = null; onRunCommand("pkg install -y ${entry.name}\n") }) { Text(label) }
                }
            },
            dismissButton = { TextButton(onClick = { detail = null }) { Text("Close") } },
        )
    }

    confirmUninstall?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmUninstall = null },
            title = { Text("Uninstall ${entry.name}?") },
            text = { Text("This will remove the package from your Termux environment.") },
            confirmButton = {
                TextButton(onClick = { confirmUninstall = null; onRunCommand("pkg uninstall ${entry.name}\n") }) { Text("Uninstall") }
            },
            dismissButton = { TextButton(onClick = { confirmUninstall = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PackageRow(entry: PkgEntry, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun loadInstalled(): List<PkgEntry> {
    val statusPath = TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/var/lib/dpkg/status"
    val f = File(statusPath)
    if (!f.exists()) return emptyList()
    val list = ArrayList<PkgEntry>()
    var pkg: String? = null; var ver: String? = null; var desc: String? = null; var status: String? = null
    try {
        f.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                when {
                    line.startsWith("Package: ") -> pkg = line.substring(9).trim()
                    line.startsWith("Version: ") -> ver = line.substring(9).trim()
                    line.startsWith("Status: ") -> status = line.substring(8).trim()
                    line.startsWith("Description: ") -> desc = line.substring(13).trim()
                    line.isEmpty() -> {
                        if (pkg != null && status == "install ok installed") list.add(PkgEntry(pkg!!, ver, desc))
                        pkg = null; ver = null; desc = null; status = null
                    }
                }
            }
        }
        if (pkg != null && status == "install ok installed") list.add(PkgEntry(pkg!!, ver, desc))
    } catch (ignored: Exception) {
    }
    return list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

private fun loadAvailable(context: android.content.Context): List<PkgEntry> {
    val list = ArrayList<PkgEntry>()
    try {
        context.assets.open("packages.txt").bufferedReader().useLines { lines ->
            lines.forEach { raw ->
                val line = raw.trim()
                if (line.isNotEmpty() && !line.contains("...") && !line.contains(" ")) {
                    list.add(PkgEntry(line, null, null))
                }
            }
        }
    } catch (ignored: Exception) {
    }
    return list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}
