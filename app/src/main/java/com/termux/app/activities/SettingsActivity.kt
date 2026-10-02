package com.termux.app.activities

import com.termux.R

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.newtermux.compose.MenuItemDivider
import com.newtermux.compose.NewTermuxComposeTheme
import com.newtermux.compose.outlinedMenuCard
import com.newtermux.features.NewTermuxSettings
import com.newtermux.features.NativeBackupManager
import com.newtermux.features.NativeStorageManager
import com.newtermux.features.TextExpansionStore
import com.termux.app.TermuxActivity
import com.termux.app.TermuxService
import com.termux.shared.android.PermissionUtils
import com.termux.app.TermuxInstaller
import com.termux.app.models.UserAction
import com.termux.shared.android.AndroidUtils
import com.termux.shared.android.PackageUtils
import com.termux.shared.file.FileUtils
import com.termux.shared.interact.ShareUtils
import com.termux.shared.logger.Logger
import com.termux.shared.models.ReportInfo
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE
import com.termux.shared.termux.TermuxUtils
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
import com.termux.shared.termux.settings.preferences.TermuxFloatAppSharedPreferences
import com.termux.shared.termux.settings.preferences.TermuxTaskerAppSharedPreferences
import com.termux.shared.termux.settings.preferences.TermuxWidgetAppSharedPreferences
import com.termux.shared.activities.ReportActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * NewTermux settings, fully rewritten in Jetpack Compose (Phase 5), replacing
 * the androidx.preference framework. Uses an internal back-stack to navigate
 * the root list, the NewTermux sections (Backup/Features/Text Expansion), and
 * the upstream Termux + plugin preference sub-screens, binding directly to the
 * existing SharedPreferences / data-store APIs.
 */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NewTermuxComposeTheme(this) {
                SettingsRoot(activity = this)
            }
        }
    }
}

private enum class Route {
    ROOT, BACKUP, STORAGE, FEATURES, TEXT_EXPANSION,
    TERMUX, TERMINAL_IO, TERMINAL_VIEW, DEBUGGING,
    PLUGIN_API, PLUGIN_FLOAT, PLUGIN_TASKER, PLUGIN_WIDGET,
}

@Composable
private fun SettingsRoot(activity: Activity) {
    val stack = remember { mutableStateListOf(Route.ROOT) }
    fun push(r: Route) = stack.add(r)
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else activity.finish() }

    BackHandler { pop() }

    when (stack.last()) {
        Route.ROOT -> RootScreen(activity, onBack = { pop() }, onNav = { push(it) })
        Route.BACKUP -> BackupScreen(onBack = { pop() })
        Route.STORAGE -> StorageScreen(onBack = { pop() })
        Route.FEATURES -> FeaturesScreen(activity, onBack = { pop() })
        Route.TEXT_EXPANSION -> TextExpansionScreen(onBack = { pop() })
        Route.TERMUX -> TermuxScreen(onBack = { pop() }, onNav = { push(it) })
        Route.TERMINAL_IO -> TerminalIOScreen(onBack = { pop() })
        Route.TERMINAL_VIEW -> TerminalViewScreen(onBack = { pop() })
        Route.DEBUGGING -> DebuggingScreen(onBack = { pop() })
        Route.PLUGIN_API -> PluginScreen("Termux:API", Plugin.API, onBack = { pop() })
        Route.PLUGIN_FLOAT -> PluginScreen("Termux:Float", Plugin.FLOAT, onBack = { pop() })
        Route.PLUGIN_TASKER -> PluginScreen("Termux:Tasker", Plugin.TASKER, onBack = { pop() })
        Route.PLUGIN_WIDGET -> PluginScreen("Termux:Widget", Plugin.WIDGET, onBack = { pop() })
    }
}

// ---------------------------------------------------------------- shared UI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = context.getString(R.string.nt_l10n_back))
                    }
                },
            )
        },
    ) { padding ->
        content(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()))
    }
}

