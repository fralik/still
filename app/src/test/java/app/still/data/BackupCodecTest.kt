package app.still.data

import app.still.ReminderSettings
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupCodecTest {
    private val backup = FullBackup(
        listOf(Entry(
            date = LocalDate.of(2020, 1, 2), weightKg = 72.123456789,
            bodyFat = 21.123456, waistCm = 81.123456, note = "A, \"note\"\r\n\u00e9t\u00e9 \u2600 |",
        )),
        Preferences(WeightUnit.LB, 70.987654321, 175.123456, ThemeMode.DARK),
        ReminderSettings(true, 23, 59),
        Instant.parse("2026-09-22T07:45:00Z"),
    )

    @Test
    fun allMeasurementsSettingsAndCreationDateRoundTripExactly() {
        assertEquals(backup, decode(BackupCodec.encode(backup)))
    }

    @Test
    fun emptyJournalAndClearedPreferencesRoundTrip() {
        val empty = backup.copy(entries = emptyList(), preferences = Preferences(), reminder = ReminderSettings())
        assertEquals(empty, decode(BackupCodec.encode(empty)))
    }

    @Test
    fun localDatabaseIdsAreNotTransferred() {
        val withIds = backup.copy(entries = backup.entries.map { it.copy(id = 999) })
        assertEquals(backup, decode(BackupCodec.encode(withIds)))
    }

    @Test
    fun missingInvalidUnknownAndRepeatedSettingsAreRejected() {
        val changes = listOf(
            "version=2" to "version=99",
            "format=app.still.backup" to "format=other",
            "unit=LB" to "unit=stones",
            "theme_mode=DARK" to "theme_mode=unknown",
            "goal_kg=70.987654321" to "goal_kg=NaN",
            "height_cm=175.123456" to "height_cm=Infinity",
            "reminder_hour=23" to "reminder_hour=24",
            "reminder_minute=59" to "reminder_minute=-1",
            "reminder_enabled=true" to "reminder_enabled=",
            "entry_count=1" to "entry_count=2",
            "entry_count=1" to "entry_count=0",
            "unit=LB" to "",
            "unit=LB" to "unit=LB\nunit=KG",
            "unit=LB" to "unit=LB\nunexpected=true",
            "created_at=2026-09-22T07:45:00Z" to "created_at=not-a-date",
        )
        for ((before, after) in changes) {
            val files = unpack()
            files["manifest.properties"] = files.getValue("manifest.properties").toString(Charsets.UTF_8)
                .replace(before, after).toByteArray(Charsets.UTF_8)
            assertThrows("Must reject $after", RuntimeException::class.java) { decode(zip(files)) }
        }
    }

    @Test
    fun rejectsCsvWrongArchiveMissingFilesAndUnexpectedPaths() {
        assertThrows(IllegalArgumentException::class.java) { decode(CsvCodec.encode(backup.entries).toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { decode(byteArrayOf()) }
        for (files in listOf(
            unpack().apply { remove("entries.csv") },
            unpack().apply { remove("manifest.properties") },
            unpack().apply { put("../escaped", byteArrayOf(1)) },
        )) {
            assertThrows(IllegalArgumentException::class.java) { decode(zip(files)) }
        }
    }

    @Test
    fun duplicateDatesAreRejectedInsteadOfSilentlyMerged() {
        val duplicate = backup.copy(entries = backup.entries + backup.entries)
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.encode(duplicate) }
        val files = unpack()
        files["entries.csv"] = CsvCodec.encode(duplicate.entries).toByteArray()
        files["manifest.properties"] = files.getValue("manifest.properties").toString(Charsets.UTF_8)
            .replace("entry_count=1", "entry_count=2").toByteArray()
        assertThrows(IllegalArgumentException::class.java) { decode(zip(files)) }
    }

    @Test
    fun rejectsInvalidValuesBeforeWriting() {
        for (invalid in listOf(
            backup.copy(entries = listOf(backup.entries.single().copy(weightKg = Double.NaN))),
            backup.copy(preferences = backup.preferences.copy(goalKg = -1.0)),
            backup.copy(reminder = ReminderSettings(true, -1, 0)),
            backup.copy(entries = listOf(backup.entries.single().copy(date = LocalDate.now().plusDays(1)))),
        )) {
            assertThrows(IllegalArgumentException::class.java) { BackupCodec.encode(invalid) }
        }
    }

    @Test
    fun rejectsCompressedBombAndOversizeInput() {
        val files = unpack()
        files["entries.csv"] = ByteArray(BackupCodec.MAX_BYTES + 1) { 'a'.code.toByte() }
        assertThrows(IllegalArgumentException::class.java) { decode(zip(files)) }
        assertThrows(IllegalArgumentException::class.java) { decode(ByteArray(BackupCodec.MAX_BYTES + 1)) }
    }

    @Test
    fun rejectsTruncatedArchiveAndMalformedUtf8() {
        val bytes = BackupCodec.encode(backup)
        assertThrows(Exception::class.java) { decode(bytes.copyOf(bytes.size / 2)) }
        val files = unpack()
        files["entries.csv"] = byteArrayOf(0xC3.toByte(), 0x28)
        assertThrows(java.nio.charset.CharacterCodingException::class.java) { decode(zip(files)) }
    }

    @Test
    fun everyThemeModeRoundTripsIncludingSystemDefault() {
        for (mode in ThemeMode.entries) {
            val snapshot = backup.copy(preferences = backup.preferences.copy(themeMode = mode))
            assertEquals(snapshot, decode(BackupCodec.encode(snapshot)))
        }
    }

    @Test
    fun versionOneBackupsPreserveTheirOriginalExplicitAppearance() {
        for ((old, expected) in listOf("true" to ThemeMode.DARK, "false" to ThemeMode.LIGHT)) {
            val files = unpack()
            files["manifest.properties"] = files.getValue("manifest.properties").toString(Charsets.UTF_8)
                .replace("version=2", "version=1").replace("theme_mode=DARK", "dark_mode=$old").toByteArray()
            assertEquals(backup.copy(preferences = backup.preferences.copy(themeMode = expected)), decode(zip(files)))
        }
    }

    @Test
    fun themeFieldsCannotBeMissingOrMixedAcrossBackupVersions() {
        for (replacement in listOf("", "dark_mode=true", "theme_mode=DARK\ndark_mode=true")) {
            val files = unpack()
            files["manifest.properties"] = files.getValue("manifest.properties").toString(Charsets.UTF_8)
                .replace("theme_mode=DARK", replacement).toByteArray()
            assertThrows(IllegalArgumentException::class.java) { decode(zip(files)) }
        }
        val files = unpack()
        files["manifest.properties"] = files.getValue("manifest.properties").toString(Charsets.UTF_8)
            .replace("version=2", "version=1").replace("theme_mode=DARK", "dark_mode=maybe").toByteArray()
        assertThrows(IllegalArgumentException::class.java) { decode(zip(files)) }
    }

    private fun decode(bytes: ByteArray) = BackupCodec.decode(ByteArrayInputStream(bytes))

    private fun unpack(): MutableMap<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(BackupCodec.encode(backup))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes()
            }
        }
        return result
    }

    private fun zip(files: Map<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}
