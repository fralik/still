package app.still.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.still.data.Entry
import app.still.data.Metrics
import app.still.data.Preferences
import app.still.data.WeightUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId

private fun inputNumber(value: Double): String =
    BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

private fun parseNumber(value: String, name: String, optional: Boolean = false): Double? {
    if (optional && value.isBlank()) return null
    val result = value.trim().replace(',', '.').toDoubleOrNull()
    require(result != null && result.isFinite() && result > 0) { "Enter a positive number for $name." }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorFrame(title: String, busy: Boolean, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(
        onDismissRequest = { if (!busy) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                modifier = Modifier.systemBarsPadding().imePadding(),
                topBar = {
                    TopAppBar(
                        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                        navigationIcon = {
                            IconButton(onClick = onClose, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Close editor") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                },
            ) { padding ->
                Column(
                    Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
fun EntryEditor(
    entry: Entry?,
    suggestedKg: Double?,
    unit: WeightUnit,
    busy: Boolean,
    onClose: () -> Unit,
    onSave: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
) {
    val initialWeight = entry?.weightKg?.let { inputNumber(unit.fromKg(it)) }.orEmpty()
    val initialFat = entry?.bodyFat?.let(::inputNumber).orEmpty()
    val initialWaist = entry?.waistCm?.let(::inputNumber).orEmpty()
    var weight by rememberSaveable { mutableStateOf(initialWeight) }
    var date by rememberSaveable { mutableStateOf((entry?.date ?: LocalDate.now()).toString()) }
    var fat by rememberSaveable { mutableStateOf(initialFat) }
    var waist by rememberSaveable { mutableStateOf(initialWaist) }
    var note by rememberSaveable { mutableStateOf(entry?.note.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    EditorFrame(if (entry == null) "New check-in" else "Edit check-in", busy, onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Eyebrow("A MOMENT FOR YOURSELF")
            Text("How are you today?", style = MaterialTheme.typography.headlineLarge)
            Text("Log the number. Leave the judgment.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = weight, onValueChange = { weight = it; error = null },
            label = { Text("Weight (${unit.symbol})") },
            placeholder = { Text(suggestedKg?.let { number(unit.fromKg(it)) } ?: "Enter weight") },
            suffix = { Text(unit.symbol) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.headlineLarge,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true, enabled = !busy,
        )
        OutlinedButton(
            onClick = {
                val selected = LocalDate.parse(date)
                DatePickerDialog(context, { _, year, month, day ->
                    date = LocalDate.of(year, month + 1, day).toString()
                }, selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
                    datePicker.maxDate = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
                }.show()
            },
            enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Icon(Icons.Outlined.CalendarToday, null)
            Spacer(Modifier.width(10.dp))
            Text(dateLabel(LocalDate.parse(date)))
            Spacer(Modifier.weight(1f))
            Text("Change")
        }
        Text("One check-in per day. You can edit earlier dates in History.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Eyebrow("A LITTLE CONTEXT / OPTIONAL")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = fat, onValueChange = { fat = it; error = null }, label = { Text("Body fat (%)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true, enabled = !busy, modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = waist, onValueChange = { waist = it; error = null }, label = { Text("Waist (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true, enabled = !busy, modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = note, onValueChange = {
                note = it
                error = if (it.length > 2000) "Notes can contain at most 2000 characters." else null
            },
            label = { Text("A note to yourself") },
            placeholder = { Text("Morning check-in, after a walk...") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6, enabled = !busy,
            supportingText = { Text("${note.length}/2000") }, isError = note.length > 2000,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Button(
            onClick = {
                try {
                    val parsedWeight = parseNumber(weight, "weight")!!
                    val result = Entry(
                        id = entry?.id ?: 0,
                        date = LocalDate.parse(date),
                        weightKg = if (entry != null && weight == initialWeight) entry.weightKg else unit.toKg(parsedWeight),
                        bodyFat = if (entry != null && fat == initialFat) entry.bodyFat else parseNumber(fat, "body fat", optional = true),
                        waistCm = if (entry != null && waist == initialWaist) entry.waistCm else parseNumber(waist, "waist", optional = true),
                        note = note.trim(),
                    )
                    Metrics.validate(result)
                    onSave(result)
                } catch (invalid: IllegalArgumentException) {
                    error = invalid.message
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(if (busy) "Saving..." else "Save check-in") }
        if (entry != null) {
            TextButton(
                onClick = { confirmDelete = true }, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Outlined.DeleteOutline, null)
                Spacer(Modifier.width(8.dp))
                Text("Delete entry")
            }
        }
    }
    if (confirmDelete && entry != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDelete = false },
            title = { Text("Delete this check-in?") },
            text = { Text("The entry for ${dateLabel(entry.date)} will be permanently removed. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { onDelete(entry) }, enabled = !busy) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }, enabled = !busy) { Text("Keep entry") } },
        )
    }
}

@Composable
fun PreferencesEditor(preferences: Preferences, busy: Boolean, onClose: () -> Unit, onSave: (Preferences) -> Unit) {
    val initialGoal = preferences.goalKg?.let { inputNumber(preferences.unit.fromKg(it)) }.orEmpty()
    val initialHeight = preferences.heightCm?.let(::inputNumber).orEmpty()
    var goal by rememberSaveable { mutableStateOf(initialGoal) }
    var height by rememberSaveable { mutableStateOf(initialHeight) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    EditorFrame("Goal & height", busy, onClose) {
        Text("A direction, if you want one.", style = MaterialTheme.typography.headlineLarge)
        Text("Both fields are optional. Clear a value to remove it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = goal, onValueChange = { goal = it; error = null }, label = { Text("Goal weight (${preferences.unit.symbol})") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
            value = height, onValueChange = { height = it; error = null }, label = { Text("Height (cm)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText = { Text("Used only to calculate an optional BMI reference.") },
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                try {
                    val parsedGoal = parseNumber(goal, "goal", optional = true)
                    val kg = if (goal == initialGoal) preferences.goalKg else parsedGoal?.let(preferences.unit::toKg)
                    val cm = if (height == initialHeight) preferences.heightCm else parseNumber(height, "height", optional = true)
                    require(kg == null || kg <= 650) { "Goal weight must be at most 650 kg (1433 lb)." }
                    require(cm == null || cm <= 300) { "Height must be at most 300 cm." }
                    onSave(preferences.copy(goalKg = kg, heightCm = cm))
                } catch (invalid: IllegalArgumentException) {
                    error = invalid.message
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        ) { Text(if (busy) "Saving..." else "Save preferences") }
    }
}