@Composable
private fun CategoryHeader(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun NavRow(title: String, summary: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SwitchRow(title: String, summary: String?, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** Bool switch backed by NewTermuxSettings.get/set. */
@Composable
private fun NtSwitch(context: Context, key: String, title: String, summary: String?) {
    var checked by remember { mutableStateOf(NewTermuxSettings.get(context, key)) }
    SwitchRow(title, summary, checked) {
        checked = it
        NewTermuxSettings.set(context, key, it)
    }
}

/**
 * "Keep alive in background" toggle. Backed by NewTermuxSettings, and when enabled it also asks
 * the user to allowlist the app from battery optimization (if not already exempt) since the wake
 * lock alone can still lose to Doze.
 */
@Composable
private fun KeepAliveSwitch(activity: Activity) {
    val context = LocalContext.current
    var checked by remember { mutableStateOf(NewTermuxSettings.isKeepAliveInBackground(context)) }
    SwitchRow(
        title = context.getString(R.string.nt_l10n_keep_alive),
        summary = context.getString(R.string.nt_l10n_keep_alive_summary),
        checked = checked,
    ) {
        checked = it
        NewTermuxSettings.set(context, NewTermuxSettings.KEY_KEEP_ALIVE_BACKGROUND, it)
        if (it && !PermissionUtils.checkIfBatteryOptimizationsDisabled(context)) {
            // Mark prompted so the first-run nudge won't also fire, then request the exemption.
            NewTermuxSettings.setBatteryOptPrompted(context, true)
            runCatching { PermissionUtils.requestDisableBatteryOptimizations(activity) }
        }
    }
}

@Composable
private fun LogLevelRow(context: Context, current: Int, onSelect: (Int) -> Unit) {
    val values = remember { Logger.getLogLevelsArray().map { it.toString() } }
    val labels = values.map { rawLevel ->
        val level = rawLevel.toInt()
        val label = when (level) {
            Logger.LOG_LEVEL_OFF -> R.string.nt_l10n_log_off
            Logger.LOG_LEVEL_NORMAL -> R.string.nt_l10n_log_normal
            Logger.LOG_LEVEL_DEBUG -> R.string.nt_l10n_log_debug
            Logger.LOG_LEVEL_VERBOSE -> R.string.nt_l10n_log_verbose
            else -> null
        }
        if (label == null) level.toString() else context.getString(label, level)
    }
    var expanded by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(current) }
    val idx = values.indexOf(value.toString()).coerceAtLeast(0)
    Box {
        NavRow(title = context.getString(R.string.nt_l10n_log_level), summary = labels.getOrNull(idx)) { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.outlinedMenuCard()) {
            values.forEachIndexed { i, v ->
                if (i > 0) MenuItemDivider()
                DropdownMenuItem(text = { Text(labels.getOrElse(i) { v }) }, onClick = {
                    expanded = false
                    v.toIntOrNull()?.let { lvl ->
                        value = lvl
                        onSelect(lvl)
                    }
                })
            }
        }
    }
}

// ---------------------------------------------------------------- root

@Composable
private fun RootScreen(activity: Activity, onBack: () -> Unit, onNav: (Route) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val apiInstalled = remember { TermuxAPIAppSharedPreferences.build(context, false) != null }
    val floatInstalled = remember { TermuxFloatAppSharedPreferences.build(context, false) != null }
    val taskerInstalled = remember { TermuxTaskerAppSharedPreferences.build(context, false) != null }
    val widgetInstalled = remember { TermuxWidgetAppSharedPreferences.build(context, false) != null }
    val donateVisible = remember { isDonateVisible(context) }

    fun launch(cls: Class<*>) = context.startActivity(Intent(context, cls))

    SettingsScaffold(context.getString(R.string.nt_l10n_settings), onBack) { mod ->
        Column(modifier = mod) {
            NavRow(context.getString(R.string.nt_l10n_appearance), context.getString(R.string.nt_l10n_appearance_summary)) { launch(ThemePickerActivity::class.java) }
            NavRow(context.getString(R.string.nt_l10n_packages), context.getString(R.string.nt_l10n_packages_summary)) { launch(PackageManagerActivity::class.java) }
            NavRow(context.getString(R.string.nt_l10n_ssh), context.getString(R.string.nt_l10n_ssh_summary)) { launch(SshManagerActivity::class.java) }
            NavRow(context.getString(R.string.nt_l10n_files), context.getString(R.string.nt_l10n_files_summary)) { launch(FileManagerActivity::class.java) }
            NavRow("Almacenamiento y respaldo", "Espacio por componente, respaldo y restauración nativos de NewTermux") { onNav(Route.STORAGE) }
            HorizontalDivider()
            NavRow(context.getString(R.string.nt_l10n_backup_restore), context.getString(R.string.nt_l10n_backup_summary)) { onNav(Route.BACKUP) }
            NavRow(context.getString(R.string.nt_l10n_features), context.getString(R.string.nt_l10n_features_summary)) { onNav(Route.FEATURES) }
            NavRow(context.getString(R.string.nt_l10n_expansions), context.getString(R.string.nt_l10n_expansions_summary)) { onNav(Route.TEXT_EXPANSION) }
            HorizontalDivider()
            NavRow("Termux", context.getString(R.string.nt_l10n_termux_summary)) { onNav(Route.TERMUX) }
            if (apiInstalled) NavRow("Termux:API", context.getString(R.string.nt_l10n_api_summary)) { onNav(Route.PLUGIN_API) }
            if (floatInstalled) NavRow("Termux:Float", context.getString(R.string.nt_l10n_float_summary)) { onNav(Route.PLUGIN_FLOAT) }
            if (taskerInstalled) NavRow("Termux:Tasker", context.getString(R.string.nt_l10n_tasker_summary)) { onNav(Route.PLUGIN_TASKER) }
            if (widgetInstalled) NavRow("Termux:Widget", context.getString(R.string.nt_l10n_widget_summary)) { onNav(Route.PLUGIN_WIDGET) }
            HorizontalDivider()
            NavRow(context.getString(R.string.nt_l10n_about), context.getString(R.string.nt_l10n_about_summary)) {
                scope.launch { openAbout(context) }
            }
            if (donateVisible) NavRow(context.getString(R.string.nt_l10n_donate), context.getString(R.string.nt_l10n_donate_summary)) { ShareUtils.openUrl(context, TermuxConstants.TERMUX_DONATE_URL) }
        }
    }
}

// ---------------------------------------------------------------- Native storage

private object NativeStorageSession {
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cancelRequested = AtomicBoolean(false)

    var snapshot by mutableStateOf<NativeStorageManager.Snapshot?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var scanPhase by mutableStateOf("Preparando inventario…")
        private set
    var scanDone by mutableStateOf(0)
        private set
    var scanTotal by mutableStateOf(0)
        private set
    var scanError by mutableStateOf<String?>(null)
        private set

    var selected by mutableStateOf<Set<String>>(emptySet())
    private var selectionInitialized = false

    var backupRunning by mutableStateOf(false)
        private set
    var backupCancelling by mutableStateOf(false)
        private set
    var backupPhase by mutableStateOf("")
        private set
    var backupDone by mutableStateOf(0L)
        private set
    var backupTotal by mutableStateOf(0L)
        private set
    var backupMessage by mutableStateOf<String?>(null)
        private set
    var backupError by mutableStateOf<String?>(null)
        private set

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else mainHandler.post { block() }
    }

    fun ensureScan(context: Context, force: Boolean = false) {
        if (loading) return
        if (!force && snapshot != null) return
        if (backupRunning) {
            scanError = "Esperá a que termine o cancelá el respaldo antes de actualizar tamaños."
            return
        }

        loading = true
        scanError = null
        scanPhase = "Preparando inventario…"
        scanDone = 0
        scanTotal = 0
        val app = context.applicationContext

        workerScope.launch {
            val result = runCatching {
                NativeStorageManager.scan(
                    app,
                    NativeStorageManager.Progress { phase, done, total ->
                        onMain {
                            scanPhase = phase
                            scanDone = done.coerceAtLeast(0)
                            scanTotal = total.coerceAtLeast(0)
                        }
                    },
                )
            }
            onMain {
                result.onSuccess { snap ->
                    snapshot = snap
                    val available = snap.items.filter { it.selectable }.map { it.id }.toSet()
                    selected = if (!selectionInitialized) {
                        selectionInitialized = true
                        snap.items
                            .filter { it.selectable && (it.id == "home" || it.id.startsWith("proot:")) }
                            .map { it.id }
                            .toSet()
                    } else {
                        selected.intersect(available)
                    }
                }.onFailure { t ->
                    scanError = t.message ?: t.javaClass.simpleName
                }
                loading = false
            }
        }
    }

    fun startBackup(context: Context) {
        if (backupRunning) return
        val snap = snapshot
        if (snap == null) {
            backupError = "Esperá a que termine el análisis de almacenamiento."
            return
        }
        val chosen = snap.items.filter { it.selectable && it.id in selected }
        if (chosen.isEmpty()) {
            backupError = "Seleccioná al menos un componente para respaldar."
            return
        }

        cancelRequested.set(false)
        backupRunning = true
        backupCancelling = false
        backupError = null
        backupMessage = null
        backupPhase = "Preparando respaldo"
        backupDone = 0L
        backupTotal = chosen.sumOf { it.bytes }
        val app = context.applicationContext

        workerScope.launch {
            val result = runCatching {
                NativeBackupManager.createBackup(
                    app,
                    chosen,
                    NativeBackupManager.Progress { phase, done, total ->
                        onMain {
                            backupPhase = phase
                            backupDone = done.coerceAtLeast(0L)
                            backupTotal = total.coerceAtLeast(0L)
                        }
                    },
                    NativeBackupManager.Cancellation { cancelRequested.get() },
                )
            }

            onMain {
                result.onSuccess { backup ->
                    backupMessage =
                        "Respaldo creado: ${backup.file.name} · " +
                            NativeStorageManager.formatBytes(backup.archiveBytes) +
                            "\nDescargas/NewTermux/Backups"
                    Toast.makeText(app, "Respaldo terminado", Toast.LENGTH_SHORT).show()
                }.onFailure { t ->
                    if (t is CancellationException) {
                        backupMessage = "Respaldo cancelado. No quedó ningún archivo parcial."
                    } else {
                        backupError = "No se pudo crear el respaldo: ${t.message ?: t.javaClass.simpleName}"
                    }
                }
                backupRunning = false
                backupCancelling = false
                cancelRequested.set(false)
            }
        }
    }

    fun cancelBackup() {
        if (!backupRunning || backupCancelling) return
        backupCancelling = true
        backupPhase = "Cancelando respaldo…"
        cancelRequested.set(true)
    }

    fun clearBackupResult() {
        backupMessage = null
        backupError = null
    }
}

@Composable
private fun NativeBackupProgressPanel(compact: Boolean = false) {
    val phase = NativeStorageSession.backupPhase.ifBlank { "Respaldando…" }
    val done = NativeStorageSession.backupDone
    val total = NativeStorageSession.backupTotal
    val cancelling = NativeStorageSession.backupCancelling

    Column(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = 16.dp,
            vertical = if (compact) 8.dp else 12.dp,
        ),
    ) {
        Text(
            if (cancelling) "Cancelando respaldo…" else phase,
            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
        )
        if (total > 0L) {
            val fraction = (done.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                "${NativeStorageManager.formatBytes(done)} / ${NativeStorageManager.formatBytes(total)} · " +
                    "${(fraction * 100).toInt()}%",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        TextButton(
            onClick = { NativeStorageSession.cancelBackup() },
            enabled = !cancelling,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        ) {
            Text(if (cancelling) "Cancelando…" else "Cancelar respaldo")
        }
    }
}

@Composable
private fun StorageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val snapshot = NativeStorageSession.snapshot
    val loading = NativeStorageSession.loading
    val scanPhase = NativeStorageSession.scanPhase
    val scanDone = NativeStorageSession.scanDone
    val scanTotal = NativeStorageSession.scanTotal
    val selected = NativeStorageSession.selected
    val backupRunning = NativeStorageSession.backupRunning

    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var operationPhase by remember { mutableStateOf("") }
    var operationDone by remember { mutableStateOf(0L) }
    var operationTotal by remember { mutableStateOf(0L) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoreInfo by remember { mutableStateOf<NativeBackupManager.BackupInfo?>(null) }
    var restoreSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var pendingRestore by remember {
        mutableStateOf(NativeBackupManager.hasPendingRestore(context.applicationContext))
    }

    val uiBusy = busy || backupRunning

    fun postProgress(phase: String, done: Long, total: Long) {
        activity?.runOnUiThread {
            operationPhase = phase
            operationDone = done.coerceAtLeast(0L)
            operationTotal = total.coerceAtLeast(0L)
        }
    }

    fun refresh() {
        NativeStorageSession.ensureScan(context.applicationContext, force = true)
    }

    fun startBackup() {
        error = null
        resultMessage = null
        NativeStorageSession.startBackup(context.applicationContext)
    }

    fun inspectRestore(uri: Uri) {
        busy = true
        error = null
        resultMessage = null
        operationPhase = "Leyendo respaldo"
        operationDone = 0L
        operationTotal = 0L

        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    NativeBackupManager.inspect(context.applicationContext, uri)
                }
            }
            result.onSuccess { info ->
                restoreUri = uri
                restoreInfo = info
                restoreSelected = info.components
                    .filter { it.restorable }
                    .map { it.id }
                    .toSet()
                showRestoreDialog = true
            }.onFailure { t ->
                error = "No se pudo abrir el respaldo: ${t.message ?: t.javaClass.simpleName}"
            }
            busy = false
        }
    }

    fun prepareRestore() {
        val uri = restoreUri ?: return
        val chosen = restoreSelected
        if (chosen.isEmpty()) return

        showRestoreDialog = false
        busy = true
        error = null
        resultMessage = null
        operationPhase = "Verificando respaldo"
        operationDone = 0L
        operationTotal = restoreInfo?.components
            ?.filter { it.id in chosen }
            ?.sumOf { it.bytes } ?: 0L

        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    NativeBackupManager.prepareRestore(
                        context.applicationContext,
                        uri,
                        chosen,
                        NativeBackupManager.Progress { phase, done, total ->
                            postProgress(phase, done, total)
                        },
                    )
                }
            }
            result.onSuccess {
                pendingRestore = true
                resultMessage =
                    "Restauración verificada y preparada. No se modificó la sesión actual. " +
                    "Se aplicará en el próximo arranque completo de NewTermux."
                Toast.makeText(context, "Restauración preparada", Toast.LENGTH_SHORT).show()
            }.onFailure { t ->
                error = "No se pudo preparar la restauración: ${t.message ?: t.javaClass.simpleName}"
            }
            busy = false
        }
    }

    val restorePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) inspectRestore(uri)
    }

    LaunchedEffect(Unit) {
        NativeStorageSession.ensureScan(context.applicationContext)
    }

    SettingsScaffold("Almacenamiento y respaldo", onBack) { mod ->
        Column(modifier = mod) {
            if (pendingRestore) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(
                        "Restauración preparada",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Está verificada y esperando un arranque limpio. Nada se aplicará sobre una sesión en ejecución.",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { showExitConfirm = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Cerrar y aplicar")
                        }
                        TextButton(
                            onClick = {
                                NativeBackupManager.discardPendingRestore(context.applicationContext)
                                pendingRestore = false
                                resultMessage = "Restauración preparada descartada."
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Descartar")
                        }
                    }
                }
                HorizontalDivider()
            }

            if (backupRunning) {
                NativeBackupProgressPanel()
                HorizontalDivider()
            } else {
                NativeStorageSession.backupMessage?.let {
                    Text(
                        it,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                NativeStorageSession.backupError?.let {
                    Text(
                        it,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            val snap = snapshot
            if (snap != null) {
                val used = (snap.totalBytes - snap.freeBytes).coerceAtLeast(0L)
                val usedFraction = if (snap.totalBytes > 0)
                    (used.toFloat() / snap.totalBytes.toFloat()).coerceIn(0f, 1f)
                else 0f

                val identified = snap.measuredBytes.coerceAtLeast(0L).coerceAtMost(used)
                val otherDevice = (used - identified).coerceAtLeast(0L)

                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Dispositivo: ${NativeStorageManager.formatBytes(used)} usados · " +
                            "${NativeStorageManager.formatBytes(snap.freeBytes)} libres",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    LinearProgressIndicator(
                        progress = { usedFraction },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Text(
                        "NewTermux identificado: ${NativeStorageManager.formatBytes(identified)} · " +
                            "Otros archivos del dispositivo: ${NativeStorageManager.formatBytes(otherDevice)}",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Los tamaños de cada componente son espacio realmente asignado en disco. " +
                            "HOME, PRoot, modelos, cachés, staging TBM y respaldos se separan para no contarlos dos veces.",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider()

                val maxItem = snap.items.maxOfOrNull { it.allocatedBytes }?.coerceAtLeast(1L) ?: 1L
                snap.items.forEach { item ->
                    val checked = item.id in selected
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.selectable) {
                            Checkbox(
                                checked = checked,
                                enabled = !uiBusy,
                                onCheckedChange = { value ->
                                    NativeStorageSession.selected =
                                        if (value) selected + item.id else selected - item.id
                                },
                            )
                        } else {
                            Spacer(Modifier.size(48.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    item.label,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    NativeStorageManager.formatBytes(item.allocatedBytes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            LinearProgressIndicator(
                                progress = {
                                    (item.allocatedBytes.toFloat() / maxItem.toFloat()).coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            )
                            if (item.detail.isNotBlank()) {
                                Text(
                                    item.detail,
                                    modifier = Modifier.padding(top = 4.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }

                val selectedBytes = snap.items
                    .filter { it.id in selected }
                    .sumOf { it.bytes }
                val selectedDiskBytes = snap.items
                    .filter { it.id in selected }
                    .sumOf { it.allocatedBytes }

                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "Seleccionado para respaldo: ${NativeStorageManager.formatBytes(selectedBytes)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${NativeStorageManager.formatBytes(selectedDiskBytes)} ocupados actualmente en disco",
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = {
                                NativeStorageSession.selected =
                                    snap.items.filter { it.selectable }.map { it.id }.toSet()
                            },
                            enabled = !uiBusy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Todo")
                        }
                        TextButton(
                            onClick = { NativeStorageSession.selected = emptySet() },
                            enabled = !uiBusy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Ninguno")
                        }
                    }

                    if (backupRunning) {
                        NativeBackupProgressPanel(compact = true)
                    } else {
                        Button(
                            onClick = { startBackup() },
                            enabled = selected.isNotEmpty() && !uiBusy,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Text("Respaldar seleccionados")
                        }
                    }

                    Button(
                        onClick = {
                            restorePicker.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/octet-stream",
                                    "application/x-zip-compressed",
                                    "*/*",
                                ),
                            )
                        },
                        enabled = !uiBusy,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text("Restaurar respaldo")
                    }

                    TextButton(
                        onClick = { refresh() },
                        enabled = !uiBusy,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        Text("Actualizar tamaños")
                    }
                }
            }

            if (busy) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        operationPhase.ifBlank { "Trabajando…" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (operationTotal > 0L) {
                        val fraction =
                            (operationDone.toDouble() / operationTotal.toDouble())
                                .coerceIn(0.0, 1.0)
                                .toFloat()
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Text(
                            "${NativeStorageManager.formatBytes(operationDone)} / " +
                                NativeStorageManager.formatBytes(operationTotal),
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
            } else if (loading) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text(
                        scanPhase,
                        modifier = Modifier.padding(top = 14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (scanTotal > 0) {
                        val fraction =
                            (scanDone.toFloat() / scanTotal.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                        Text(
                            "$scanDone / $scanTotal etapas",
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "El cálculo se ejecuta en segundo plano. Podés volver atrás mientras termina.",
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            resultMessage?.let {
                Text(
                    it,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            error?.let {
                Text(
                    it,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            NativeStorageSession.scanError?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showRestoreDialog) {
        val info = restoreInfo
        if (info != null) {
            AlertDialog(
                onDismissRequest = { if (!uiBusy) showRestoreDialog = false },
                title = { Text("Restaurar respaldo") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            "Respaldo: ${info.createdAt}\n" +
                                "NewTermux ${info.appVersion} · ${info.abi}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Elegí qué querés restaurar. Primero se verificará y preparará en un área privada; " +
                                "los datos activos no se modifican todavía.",
                            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        info.components.forEach { component ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = component.id in restoreSelected,
                                    enabled = component.restorable && !uiBusy,
                                    onCheckedChange = { value ->
                                        restoreSelected = if (value)
                                            restoreSelected + component.id
                                        else
                                            restoreSelected - component.id
                                    },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(component.label)
                                    Text(
                                        NativeStorageManager.formatBytes(component.bytes) +
                                            if (component.restorable) "" else " · sólo respaldo",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { prepareRestore() },
                        enabled = restoreSelected.isNotEmpty() && !uiBusy,
                    ) {
                        Text("Preparar restauración")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showRestoreDialog = false },
                        enabled = !uiBusy,
                    ) {
                        Text("Cancelar")
                    }
                },
            )
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Cerrar NewTermux y aplicar") },
            text = {
                Text(
                    "Se cerrarán todas las sesiones y procesos de NewTermux. " +
                        "La restauración preparada se aplicará antes de iniciar shells o PRoot " +
                        "cuando vuelvas a abrir la aplicación.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirm = false
                        val stop = Intent(context, TermuxService::class.java)
                            .setAction(TERMUX_SERVICE.ACTION_STOP_SERVICE)
                        runCatching { context.startService(stop) }
                        Handler(Looper.getMainLooper()).postDelayed({
                            runCatching { activity?.finishAndRemoveTask() }
                            Process.killProcess(Process.myPid())
                        }, 1000L)
                    },
                ) {
                    Text("Cerrar sesiones y salir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

// ---------------------------------------------------------------- Features

@Composable
private fun FeaturesScreen(activity: Activity, onBack: () -> Unit) {
    val context = LocalContext.current
    var showScriptEditor by remember { mutableStateOf(false) }
    var showRestartWarning by remember { mutableStateOf(false) }

    val zshInstalled = remember { File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, "bin/zsh").exists() }

    SettingsScaffold(context.getString(R.string.nt_l10n_features), onBack) { mod ->
        Column(modifier = mod) {
            CategoryHeader(context.getString(R.string.nt_l10n_keyboard))
            NtSwitch(context, NewTermuxSettings.KEY_KEYBOARD_SUGGESTIONS, context.getString(R.string.nt_l10n_keyboard_suggestions), context.getString(R.string.nt_l10n_keyboard_suggestions_summary))
            NtSwitch(context, NewTermuxSettings.KEY_AUTOCORRECT, context.getString(R.string.nt_l10n_autocorrect), context.getString(R.string.nt_l10n_autocorrect_summary))
            NtSwitch(context, NewTermuxSettings.KEY_URL_DETECTION_ENABLED, context.getString(R.string.nt_l10n_url_detection), context.getString(R.string.nt_l10n_url_detection_summary))
            NtSwitch(context, NewTermuxSettings.KEY_EXTRA_KEYS_VISIBLE, context.getString(R.string.nt_l10n_extra_keys), context.getString(R.string.nt_l10n_extra_keys_summary))
            NtSwitch(context, NewTermuxSettings.KEY_EXTRA_KEYS_IN_DRAWER, context.getString(R.string.nt_l10n_extra_keys_drawer), context.getString(R.string.nt_l10n_extra_keys_drawer_summary))

            CategoryHeader(context.getString(R.string.nt_l10n_quick_bar))
            NtSwitch(context, NewTermuxSettings.KEY_SHOW_AC_BUTTON, context.getString(R.string.nt_l10n_ac_button), context.getString(R.string.nt_l10n_ac_button_summary))
            NtSwitch(context, NewTermuxSettings.KEY_SHOW_STT_BUTTON, context.getString(R.string.nt_l10n_microphone), context.getString(R.string.nt_l10n_microphone_summary))
            NtSwitch(context, NewTermuxSettings.KEY_SHOW_PACKAGES_BUTTON, context.getString(R.string.nt_l10n_quick_packages), context.getString(R.string.nt_l10n_quick_packages_summary))
            NtSwitch(context, NewTermuxSettings.KEY_SHOW_CLEAR_BUTTON, context.getString(R.string.nt_l10n_clear), context.getString(R.string.nt_l10n_clear_summary))
            NavRow(context.getString(R.string.nt_l10n_quick_more), context.getString(R.string.nt_l10n_quick_more_summary), enabled = false) {}

            CategoryHeader(context.getString(R.string.nt_l10n_session_tabs))
            NtSwitch(context, NewTermuxSettings.KEY_SESSION_TABS, context.getString(R.string.nt_l10n_show_session_tabs), context.getString(R.string.nt_l10n_show_session_tabs_summary))
            NtSwitch(context, NewTermuxSettings.KEY_SESSION_RENAME_ENABLED, context.getString(R.string.nt_l10n_session_rename), context.getString(R.string.nt_l10n_session_rename_summary))

            CategoryHeader(context.getString(R.string.nt_l10n_startup))
            NtSwitch(context, NewTermuxSettings.KEY_STARTUP_SCRIPT_ENABLED, context.getString(R.string.nt_l10n_startup_script), context.getString(R.string.nt_l10n_startup_script_summary))
            NavRow(context.getString(R.string.nt_l10n_edit_startup), context.getString(R.string.nt_l10n_edit_startup_summary)) { showScriptEditor = true }

            CategoryHeader(context.getString(R.string.nt_l10n_shell))
            if (zshInstalled) {
                NavRow("Zsh", context.getString(R.string.nt_l10n_zsh_installed), enabled = false) {}
            } else {
                NavRow(context.getString(R.string.nt_l10n_install_zsh), context.getString(R.string.nt_l10n_install_zsh_summary)) {
                    NewTermuxSettings.setPendingCommand(context, "pkg install zsh\n")
                    activity.finish()
                }
            }
            ZshPluginsSwitch(context, zshInstalled, onChanged = { showRestartWarning = true })

            CategoryHeader(context.getString(R.string.nt_l10n_drawer))
            NtSwitch(context, NewTermuxSettings.KEY_SHOW_DRAWER_EXPORT_SCRIPT, context.getString(R.string.nt_l10n_export_screen_script), context.getString(R.string.nt_l10n_export_screen_script_summary))
            NtSwitch(context, NewTermuxSettings.KEY_SHOW_DRAWER_PKG_UPDATE, context.getString(R.string.nt_l10n_pkg_update), context.getString(R.string.nt_l10n_pkg_update_summary))
            NtSwitch(context, NewTermuxSettings.KEY_SHOW_DRAWER_CMD_BUTTONS, context.getString(R.string.nt_l10n_drawer_commands), context.getString(R.string.nt_l10n_drawer_commands_summary))

            CategoryHeader(context.getString(R.string.nt_l10n_background))
            KeepAliveSwitch(activity)

            CategoryHeader(context.getString(R.string.nt_l10n_permissions))
            NavRow(context.getString(R.string.nt_l10n_storage_permission), context.getString(R.string.nt_l10n_storage_permission_summary)) {
                (activity as? TermuxActivity)?.requestStoragePermission(false)
            }
            Spacer(Modifier.size(16.dp))
        }
    }

    if (showScriptEditor) {
        StartupScriptEditorDialog(context, onDismiss = { showScriptEditor = false })
    }
    if (showRestartWarning) {
        AlertDialog(
            onDismissRequest = { showRestartWarning = false },
            title = { Text(context.getString(R.string.nt_l10n_restart_required)) },
            text = { Text(context.getString(R.string.nt_l10n_restart_required_summary)) },
            confirmButton = { TextButton(onClick = { showRestartWarning = false }) { Text(context.getString(R.string.nt_l10n_ok)) } },
        )
    }
}

@Composable
private fun ZshPluginsSwitch(context: Context, zshInstalled: Boolean, onChanged: () -> Unit) {
    var checked by remember { mutableStateOf(NewTermuxSettings.isZshPluginsEnabled(context)) }
    SwitchRow(
        title = context.getString(R.string.nt_l10n_shell_enhancements),
        summary = if (zshInstalled) context.getString(R.string.nt_l10n_shell_enhancements_summary) else context.getString(R.string.nt_l10n_shell_enhancements_unavailable),
        checked = checked,
        enabled = zshInstalled,
    ) {
        checked = it
        NewTermuxSettings.set(context, NewTermuxSettings.KEY_ZSH_PLUGINS, it)
        Thread { TermuxInstaller.setZshPlugins(context, it) }.start()
        onChanged()
    }
}

@Composable
private fun StartupScriptEditorDialog(context: Context, onDismiss: () -> Unit) {
    val scriptFile = remember { File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".termux/startup-script.sh") }
    var text by remember { mutableStateOf(if (scriptFile.exists()) runCatching { scriptFile.readText() }.getOrDefault("") else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.nt_l10n_edit_startup)) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    scriptFile.parentFile?.mkdirs()
                    scriptFile.writeText(text)
                    Toast.makeText(context, context.getString(R.string.nt_l10n_startup_saved), Toast.LENGTH_SHORT).show()
                    onDismiss()
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.nt_l10n_save_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }) { Text(context.getString(R.string.nt_l10n_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(context.getString(R.string.nt_l10n_cancel)) } },
    )
}

// ---------------------------------------------------------------- Text Expansion

@Composable
private fun TextExpansionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(NewTermuxSettings.isTextExpansionEnabled(context)) }
    val items = remember { mutableStateListOf<TextExpansionStore.TextExpansion>().apply { addAll(TextExpansionStore.load(context)) } }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    fun persist() = TextExpansionStore.save(context, items.toMutableList())

    SettingsScaffold(context.getString(R.string.nt_l10n_expansions), onBack) { mod ->
        Column(modifier = mod) {
            SwitchRow(context.getString(R.string.nt_l10n_enable_expansions), context.getString(R.string.nt_l10n_expansions_summary), enabled) {
                enabled = it
                NewTermuxSettings.set(context, NewTermuxSettings.KEY_TEXT_EXPANSION_ENABLED, it)
            }
            HorizontalDivider()
            NavRow(context.getString(R.string.nt_l10n_add_new), context.getString(R.string.nt_l10n_add_new_summary)) { editIndex = null; showEditor = true }
            if (items.isEmpty()) {
                Text(
                    context.getString(R.string.nt_l10n_no_expansions),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                items.forEachIndexed { i, exp ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { editIndex = i; showEditor = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${exp.trigger}  →  ${exp.expansion}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { items.removeAt(i); persist() }) { Text(context.getString(R.string.nt_l10n_delete)) }
                    }
                }
            }
        }
    }

    if (showEditor) {
        val editing = editIndex?.let { items.getOrNull(it) }
        ExpansionEditorDialog(
            initTrigger = editing?.trigger ?: "",
            initExpansion = editing?.expansion ?: "",
            onDismiss = { showEditor = false },
            onSave = { trigger, expansion ->
                val idx = editIndex
                if (idx != null && idx < items.size) {
                    items[idx].trigger = trigger
                    items[idx].expansion = expansion
                    items[idx] = items[idx] // trigger recomposition
                } else {
                    val e = TextExpansionStore.TextExpansion()
                    e.trigger = trigger; e.expansion = expansion
                    items.add(e)
                }
                persist()
                showEditor = false
            },
        )
    }
}

@Composable
private fun ExpansionEditorDialog(
    initTrigger: String,
    initExpansion: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    val context = LocalContext.current
    var trigger by remember { mutableStateOf(initTrigger) }
    var expansion by remember { mutableStateOf(initExpansion) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initTrigger.isNotEmpty() || initExpansion.isNotEmpty()) context.getString(R.string.nt_l10n_edit_expansion) else context.getString(R.string.nt_l10n_add_expansion)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(trigger, { trigger = it }, label = { Text(context.getString(R.string.nt_l10n_trigger)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(expansion, { expansion = it }, label = { Text(context.getString(R.string.nt_l10n_expansion)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (trigger.trim().isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.nt_l10n_trigger_empty), Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                onSave(trigger.trim(), expansion)
            }) { Text(context.getString(R.string.nt_l10n_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(context.getString(R.string.nt_l10n_cancel)) } },
    )
}

// ---------------------------------------------------------------- Backup

@Composable
private fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoreFull by remember { mutableStateOf<Boolean?>(null) }

    val basicSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
        if (uri != null) scope.launch {
            busy = context.getString(R.string.nt_l10n_backup_home_busy)
            val err = withContext(Dispatchers.IO) { runBackup(context, uri, false) }
            busy = null
            toast(context, if (err == null) context.getString(R.string.nt_l10n_backup_complete) else context.getString(R.string.nt_l10n_backup_failed, err))
        }
    }
    val fullSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
        if (uri != null) scope.launch {
            busy = context.getString(R.string.nt_l10n_backup_full_busy)
            val err = withContext(Dispatchers.IO) { runBackup(context, uri, true) }
            busy = null
            toast(context, if (err == null) context.getString(R.string.nt_l10n_backup_complete) else context.getString(R.string.nt_l10n_backup_failed, err))
        }
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { restoreUri = uri; restoreFull = null }
    }

    SettingsScaffold(context.getString(R.string.nt_l10n_backup_restore), onBack) { mod ->
        Column(modifier = mod) {
            NavRow(context.getString(R.string.nt_l10n_backup_home), context.getString(R.string.nt_l10n_backup_home_summary)) { basicSaver.launch("termux-home-backup.tar.gz") }
            NavRow(context.getString(R.string.nt_l10n_backup_full), context.getString(R.string.nt_l10n_backup_full_summary)) { fullSaver.launch("termux-full-backup.tar.gz") }
            HorizontalDivider()
            NavRow(context.getString(R.string.nt_l10n_restore_from), context.getString(R.string.nt_l10n_restore_from_summary)) { restorePicker.launch(arrayOf("*/*")) }
        }
    }

    busy?.let { msg ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.size(16.dp))
                    Text(msg)
                }
            },
        )
    }

    // Restore: choose type
    if (restoreUri != null && restoreFull == null) {
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text(context.getString(R.string.nt_l10n_backup_type)) },
            text = { Text(context.getString(R.string.nt_l10n_backup_type_summary)) },
            confirmButton = { TextButton(onClick = { restoreFull = true }) { Text(context.getString(R.string.nt_l10n_full)) } },
            dismissButton = { TextButton(onClick = { restoreFull = false }) { Text(context.getString(R.string.nt_l10n_basic)) } },
        )
    }
    // Restore: confirm
    if (restoreUri != null && restoreFull != null) {
        val uri = restoreUri!!
        val full = restoreFull!!
        AlertDialog(
            onDismissRequest = { restoreUri = null; restoreFull = null },
            title = { Text(context.getString(R.string.nt_l10n_restore_termux)) },
            text = { Text(if (full) context.getString(R.string.nt_l10n_restore_confirm_full) else context.getString(R.string.nt_l10n_restore_confirm_home)) },
            confirmButton = {
                TextButton(onClick = {
                    restoreUri = null; restoreFull = null
                    scope.launch {
                        busy = context.getString(R.string.nt_l10n_restoring)
                        val err = withContext(Dispatchers.IO) { runRestore(context, uri, full) }
                        busy = null
                        toast(context, if (err == null) context.getString(R.string.nt_l10n_restore_complete) else context.getString(R.string.nt_l10n_restore_failed, err))
                    }
                }) { Text(context.getString(R.string.nt_l10n_restore)) }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null; restoreFull = null }) { Text(context.getString(R.string.nt_l10n_cancel)) } },
        )
    }
}

// ---------------------------------------------------------------- Termux (upstream)

@Composable
private fun TermuxScreen(onBack: () -> Unit, onNav: (Route) -> Unit) {
    val context = LocalContext.current
    SettingsScaffold("Termux", onBack) { mod ->
        Column(modifier = mod) {
            NavRow(context.getString(R.string.nt_l10n_debugging), context.getString(R.string.nt_l10n_debugging_summary)) { onNav(Route.DEBUGGING) }
            NavRow(context.getString(R.string.nt_l10n_terminal_io), context.getString(R.string.nt_l10n_terminal_io_summary)) { onNav(Route.TERMINAL_IO) }
            NavRow(context.getString(R.string.nt_l10n_terminal_view), context.getString(R.string.nt_l10n_terminal_margins)) { onNav(Route.TERMINAL_VIEW) }
        }
    }
}

@Composable
private fun TerminalIOScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { TermuxAppSharedPreferences.build(context, true) }
    SettingsScaffold(context.getString(R.string.nt_l10n_terminal_io), onBack) { mod ->
        Column(modifier = mod) {
            if (prefs == null) { Text(context.getString(R.string.nt_l10n_unavailable), modifier = Modifier.padding(16.dp)); return@Column }
            CategoryHeader(context.getString(R.string.nt_l10n_keyboard))
            var soft by remember { mutableStateOf(prefs.isSoftKeyboardEnabled) }
            SwitchRow(context.getString(R.string.nt_l10n_soft_keyboard), context.getString(R.string.nt_l10n_soft_keyboard_summary), soft) { soft = it; prefs.setSoftKeyboardEnabled(it) }
            var softNoHw by remember { mutableStateOf(prefs.isSoftKeyboardEnabledOnlyIfNoHardware) }
            SwitchRow(context.getString(R.string.nt_l10n_no_hardware_keyboard), context.getString(R.string.nt_l10n_no_hardware_keyboard_summary), softNoHw) { softNoHw = it; prefs.setSoftKeyboardEnabledOnlyIfNoHardware(it) }
        }
    }
}

@Composable
private fun TerminalViewScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { TermuxAppSharedPreferences.build(context, true) }
    SettingsScaffold(context.getString(R.string.nt_l10n_terminal_view), onBack) { mod ->
        Column(modifier = mod) {
            if (prefs == null) { Text(context.getString(R.string.nt_l10n_unavailable), modifier = Modifier.padding(16.dp)); return@Column }
            CategoryHeader(context.getString(R.string.nt_l10n_view))
            var margin by remember { mutableStateOf(prefs.isTerminalMarginAdjustmentEnabled) }
            SwitchRow(context.getString(R.string.nt_l10n_terminal_margins), context.getString(R.string.nt_l10n_terminal_margins_summary), margin) { margin = it; prefs.setTerminalMarginAdjustment(it) }
        }
    }
}

@Composable
private fun DebuggingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { TermuxAppSharedPreferences.build(context, true) }
    SettingsScaffold(context.getString(R.string.nt_l10n_debugging), onBack) { mod ->
        Column(modifier = mod) {
            if (prefs == null) { Text(context.getString(R.string.nt_l10n_unavailable), modifier = Modifier.padding(16.dp)); return@Column }
            CategoryHeader(context.getString(R.string.nt_l10n_logging))
            LogLevelRow(context, prefs.logLevel) { prefs.setLogLevel(context, it) }
            var keyLog by remember { mutableStateOf(prefs.isTerminalViewKeyLoggingEnabled) }
            SwitchRow(context.getString(R.string.nt_l10n_key_logging), context.getString(R.string.nt_l10n_key_logging_summary), keyLog) { keyLog = it; prefs.setTerminalViewKeyLoggingEnabled(it) }
            var pluginErr by remember { mutableStateOf(prefs.arePluginErrorNotificationsEnabled(false)) }
            SwitchRow(context.getString(R.string.nt_l10n_plugin_errors), null, pluginErr) { pluginErr = it; prefs.setPluginErrorNotificationsEnabled(it) }
            var crash by remember { mutableStateOf(prefs.areCrashReportNotificationsEnabled(false)) }
            SwitchRow(context.getString(R.string.nt_l10n_crash_reports), null, crash) { crash = it; prefs.setCrashReportNotificationsEnabled(it) }
        }
    }
}

// ---------------------------------------------------------------- Plugins

private enum class Plugin { API, FLOAT, TASKER, WIDGET }

@Composable
private fun PluginScreen(title: String, plugin: Plugin, onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsScaffold(title, onBack) { mod ->
        Column(modifier = mod) {
            CategoryHeader(context.getString(R.string.nt_l10n_logging))
            when (plugin) {
                Plugin.API -> {
                    val p = remember { TermuxAPIAppSharedPreferences.build(context, true) }
                    if (p != null) LogLevelRow(context, p.getLogLevel(true)) { p.setLogLevel(context, it, true) }
                }
                Plugin.FLOAT -> {
                    val p = remember { TermuxFloatAppSharedPreferences.build(context, true) }
                    if (p != null) {
                        LogLevelRow(context, p.getLogLevel(true)) { p.setLogLevel(context, it, true) }
                        var keyLog by remember { mutableStateOf(p.isTerminalViewKeyLoggingEnabled(true)) }
                        SwitchRow(context.getString(R.string.nt_l10n_key_logging), null, keyLog) { keyLog = it; p.setTerminalViewKeyLoggingEnabled(it, true) }
                    }
                }
                Plugin.TASKER -> {
                    val p = remember { TermuxTaskerAppSharedPreferences.build(context, true) }
                    if (p != null) LogLevelRow(context, p.getLogLevel(true)) { p.setLogLevel(context, it, true) }
                }
                Plugin.WIDGET -> {
                    val p = remember { TermuxWidgetAppSharedPreferences.build(context, true) }
                    if (p != null) LogLevelRow(context, p.getLogLevel(true)) { p.setLogLevel(context, it, true) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- helpers

private fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

private fun isDonateVisible(context: Context): Boolean {
    val digest = PackageUtils.getSigningCertificateSHA256DigestForPackage(context) ?: return true
    val apkRelease = TermuxUtils.getAPKRelease(digest)
    return !(apkRelease == null || apkRelease == TermuxConstants.APK_RELEASE_GOOGLE_PLAYSTORE_SIGNING_CERTIFICATE_SHA256_DIGEST)
}

private fun openAbout(context: Context) {
    val about = StringBuilder()
    about.append(TermuxUtils.getAppInfoMarkdownString(context, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES))
    about.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(context, true))
    about.append("\n\n").append(TermuxUtils.getImportantLinksMarkdownString(context))

    val userActionName = UserAction.ABOUT.getName()
    val reportInfo = ReportInfo(userActionName, TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME, context.getString(R.string.nt_l10n_about))
    reportInfo.setReportString(about.toString())
    reportInfo.setReportSaveFileLabelAndPath(
        userActionName,
        Environment.getExternalStorageDirectory().toString() + "/" +
            FileUtils.sanitizeFileName(TermuxConstants.TERMUX_APP_NAME + "-" + userActionName + ".log", true, true),
    )
    ReportActivity.startReportActivity(context, reportInfo)
}

private fun runBackup(context: Context, uri: Uri, full: Boolean): String? {
    return try {
        val cmd = if (full) arrayOf(
            "/data/data/com.termux/files/usr/bin/tar", "-zcf", "-",
            "-C", "/data/data/com.termux/files", "./home", "./usr",
        ) else arrayOf(
            "/data/data/com.termux/files/usr/bin/tar", "-zcf", "-",
            "-C", "/data/data/com.termux/files/home", ".",
        )
        val p = Runtime.getRuntime().exec(cmd)
        p.inputStream.use { input ->
            context.contentResolver.openOutputStream(uri).use { out ->
                if (out == null) return context.getString(R.string.nt_l10n_output_unavailable)
                input.copyTo(out)
            }
        }
        val errText = readStream(p.errorStream)
        val exit = p.waitFor()
        if (exit != 0) (errText.ifEmpty { context.getString(R.string.nt_l10n_tar_exit, exit) }) else null
    } catch (e: Exception) {
        e.message ?: context.getString(R.string.nt_l10n_generic_error)
    }
}

private fun runRestore(context: Context, fileUri: Uri, full: Boolean): String? {
    return try {
        val filePath: String = if (fileUri.scheme == "content") {
            val tmp = File(context.cacheDir, "restore_tmp.tar.gz")
            context.contentResolver.openInputStream(fileUri).use { input ->
                if (input == null) return context.getString(R.string.nt_l10n_input_unavailable)
                FileOutputStream(tmp).use { out -> input.copyTo(out) }
            }
            tmp.absolutePath
        } else {
            fileUri.path ?: return context.getString(R.string.nt_l10n_invalid_path)
        }
        val p = if (full) Runtime.getRuntime().exec(arrayOf(
            "/data/data/com.termux/files/usr/bin/tar", "-zxvf", filePath,
            "-C", "/data/data/com.termux/files", "--recursive-unlink", "--preserve-permissions",
        )) else Runtime.getRuntime().exec(arrayOf(
            "/data/data/com.termux/files/usr/bin/tar", "-zxvf", filePath,
            "-C", "/data/data/com.termux/files/home",
        ))
        val errText = readStream(p.errorStream)
        val exit = p.waitFor()
        if (exit != 0) errText else null
    } catch (e: Exception) {
        e.message ?: context.getString(R.string.nt_l10n_generic_error)
    }
}

private fun readStream(input: InputStream): String {
    val baos = ByteArrayOutputStream()
    val buf = ByteArray(4096)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        baos.write(buf, 0, n)
    }
    return baos.toString()
}
