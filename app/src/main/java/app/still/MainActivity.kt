package app.still

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.ui.*
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: TrackerViewModel = viewModel()
            val state by model.state.collectAsState()
            StillTheme(state.preferences.darkMode) {
                val background = MaterialTheme.colorScheme.background.toArgb()
                SideEffect {
                    val style = if (state.preferences.darkMode) SystemBarStyle.dark(background) else SystemBarStyle.light(background, background)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                StillApp(model, state)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StillApp(model: TrackerViewModel, state: TrackerState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var goalEditor by rememberSaveable { mutableStateOf(false) }
    val screenStates = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var notificationsAllowed by remember { mutableStateOf(Reminders.allowed(context)) }
    var pendingReminderHour by rememberSaveable { mutableIntStateOf(8) }
    var pendingReminderMinute by rememberSaveable { mutableIntStateOf(0) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAllowed = Reminders.allowed(context)
                model.refreshReminderSchedule()
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = Reminders.allowed(context)
        if (granted) model.saveReminder(ReminderSettings(true, pendingReminderHour, pendingReminderMinute))
        else model.notifyUser("Notifications are off. You can enable them later in Android Settings.")
    }
    val setReminder: (ReminderSettings) -> Unit = { value ->
        if (value.enabled && Build.VERSION.SDK_INT >= 33 && !Reminders.allowed(context)) {
            pendingReminderHour = value.hour
            pendingReminderMinute = value.minute
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            model.saveReminder(value)
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) model.export(uri)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.prepareImport(uri)
    }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) model.createBackup(uri)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.prepareRestore(uri)
    }
    val add: () -> Unit = {
        editorId = state.entries.firstOrNull { it.date == LocalDate.now() }?.id ?: -1L
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            model.clearMessage()
        }
    }
    BackHandler(enabled = tab != 0 && editorId == null && !goalEditor) { tab = 0 }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(33.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Spa, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        Text("still", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                val destinations = listOf("Today" to Icons.Outlined.GridView, "History" to Icons.Outlined.CalendarToday, "Trends" to Icons.AutoMirrored.Outlined.ShowChart, "Settings" to Icons.Outlined.Tune)
                destinations.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == index, onClick = { tab = index },
                        icon = { Icon(icon, label, Modifier.size(22.dp)) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == 1 && !state.loading) {
                ExtendedFloatingActionButton(onClick = add, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Check in") }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (state.error == null) CircularProgressIndicator()
                    else Button(onClick = model::reload, enabled = !state.busy) { Text("Retry loading") }
                    Text("Loading entries...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                screenStates.SaveableStateProvider(tab) {
                    when (tab) {
                        0 -> HomeScreen(state, add, { editorId = it.id }, { tab = 1 }, { goalEditor = true })
                        1 -> HistoryScreen(state, { editorId = it.id }, add)
                        2 -> TrendsScreen(state) { goalEditor = true }
                        3 -> SettingsScreen(
                            state, { model.savePreferences(it) }, { goalEditor = true },
                            { import.launch(arrayOf("text/*", "application/csv", "application/octet-stream")) },
                            { export.launch("still-${LocalDate.now()}.csv") },
                            notificationsAllowed, setReminder,
                            onBackup = { backup.launch("still-${LocalDate.now()}.still") },
                            onRestore = { restore.launch(arrayOf("*/*")) },
                        )
                    }
                }
            }
            if (state.busy && !state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
    editorId?.let { id ->
        key(id) {
            EntryEditor(
                entry = state.entries.find { it.id == id }, suggestedKg = state.entries.firstOrNull()?.weightKg,
                unit = state.preferences.unit, busy = state.busy, onClose = { editorId = null },
                onSave = { model.saveEntry(it) { editorId = null } },
                onDelete = { model.deleteEntry(it) { editorId = null } },
            )
        }
    }
    if (goalEditor) {
        PreferencesEditor(state.preferences, state.busy, { goalEditor = false }) {
            model.savePreferences(it) { goalEditor = false }
        }
    }
    state.pendingImport?.let { pending ->
        AlertDialog(
            onDismissRequest = { if (!state.busy) model.cancelImport() },
            icon = { Icon(Icons.Outlined.FileDownload, null) },
            title = { Text("Import measurements?") },
            text = { Text("Found ${pending.size} check-ins. New dates will be added; existing dates will be skipped. Your current entries will not be changed.") },
            confirmButton = { TextButton(onClick = model::confirmImport, enabled = !state.busy) { Text("Import") } },
            dismissButton = { TextButton(onClick = model::cancelImport, enabled = !state.busy) { Text("Cancel") } },
        )
    }
    state.pendingRestore?.let { pending ->
        RestoreBackupDialog(
            pending, state.entries.size, state.busy, Reminders.allowed(context),
            model::cancelRestore, model::confirmRestore,
        )
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = model::clearError,
            title = { Text("Unable to complete action") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = model::clearError) { Text("OK") } },
        )
    }
}
