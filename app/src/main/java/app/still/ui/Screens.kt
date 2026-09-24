package app.still.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.still.TrackerState
import app.still.BuildConfig
import app.still.data.Entry
import app.still.data.Metrics
import app.still.data.Preferences
import app.still.data.ThemeMode
import app.still.data.WeightUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(state: TrackerState, onAdd: () -> Unit, onEdit: (Entry) -> Unit, onHistory: () -> Unit) {
    val unit = state.preferences.unit
    val hero = LocalHeroColors.current
    val entries = state.entries
    val latest = entries.firstOrNull()
    var period by rememberSaveable { mutableIntStateOf(30) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")).uppercase())
                Text("Overview", style = MaterialTheme.typography.headlineLarge)
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().background(hero.background, RoundedCornerShape(30.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("LATEST WEIGHT", hero.muted)
                    Icon(Icons.Outlined.Spa, null, tint = hero.accent, modifier = Modifier.size(26.dp))
                }
                if (latest == null) {
                    Text("No weight recorded", color = hero.foreground, style = MaterialTheme.typography.headlineLarge)
                    Text("Log a weight to start tracking.", color = hero.muted, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(number(unit.fromKg(latest.weightKg)), color = hero.foreground, style = MaterialTheme.typography.displayLarge)
                        Text(unit.symbol, color = hero.muted, fontSize = 23.sp, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(dateLabel(latest.date), color = hero.muted, style = MaterialTheme.typography.bodySmall)
                        if (entries.size > 1) {
                            Text(
                                "${signed(unit.fromKg(latest.weightKg - entries.last().weightKg))} ${unit.symbol} overall",
                                modifier = Modifier.background(hero.foreground.copy(alpha = .12f), RoundedCornerShape(20.dp)).padding(horizontal = 11.dp, vertical = 6.dp),
                                color = hero.badgeForeground, style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                Button(
                    onClick = onAdd,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = hero.accent, contentColor = hero.onAccent),
                ) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(if (entries.any { it.date == LocalDate.now() }) "Update today's check-in" else "Log your weight")
                }
            }
        }
        if (latest != null) {
            item {
                val week = entries.filter { !it.date.isBefore(LocalDate.now().minusDays(6)) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile("7-day average", Metrics.average(week)?.let { number(unit.fromKg(it)) } ?: "--", "${unit.symbol} / ${week.size} check-ins", Modifier.weight(1f))
                    MetricTile("Total check-ins", entries.size.toString(), "Since ${entries.last().date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}", Modifier.weight(1f))
                }
            }
        }
        item {
            Panel {
                SectionHeading("Weight history")
                PeriodPicker(period) { period = it }
                WeightChart(entries, unit, period)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Text("Recorded weight / ${unit.symbol}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (entries.isNotEmpty()) {
            item {
                Panel {
                    SectionHeading("Recent check-ins", "See all", onHistory)
                    Column {
                        entries.take(3).forEachIndexed { index, entry ->
                            EntryRow(entry, entries.getOrNull(index + 1), unit) { onEdit(entry) }
                            if (index < minOf(entries.size, 3) - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(state: TrackerState, onEdit: (Entry) -> Unit, onAdd: () -> Unit) {
    val entries = state.entries
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp, 16.dp, 22.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("History", style = MaterialTheme.typography.headlineLarge)
                Text("${entries.size} ${if (entries.size == 1) "entry" else "entries"} / Tap any check-in to edit", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (entries.isEmpty()) {
            item {
                Panel {
                    IconBadge(Icons.Outlined.CalendarToday)
                    Text("No entries yet", style = MaterialTheme.typography.headlineMedium)
                    Text("Saved weights and notes appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Add your first check-in") }
                }
            }
        }
        itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
            Column {
                if (index == 0 || entries[index - 1].date.month != entry.date.month || entries[index - 1].date.year != entry.date.year) {
                    Box(Modifier.padding(top = 14.dp, bottom = 12.dp)) {
                        Eyebrow(entry.date.format(DateTimeFormatter.ofPattern("MMMM yyyy")).uppercase())
                    }
                }
                EntryRow(entry, entries.getOrNull(index + 1), state.preferences.unit) { onEdit(entry) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
            }
        }
    }
}

@Composable
fun TrendsScreen(state: TrackerState) {
    var period by rememberSaveable { mutableIntStateOf(90) }
    val entries = state.entries
    val unit = state.preferences.unit
    val selected = entries.filter { period == 0 || !it.date.isBefore(LocalDate.now().minusDays(period.toLong() - 1)) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp, 16.dp, 22.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Text("Trends", style = MaterialTheme.typography.headlineLarge)
        }
        item {
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Weight history", style = MaterialTheme.typography.titleLarge)
                    Text(unit.symbol, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PeriodPicker(period) { period = it }
                WeightChart(entries, unit, period, 220)
                Text("${selected.size} check-ins in this period", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile("Period average", Metrics.average(selected)?.let { number(unit.fromKg(it)) } ?: "--", unit.symbol, Modifier.weight(1f))
                MetricTile("Period change", if (selected.size < 2) "--" else signed(unit.fromKg(selected.first().weightKg - selected.last().weightKg)), unit.symbol, Modifier.weight(1f))
            }
        }
        if (selected.isNotEmpty()) {
            item {
                Panel {
                    Eyebrow("IN THIS PERIOD")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Lowest", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            Text("${number(unit.fromKg(selected.minOf { it.weightKg }))} ${unit.symbol}", style = MaterialTheme.typography.titleLarge)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Highest", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            Text("${number(unit.fromKg(selected.maxOf { it.weightKg }))} ${unit.symbol}", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
        val height = state.preferences.heightCm
        val latest = entries.firstOrNull()
        if (height != null && latest != null) {
            item {
                Panel {
                    Eyebrow("OPTIONAL METRIC")
                    Text(number(Metrics.bmi(latest.weightKg, height)), style = MaterialTheme.typography.headlineLarge)
                    Text("Body mass index (BMI)", style = MaterialTheme.typography.titleMedium)
                    Text("Calculated from your latest weight and saved height. This is a reference number, not a health assessment.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    state: TrackerState,
    onPreferences: (Preferences) -> Unit,
    onHeight: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onBackup: () -> Unit = {},
    onRestore: () -> Unit = {},
) {
    val preferences = state.preferences
    var dataToolsExpanded by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp, 16.dp, 22.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineLarge)
        }
        item {
            Panel {
                Text("Preferences", style = MaterialTheme.typography.titleLarge)
                Text("Weight unit", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WeightUnit.entries.forEach { unit ->
                        FilterChip(
                            selected = preferences.unit == unit,
                            onClick = { onPreferences(preferences.copy(unit = unit)) },
                            enabled = !state.busy,
                            label = { Text(if (unit == WeightUnit.KG) "Kilograms / kg" else "Pounds / lb") },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                    }
                }
                Text("Height and waist use centimeters.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick = onHeight, enabled = !state.busy, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Outlined.Tune, null, Modifier.size(21.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Height")
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Outlined.ChevronRight, null)
                }
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = preferences.themeMode == mode,
                            onClick = { onPreferences(preferences.copy(themeMode = mode)) },
                            enabled = !state.busy,
                            label = { Text(mode.label) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                    }
                }
                if (preferences.themeMode == ThemeMode.SYSTEM) {
                    Text("Follows the phone's light or dark theme.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Panel {
                Text("Backup & transfer", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (android.os.Build.VERSION.SDK_INT >= 28) "Entries and settings can transfer during Android phone setup, where supported."
                    else "On Android 8, use a backup file to move entries and settings to a new phone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onBackup, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                    Icon(Icons.Outlined.FileUpload, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("Back up everything")
                }
                OutlinedButton(onClick = onRestore, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                    Icon(Icons.Outlined.FileDownload, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("Restore backup")
                }
                Text("Full backups (.still) include entries and settings. Restoring replaces the current journal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Backup files are not encrypted. No automatic cloud backup or sync.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Panel {
                TextButton(
                    onClick = { dataToolsExpanded = !dataToolsExpanded },
                    modifier = Modifier.fillMaxWidth().semantics {
                        stateDescription = if (dataToolsExpanded) "Expanded" else "Collapsed"
                    },
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    Text("Data tools", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    Icon(if (dataToolsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                }
                Text("CSV import and export", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (dataToolsExpanded) {
                    Text("Measurements and notes only. Import adds missing dates without overwriting entries.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onImport, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                        Icon(Icons.Outlined.FileDownload, null, Modifier.size(19.dp))
                        Spacer(Modifier.width(9.dp))
                        Text("Import measurements (CSV)")
                    }
                    OutlinedButton(onClick = onExport, enabled = !state.busy && state.entries.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                        Icon(Icons.Outlined.FileUpload, null, Modifier.size(19.dp))
                        Spacer(Modifier.width(9.dp))
                        Text("Export measurements (CSV)")
                    }
                    Text("DroidWeight CSV imports are also supported.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Panel {
                Text("About", style = MaterialTheme.typography.titleLarge)
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                Text("Inspired by DroidWeight.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
