package app.still.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.still.TrackerState
import app.still.ReminderSettings
import app.still.data.Entry
import app.still.data.Metrics
import app.still.data.Preferences
import app.still.data.WeightUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(state: TrackerState, onAdd: () -> Unit, onEdit: (Entry) -> Unit, onHistory: () -> Unit, onGoal: () -> Unit) {
    val unit = state.preferences.unit
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
                Text(if (latest == null) "A little check-in.\nA clearer picture." else "Your progress,\nat your pace.", style = MaterialTheme.typography.headlineLarge)
                Text("Less pressure. More perspective.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().background(Forest, RoundedCornerShape(30.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow(if (latest == null) "YOUR STARTING POINT" else "LATEST WEIGHT", Color(0xFFD1DEC9))
                    Icon(Icons.Outlined.Spa, null, tint = Lime, modifier = Modifier.size(26.dp))
                }
                if (latest == null) {
                    Text("Hello, you.", color = Color.White, style = MaterialTheme.typography.headlineLarge)
                    Text("One number doesn't tell the whole story.\nStart yours with a simple check-in.", color = Color(0xFFDFE8D9), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(number(unit.fromKg(latest.weightKg)), color = Color.White, style = MaterialTheme.typography.displayLarge)
                        Text(unit.symbol, color = Color(0xFFDFE8D9), fontSize = 23.sp, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(dateLabel(latest.date), color = Color(0xFFDFE8D9), style = MaterialTheme.typography.bodySmall)
                        if (entries.size > 1) {
                            Text(
                                "${signed(unit.fromKg(latest.weightKg - entries.last().weightKg))} ${unit.symbol} overall",
                                modifier = Modifier.background(Color.White.copy(alpha = .12f), RoundedCornerShape(20.dp)).padding(horizontal = 11.dp, vertical = 6.dp),
                                color = Lime, style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                Button(
                    onClick = onAdd,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Color(0xFF243A28)),
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
                    MetricTile("Total check-ins", entries.size.toString(), "one day at a time", Modifier.weight(1f))
                }
            }
        }
        item {
            Panel {
                SectionHeading("The bigger picture")
                PeriodPicker(period) { period = it }
                WeightChart(entries, unit, period)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Text("Recorded weight / ${unit.symbol}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { GoalCard(entries, state.preferences, onGoal) }
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
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text("Just for you. Stored on your device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun GoalCard(entries: List<Entry>, preferences: Preferences, onGoal: () -> Unit) {
    val goal = preferences.goalKg
    val current = entries.firstOrNull()?.weightKg
    val start = entries.lastOrNull()?.weightKg
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(Icons.Outlined.Flag)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (goal == null) "Your pace. Your goal." else "A direction, not a deadline", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (goal == null) "Add a target if it feels right for you." else "Target: ${number(preferences.unit.fromKg(goal))} ${preferences.unit.symbol}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (goal != null && current != null && start != null) {
            val progress = Metrics.progress(start, current, goal).toFloat()
            LinearProgressIndicator(
                progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp),
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                "${number(preferences.unit.fromKg(kotlin.math.abs(current - goal)))} ${preferences.unit.symbol} from your target",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onGoal, contentPadding = PaddingValues(0.dp)) {
            Text(if (goal == null) "Set an optional goal" else "Edit goal")
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp))
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
                Eyebrow("ONE DAY AT A TIME")
                Text("Your check-ins", style = MaterialTheme.typography.headlineLarge)
                Text("${entries.size} ${if (entries.size == 1) "entry" else "entries"} / Tap any check-in to edit", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (entries.isEmpty()) {
            item {
                Panel {
                    IconBadge(Icons.Outlined.CalendarToday)
                    Text("A fresh page", style = MaterialTheme.typography.headlineMedium)
                    Text("Your check-ins will live here, along with the little notes that put them in context.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
fun TrendsScreen(state: TrackerState, onGoal: () -> Unit) {
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow("ZOOM OUT A LITTLE")
                Text("Patterns, not pressure.", style = MaterialTheme.typography.headlineLarge)
                Text("A longer view of your recorded weight.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
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
        item { GoalCard(entries, state.preferences, onGoal) }
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
    onGoal: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    notificationsAllowed: Boolean = true,
    onReminder: (ReminderSettings) -> Unit = {},
) {
    val preferences = state.preferences
    val context = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp, 16.dp, 22.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow("MAKE YOURSELF AT HOME")
                Text("Made for you.", style = MaterialTheme.typography.headlineLarge)
                Text("A few preferences. Nothing complicated.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Panel {
                Text("The essentials", style = MaterialTheme.typography.titleLarge)
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
                Text("Your entries are converted, never reinterpreted. Waist and height use centimeters.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick = onGoal, enabled = !state.busy, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Outlined.Tune, null, Modifier.size(21.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Goal & height")
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Outlined.ChevronRight, null)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Dark appearance", style = MaterialTheme.typography.titleSmall)
                        Text("A softer glow for evening check-ins", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = preferences.darkMode, enabled = !state.busy, onCheckedChange = { onPreferences(preferences.copy(darkMode = it)) })
                }
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("A gentle nudge", style = MaterialTheme.typography.titleLarge)
                        Text("An optional daily reminder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = state.reminder.enabled, enabled = !state.busy,
                        onCheckedChange = { onReminder(state.reminder.copy(enabled = it)) },
                    )
                }
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(context, { _, hour, minute ->
                            onReminder(state.reminder.copy(hour = hour, minute = minute))
                        }, state.reminder.hour, state.reminder.minute, DateFormat.is24HourFormat(context)).show()
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("Around ${java.time.LocalTime.of(state.reminder.hour, state.reminder.minute).format(DateTimeFormatter.ofPattern("HH:mm"))}")
                }
                Text(
                    if (state.reminder.enabled && !notificationsAllowed) "Notifications are blocked in Android Settings. Turn them on there to receive reminders."
                    else "Off by default. Delivery may be delayed by Android's battery settings.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Panel {
                Text("Your data stays yours", style = MaterialTheme.typography.titleLarge)
                Text("Save a CSV backup or bring your history with you. Imports add new dates without replacing existing check-ins.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onExport, enabled = !state.busy && state.entries.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                    Icon(Icons.Outlined.FileUpload, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("Export CSV backup")
                }
                OutlinedButton(onClick = onImport, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                    Icon(Icons.Outlined.FileDownload, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("Import CSV")
                }
                Text("Accepts Still backups and original DroidWeight exports. Keep a backup before changing phones or uninstalling.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Panel {
                IconBadge(Icons.Outlined.Lock)
                Text("Private by design.", style = MaterialTheme.typography.titleLarge)
                Text("No account. No ads. No analytics. Still has no internet permission, and automatic cloud backups are disabled.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("STILL / 1.0.0", style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
                Text("A fresh take on the simplicity of DroidWeight.\nBuilt for a little more perspective.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
