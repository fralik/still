package app.still.data

import app.still.ReminderSettings
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class FullBackup(
    val entries: List<Entry>,
    val preferences: Preferences,
    val reminder: ReminderSettings,
    val createdAt: Instant = Instant.now(),
) {
    fun validate() {
        require(entries.size <= 100_000) { "A backup can contain at most 100000 check-ins." }
        entries.forEach(Metrics::validate)
        require(entries.map { it.date }.toSet().size == entries.size) {
            "The backup contains multiple check-ins for the same date."
        }
        Metrics.validatePreferences(preferences)
        reminder.validate()
    }
}

/** Portable ZIP container, never extracted to disk. CSV remains the measurement interchange format. */
object BackupCodec {
    const val MAX_BYTES = 16 * 1024 * 1024
    private const val MANIFEST = "manifest.properties"
    private const val ENTRIES = "entries.csv"
    private val keys = setOf(
        "format", "version", "created_at", "entry_count", "unit", "goal_kg", "height_cm",
        "dark_mode", "reminder_enabled", "reminder_hour", "reminder_minute",
    )

    fun encode(backup: FullBackup): ByteArray {
        backup.validate()
        require(backup.entries.sumOf { it.note.length.toLong() + 128 } <= MAX_BYTES) {
            "The journal is too large for a full backup (16 MB limit). No backup was written."
        }
        val csv = CsvCodec.encode(backup.entries).toByteArray(Charsets.UTF_8)
        val preferences = backup.preferences
        val manifest = """
            format=app.still.backup
            version=1
            created_at=${backup.createdAt}
            entry_count=${backup.entries.size}
            unit=${preferences.unit.name}
            goal_kg=${preferences.goalKg ?: ""}
            height_cm=${preferences.heightCm ?: ""}
            dark_mode=${preferences.darkMode}
            reminder_enabled=${backup.reminder.enabled}
            reminder_hour=${backup.reminder.hour}
            reminder_minute=${backup.reminder.minute}
        """.trimIndent().toByteArray(Charsets.UTF_8)
        require(csv.size.toLong() + manifest.size <= MAX_BYTES) { "The journal exceeds the 16 MB backup limit." }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((name, bytes) in listOf(MANIFEST to manifest, ENTRIES to csv)) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray().also {
            require(it.size <= MAX_BYTES) { "The backup exceeds the 16 MB file limit." }
        }
    }

    fun decode(input: InputStream): FullBackup {
        val bytes = readBounded(input, MAX_BYTES)
        val files = mutableMapOf<String, ByteArray>()
        var remaining = MAX_BYTES
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in setOf(MANIFEST, ENTRIES)) {
                    "Unsupported backup file. Use Data tools > Import measurements (CSV) for CSV files."
                }
                require(entry.name !in files) { "The backup contains duplicate files." }
                val content = readBounded(zip, minOf(remaining, if (entry.name == MANIFEST) 16_384 else MAX_BYTES))
                remaining -= content.size
                files[entry.name] = content
                zip.closeEntry()
            }
        }
        require(files.keys == setOf(MANIFEST, ENTRIES)) {
            "The backup is incomplete or unsupported. Choose a .still file."
        }
        val properties = object : Properties() {
            override fun put(key: Any, value: Any): Any? {
                require(!containsKey(key)) { "Duplicate backup setting: $key." }
                return super.put(key, value)
            }
        }
        properties.load(StringReader(utf8(files.getValue(MANIFEST))))
        require(properties.getProperty("format") == "app.still.backup") { "Unsupported backup format. Choose a .still file." }
        require(properties.getProperty("version") == "1") {
            "Unsupported backup version. Update the app before restoring this file."
        }
        require(properties.stringPropertyNames() == keys) { "The backup has missing or unrecognized settings." }
        fun field(name: String): String = properties.getProperty(name)
        fun boolean(name: String): Boolean = field(name).toBooleanStrictOrNull()
            ?: throw IllegalArgumentException("Invalid backup setting: $name.")
        fun integer(name: String): Int = field(name).toIntOrNull()
            ?: throw IllegalArgumentException("Invalid backup setting: $name.")
        fun optionalNumber(name: String): Double? = field(name).let {
            if (it.isEmpty()) null else it.toDoubleOrNull()
                ?: throw IllegalArgumentException("Invalid backup setting: $name.")
        }
        val csv = utf8(files.getValue(ENTRIES))
        require(csv.startsWith("date,weight_kg,body_fat_percent,waist_cm,note")) {
            "The backup measurement format is invalid."
        }
        val entries = CsvCodec.decode(csv)
        require(entries.size == integer("entry_count")) { "The backup check-in count does not match its contents." }
        val unit = WeightUnit.entries.find { it.name == field("unit") }
            ?: throw IllegalArgumentException("The backup weight unit is invalid.")
        return FullBackup(
            entries,
            Preferences(unit, optionalNumber("goal_kg"), optionalNumber("height_cm"), boolean("dark_mode")),
            ReminderSettings(boolean("reminder_enabled"), integer("reminder_hour"), integer("reminder_minute")),
            Instant.parse(field("created_at")),
        ).also(FullBackup::validate)
    }

    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            require(output.size().toLong() + count <= limit) { "The backup exceeds its supported size limit (up to 16 MB)." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun utf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
}
