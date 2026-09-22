package app.still.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.still.data.FullBackup
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RestoreBackupDialog(
    backup: FullBackup,
    currentEntries: Int,
    busy: Boolean,
    notificationsAllowed: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val preferences = backup.preferences
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        icon = { Icon(Icons.Outlined.Restore, null) },
        title = { Text("Restore full backup?") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Created ${backup.createdAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm"))}")
                Text("${backup.entries.size} check-ins in this backup")
                if (backup.entries.isNotEmpty()) {
                    Text("${backup.entries.minOf { it.date }} to ${backup.entries.maxOf { it.date }}")
                }
                Text("Unit: ${preferences.unit.symbol}\nAppearance: ${if (preferences.darkMode) "Dark" else "Light"}")
                Text("Goal: ${preferences.goalKg?.let { "${number(preferences.unit.fromKg(it))} ${preferences.unit.symbol}" } ?: "Not set"}\nHeight: ${preferences.heightCm?.let { "${number(it)} cm" } ?: "Not set"}")
                val time = java.time.LocalTime.of(backup.reminder.hour, backup.reminder.minute)
                Text("Reminder: ${if (backup.reminder.enabled) "On" else "Off"} / $time (this phone's local time)")
                if (backup.reminder.enabled && !notificationsAllowed) {
                    Text("Reminders will be paused until you allow notifications in Android Settings.")
                }
                HorizontalDivider()
                Text(
                    "This replaces all $currentEntries current check-ins and all settings on this device. It does not merge entries. Create a full backup first if you want to keep your current journal.",
                    color = MaterialTheme.colorScheme.error,
                )
                if (backup.entries.isEmpty()) {
                    Text("This backup has no check-ins. Restoring it will leave your journal empty.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(if (busy) "Restoring..." else "Replace journal", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel") } },
    )
}
