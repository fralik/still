package app.still.data

import java.time.LocalDate
import java.time.format.DateTimeParseException

object CsvCodec {
    private const val HEADER = "date,weight_kg,body_fat_percent,waist_cm,note"
    private const val LEGACY_HEADER = "value|type|date|metric|id|comment"
    private const val MAX_ENTRIES = 100_000
    private val datePattern = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val legacyDatePattern = Regex("\\d{4}-\\d{2}-\\d{2}(?: [0-2]\\d:[0-5]\\d:[0-5]\\d)?")

    fun encode(entries: List<Entry>): String {
        require(entries.size <= MAX_ENTRIES) { "Export must contain at most $MAX_ENTRIES entries." }
        entries.forEach(Metrics::validate)
        return buildString {
            append(HEADER).append("\r\n")
            for (entry in entries) {
                append(
                    listOf(
                        entry.date.toString(),
                        entry.weightKg.toString(),
                        entry.bodyFat?.toString().orEmpty(),
                        entry.waistCm?.toString().orEmpty(),
                        entry.note,
                    ).joinToString(",") { escape(it) },
                ).append("\r\n")
            }
        }
    }

    fun decode(text: String): List<Entry> {
        val input = text.removePrefix("\uFEFF")
        require(input.isNotBlank()) { "Line 1: File is empty." }
        val firstLine = input.takeWhile { it != '\r' && it != '\n' }
        return when (firstLine) {
            HEADER -> decodeCanonical(input)
            LEGACY_HEADER, "$LEGACY_HEADER|" -> decodeLegacy(input)
            else -> fail(1, "Unsupported header. Expected $HEADER or $LEGACY_HEADER.")
        }
    }

    private fun decodeCanonical(input: String): List<Entry> =
        parseCsv(input).drop(1).map { row ->
            val fields = row.fields
            if (fields.size != 5) fail(row.line, "Expected exactly 5 CSV fields, got ${fields.size}.")
            validated(
                Entry(
                    date = date(fields[0], row.line),
                    weightKg = number(fields[1], row.line, "weight"),
                    bodyFat = optionalNumber(fields[2], row.line, "body fat"),
                    waistCm = optionalNumber(fields[3], row.line, "waist"),
                    note = fields[4],
                ),
                row.line,
            )
        }

    private data class LegacyDay(
        val firstLine: Int,
        var weight: Double? = null,
        var fat: Double? = null,
        var waist: Double? = null,
        var weightNote: String = "",
        var fatNote: String = "",
        var waistNote: String = "",
    )

    private fun decodeLegacy(input: String): List<Entry> {
        val days = linkedMapOf<LocalDate, LegacyDay>()
        val rows = input.split(Regex("\\r\\n|\\n|\\r")).toMutableList()
        if (rows.last().isEmpty()) rows.removeAt(rows.lastIndex)
        if (rows.size > MAX_ENTRIES * 4 + 1) fail(1, "Too many legacy rows.")
        for (index in 1 until rows.size) {
            val line = index + 1
            val parts = rows[index].split('|').toMutableList()
            if (parts.size > 6 && parts.last().isEmpty()) parts.removeAt(parts.lastIndex)
            if (parts.size < 6) fail(line, "Expected value|type|date|metric|id|comment.")
            val value = number(parts[0], line, "measurement")
            if (value <= 0) fail(line, "Measurement must be greater than 0.")
            val type = parts[1]
            if (type !in setOf("WEIGHT", "BODYFAT", "WAIST", "HEIGHT")) {
                fail(line, "Unsupported measurement type '$type'.")
            }
            val timestamp = parts[2]
            if (!legacyDatePattern.matches(timestamp) ||
                (timestamp.length > 10 && timestamp.substring(11, 13).toInt() > 23)
            ) {
                fail(line, "Invalid legacy date '$timestamp'.")
            }
            // Old exports used ambiguous 12-hour timestamps; only the calendar date is meaningful.
            val date = date(timestamp.take(10), line)
            val metric = when (parts[3]) {
                "true" -> true
                "false" -> false
                else -> fail(line, "Metric must be 'true' or 'false'.")
            }
            if (parts[4].toLongOrNull()?.let { it >= 0 } != true) fail(line, "Invalid legacy ID.")
            val note = parts.drop(5).joinToString("|")
            if (note.length > 2000) fail(line, "Note must be at most 2000 characters.")
            // HEIGHT is a profile setting, not a daily measurement, and is intentionally ignored.
            if (type == "HEIGHT") continue
            val day = days.getOrPut(date) { LegacyDay(line) }
            if (days.size > MAX_ENTRIES) fail(line, "Import must contain at most $MAX_ENTRIES entries.")
            when (type) {
                "WEIGHT" -> {
                    // Match DroidWeight's historical conversions rather than today's precise unit factor.
                    val kg = if (metric) value else value / 2.2
                    validated(Entry(date = date, weightKg = kg), line)
                    day.weight = kg
                    day.weightNote = note
                }
                "BODYFAT" -> {
                    validated(Entry(date = date, weightKg = 1.0, bodyFat = value), line)
                    day.fat = value
                    day.fatNote = note
                }
                "WAIST" -> {
                    val cm = if (metric) value else value / 0.39
                    validated(Entry(date = date, weightKg = 1.0, waistCm = cm), line)
                    day.waist = cm
                    day.waistNote = note
                }
            }
        }
        return days.map { (date, day) ->
            val weight = day.weight ?: fail(day.firstLine, "Measurements for $date have no WEIGHT row.")
            validated(
                Entry(
                    date = date,
                    weightKg = weight,
                    bodyFat = day.fat,
                    waistCm = day.waist,
                    note = listOf(day.weightNote, day.fatNote, day.waistNote)
                        .filter { it.isNotEmpty() }.distinct().joinToString("\n"),
                ),
                day.firstLine,
            )
        }
    }

