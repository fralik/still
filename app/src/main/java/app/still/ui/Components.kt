package app.still.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.still.data.Entry
import app.still.data.WeightUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

fun number(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)
fun signed(value: Double): String = (if (value > 0.049) "+" else "") + number(if (kotlin.math.abs(value) < .05) 0.0 else value)
fun dateLabel(date: LocalDate): String = when (date) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
}

@Composable
fun Eyebrow(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.7.sp, color = color)
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f), RoundedCornerShape(28.dp))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
fun SectionHeading(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action); Spacer(Modifier.width(5.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp)) }
    }
}

@Composable
fun MetricTile(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f), RoundedCornerShape(22.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(modifier.size(46.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun EntryRow(entry: Entry, previous: Entry?, unit: WeightUnit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Edit check-in", onClick = onClick).padding(vertical = 13.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Column(
            Modifier.width(43.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(13.dp)).padding(vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(entry.date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
            Text(entry.date.format(DateTimeFormatter.ofPattern("MMM")).uppercase(), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(dateLabel(entry.date), style = MaterialTheme.typography.titleSmall)
            val detail = entry.note.ifBlank {
                listOfNotNull(entry.bodyFat?.let { "${number(it)}% body fat" }, entry.waistCm?.let { "${number(it)} cm waist" })
                    .joinToString(" / ")
            }
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${number(unit.fromKg(entry.weightKg))} ${unit.symbol}", style = MaterialTheme.typography.titleMedium)
            if (previous != null) Text(
                "${signed(unit.fromKg(entry.weightKg - previous.weightKg))} ${unit.symbol}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Outlined.Edit, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun PeriodPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectableGroup().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f), RoundedCornerShape(16.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(30 to "1M", 90 to "3M", 180 to "6M", 0 to "All").forEach { (days, label) ->
            val active = days == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp)
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                    .selectable(selected = active, role = Role.Tab) { onSelect(days) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun WeightChart(entries: List<Entry>, unit: WeightUnit, days: Int, height: Int = 170) {
    val today = LocalDate.now()
    val points = remember(entries, days, today) {
        entries.filter { days == 0 || !it.date.isBefore(today.minusDays(days.toLong() - 1)) }.sortedBy { it.date }
    }
    if (points.isEmpty()) {
        Column(Modifier.fillMaxWidth().height(height.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            IconBadge(Icons.AutoMirrored.Outlined.ShowChart)
            Spacer(Modifier.height(12.dp))
            Text(if (entries.isEmpty()) "No entries yet" else "No check-ins in this period", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(if (entries.isEmpty()) "Log a weight to begin your chart." else "Try a wider date range.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val values = points.map { unit.fromKg(it.weightKg) }
    val spread = max(values.max() - values.min(), 1.0)
    val low = values.min() - spread * .25
    val high = values.max() + spread * .25
    val start = if (days == 0) points.first().date else today.minusDays(days.toLong() - 1)
    val end = if (days == 0) points.last().date else today
    val duration = max((end.toEpochDay() - start.toEpochDay()).toDouble(), 1.0)
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val summary = "${points.size} entries. ${number(values.first())} to ${number(values.last())} ${unit.symbol}, from $start to $end."
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = summary }) {
        Row(Modifier.fillMaxWidth().height(height.dp)) {
            Column(Modifier.width(44.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(high, (high + low) / 2, low).forEach {
                    Text(number(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Canvas(Modifier.weight(1f).fillMaxHeight().padding(top = 7.dp, bottom = 7.dp, end = 5.dp)) {
                for (i in 0..2) {
                    val y = size.height * i / 2
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                val offsets = points.mapIndexed { index, entry ->
                    val x = if (points.size == 1 && days == 0) size.width / 2 else ((entry.date.toEpochDay() - start.toEpochDay()) / duration * size.width).toFloat()
                    Offset(x, ((high - values[index]) / (high - low) * size.height).toFloat())
                }
                val path = Path().apply {
                    moveTo(offsets.first().x, offsets.first().y)
                    offsets.drop(1).forEach { lineTo(it.x, it.y) }
                }
                if (offsets.size > 1) {
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(offsets.last().x, size.height)
                        lineTo(offsets.first().x, size.height)
                        close()
                    }
                    drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = .18f), lineColor.copy(alpha = .01f))))
                    drawPath(path, lineColor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                }
                if (offsets.size < 45) offsets.forEach { drawCircle(lineColor, 2.5.dp.toPx(), it) }
                drawCircle(lineColor, 5.dp.toPx(), offsets.last())
                drawCircle(Color.White, 2.dp.toPx(), offsets.last())
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 44.dp, top = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(start.format(DateTimeFormatter.ofPattern("MMM d")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(end.format(DateTimeFormatter.ofPattern("MMM d")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