    private data class Row(val line: Int, val fields: List<String>)

    private fun parseCsv(input: String): List<Row> {
        val rows = mutableListOf<Row>()
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var line = 1
        var rowLine = 1
        var inQuotes = false
        var afterQuote = false
        var started = false
        var index = 0

        fun endField() {
            fields.add(field.toString())
            field.setLength(0)
            afterQuote = false
        }

        fun endRow() {
            endField()
            rows.add(Row(rowLine, fields.toList()))
            if (rows.size > MAX_ENTRIES + 1) fail(rowLine, "Import must contain at most $MAX_ENTRIES entries.")
            fields.clear()
            started = false
        }

        while (index < input.length) {
            val char = input[index]
            started = true
            if (inQuotes) {
                if (char == '"') {
                    if (index + 1 < input.length && input[index + 1] == '"') {
                        field.append('"')
                        index++
                    } else {
                        inQuotes = false
                        afterQuote = true
                    }
                } else {
                    field.append(char)
                    if (char == '\r') {
                        if (index + 1 < input.length && input[index + 1] == '\n') {
                            field.append('\n')
                            index++
                        }
                        line++
                    } else if (char == '\n') {
                        line++
                    }
                }
            } else {
                when (char) {
                    ',' -> endField()
                    '\r', '\n' -> {
                        endRow()
                        if (char == '\r' && index + 1 < input.length && input[index + 1] == '\n') index++
                        line++
                        rowLine = line
                    }
                    '"' -> {
                        if (field.isNotEmpty() || afterQuote) fail(line, "Unexpected quote in unquoted field.")
                        inQuotes = true
                    }
                    else -> {
                        if (afterQuote) fail(line, "Unexpected text after closing quote.")
                        field.append(char)
                    }
                }
            }
            index++
        }
        if (inQuotes) fail(rowLine, "Unterminated quoted field.")
        if (started) endRow()
        return rows
    }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    private fun optionalNumber(value: String, line: Int, name: String): Double? =
        if (value.isEmpty()) null else number(value, line, name)

    private fun number(value: String, line: Int, name: String): Double {
        if (value != value.trim()) fail(line, "Invalid $name '$value'.")
        return value.toDoubleOrNull()?.takeIf { it.isFinite() }
            ?: fail(line, "Invalid finite $name '$value'.")
    }

    private fun date(value: String, line: Int): LocalDate {
        if (!datePattern.matches(value)) fail(line, "Invalid date '$value'; expected yyyy-MM-dd.")
        val parsed = try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            fail(line, "Invalid date '$value'.")
        }
        if (parsed.isAfter(LocalDate.now())) fail(line, "Date must not be in the future.")
        return parsed
    }

    private fun validated(entry: Entry, line: Int): Entry {
        try {
            Metrics.validate(entry)
        } catch (error: IllegalArgumentException) {
            fail(line, error.message ?: "Invalid entry.")
        }
        return entry
    }

    private fun fail(line: Int, message: String): Nothing =
        throw IllegalArgumentException("Line $line: $message")
}
