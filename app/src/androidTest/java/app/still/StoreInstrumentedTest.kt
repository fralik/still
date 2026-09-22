package app.still

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.still.data.Entry
import app.still.data.Preferences
import app.still.data.TrackerStore
import app.still.data.WeightUnit
import app.still.data.ThemeMode
import app.still.data.BackupCodec
import app.still.data.FullBackup
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StoreInstrumentedTest {
    private lateinit var context: IsolatedContext
    private lateinit var store: TrackerStore

    @Before
    fun setUp() {
        context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        store = TrackerStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.cleanUp()
    }

    @Test
    fun savesEditsDeletesAndSurvivesReopening() {
        val today = LocalDate.now()
        store.saveEntry(Entry(date = today, weightKg = 72.4, bodyFat = 21.5, waistCm = 81.2, note = "Morning"))
        val saved = store.entries().single()
        assertTrue(saved.id > 0)
        store.saveEntry(saved.copy(weightKg = 72.1, note = "Edited"))
        store.savePreferences(Preferences(WeightUnit.LB, 70.0, 175.0, ThemeMode.DARK))
        store.close()
        store = TrackerStore(context)
        assertEquals(72.1, store.entries().single().weightKg, 0.00001)
        assertEquals("Edited", store.entries().single().note)
        assertEquals(Preferences(WeightUnit.LB, 70.0, 175.0, ThemeMode.DARK), store.preferences())
        store.deleteEntry(saved.id)
        assertTrue(store.entries().isEmpty())
    }

    @Test
    fun duplicateDatesNeverOverwriteAndImportSkipsExisting() {
        val today = LocalDate.now()
        store.saveEntry(Entry(date = today, weightKg = 72.0))
        assertThrows(IllegalArgumentException::class.java) {
            store.saveEntry(Entry(date = today, weightKg = 99.0))
        }
        val count = store.importEntries(listOf(
            Entry(date = today, weightKg = 99.0),
            Entry(date = today.minusDays(1), weightKg = 73.0),
            Entry(date = today.minusDays(1), weightKg = 74.0),
        ))
        assertEquals(1, count)
        assertEquals(2, store.entries().size)
        assertEquals(72.0, store.entries().first().weightKg, 0.0)
        val older = store.entries().last()
        assertThrows(IllegalArgumentException::class.java) {
            store.saveEntry(older.copy(date = today))
        }
        assertEquals(older, store.entries().last())
    }

    @Test
    fun invalidImportIsAtomic() {
        val today = LocalDate.now()
        store.saveEntry(Entry(date = today, weightKg = 72.0))
        assertThrows(IllegalArgumentException::class.java) {
            store.importEntries(listOf(
                Entry(date = today.minusDays(2), weightKg = 73.0),
                Entry(date = today.minusDays(1), weightKg = Double.NaN),
            ))
        }
        assertEquals(1, store.entries().size)
        assertEquals(today, store.entries().single().date)
    }

    @Test
    fun fullBackupMovesEverySettingAndEntryToAnotherStore() {
        store.saveEntry(Entry(date = LocalDate.now(), weightKg = 72.123456, bodyFat = 21.5, waistCm = 81.2, note = "First\nsecond"))
        store.savePreferences(Preferences(WeightUnit.LB, 70.0, 175.0, ThemeMode.DARK))
        store.saveReminder(ReminderSettings(true, 19, 35))
        val original = store.fullBackup()
        val backup = BackupCodec.decode(BackupCodec.encode(original).inputStream())
        val destination = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            TrackerStore(destination).use { target ->
                target.saveEntry(Entry(date = LocalDate.now().minusDays(1), weightKg = 90.0))
                target.restoreBackup(backup)
            }

            TrackerStore(destination).use { target ->
                assertEquals(original.entries.map { it.copy(id = 0) }, target.entries().map { it.copy(id = 0) })
                assertEquals(original.preferences, target.preferences())
                assertEquals(original.reminder, target.reminder())
            }
            assertEquals(original.entries, store.entries())
        } finally {
            destination.cleanUp()
        }
    }

    @Test
    fun fullBackupRoundTripsThroughContentResolverStreams() {
        store.saveEntry(Entry(date = LocalDate.of(2020, 1, 2), weightKg = 72.5, note = "Exported note"))
        store.savePreferences(Preferences(WeightUnit.LB, 70.0, 175.0, ThemeMode.DARK))
        store.saveReminder(ReminderSettings(true, 20, 45))
        val snapshot = store.fullBackup()
        val file = File.createTempFile("still-backup-test-", ".still", context.cacheDir)
        try {
            val uri = Uri.fromFile(file)
            val resolver = context.contentResolver
            checkNotNull(resolver.openOutputStream(uri, "wt")).use { it.write(BackupCodec.encode(snapshot)) }
            val decoded = checkNotNull(resolver.openInputStream(uri)).use(BackupCodec::decode)
            assertEquals(snapshot.copy(entries = snapshot.entries.map { it.copy(id = 0) }), decoded)
        } finally {
            check(file.delete()) { "Could not remove the test backup." }
        }
    }

    @Test
    fun emptyBackupExplicitlyClearsJournalAndResetsAllSettings() {
        store.saveEntry(Entry(date = LocalDate.now(), weightKg = 72.0))
        store.savePreferences(Preferences(WeightUnit.LB, 70.0, 175.0, ThemeMode.DARK))
        store.saveReminder(ReminderSettings(true, 19, 35))
        store.restoreBackup(FullBackup(emptyList(), Preferences(), ReminderSettings()))
        assertTrue(store.entries().isEmpty())
        assertEquals(Preferences(), store.preferences())
        assertEquals(ReminderSettings(), store.reminder())
    }

    @Test
    fun invalidBackupCannotChangeAnyExistingData() {
        store.saveEntry(Entry(date = LocalDate.now(), weightKg = 72.0))
        store.saveReminder(ReminderSettings(true, 18, 20))
        val original = store.fullBackup()
        val invalid = original.copy(preferences = Preferences(heightCm = -1.0))
        assertThrows(IllegalArgumentException::class.java) { store.restoreBackup(invalid) }
        val duplicate = original.copy(entries = original.entries + original.entries)
        assertThrows(IllegalArgumentException::class.java) { store.restoreBackup(duplicate) }
        assertEquals(original.entries, store.entries())
        assertEquals(original.preferences, store.preferences())
        assertEquals(original.reminder, store.reminder())
    }

    @Test
    fun databaseFailureRollsBackEntriesPreferencesAndReminderTogether() {
        store.saveEntry(Entry(date = LocalDate.now(), weightKg = 72.0))
        val original = store.fullBackup()
        val replacement = FullBackup(
            listOf(Entry(date = LocalDate.now().minusDays(1), weightKg = 90.0)),
            Preferences(WeightUnit.LB, 70.0, 175.0, ThemeMode.DARK),
            ReminderSettings(true, 17, 30),
        )
        store.writableDatabase.execSQL(
            "CREATE TRIGGER fail_restore BEFORE UPDATE ON reminder BEGIN SELECT RAISE(ABORT, 'Injected failure'); END",
        )
        assertThrows(android.database.sqlite.SQLiteException::class.java) { store.restoreBackup(replacement) }
        assertEquals(original.entries, store.entries())
        assertEquals(original.preferences, store.preferences())
        assertEquals(original.reminder, store.reminder())
    }

    @Test
    fun upgradesVersionOneWithoutLosingLegacyEntriesOrReminderSettings() {
        createLegacyDatabase(1, true)
        assertTrue(context.getSharedPreferences("reminder", Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", true).putInt("hour", 22).putInt("minute", 45).commit())
        store = TrackerStore(context)
        assertEquals("Existing journal", store.entries().single().note)
        assertEquals(42L, store.entries().single().id)
        assertEquals(Preferences(WeightUnit.LB, 70.0, 175.0, ThemeMode.DARK), store.preferences())
        assertEquals(ReminderSettings(true, 22, 45), store.reminder())
        store.saveReminder(ReminderSettings(false, 7, 15))
        store.close()
        store = TrackerStore(context)
        assertEquals(ReminderSettings(false, 7, 15), store.reminder())
        assertEquals(3, store.readableDatabase.version)
    }

    @Test
    fun versionTwoDefaultBecomesSystemWithoutChangingOtherSettings() {
        createLegacyDatabase(2, false)
        store = TrackerStore(context)
        assertEquals(Preferences(WeightUnit.LB, 70.0, 175.0, ThemeMode.SYSTEM), store.preferences())
        assertEquals(ReminderSettings(true, 22, 45), store.reminder())
        assertEquals("Existing journal", store.entries().single().note)
        assertEquals(3, store.readableDatabase.version)
    }

    @Test
    fun versionTwoExplicitDarkSelectionIsPreserved() {
        createLegacyDatabase(2, true)
        store = TrackerStore(context)
        assertEquals(ThemeMode.DARK, store.preferences().themeMode)
        assertEquals(ReminderSettings(true, 22, 45), store.reminder())
    }

    @Test
    fun versionOneDefaultCanUpgradeDirectlyToSystemTheme() {
        createLegacyDatabase(1, false)
        store = TrackerStore(context)
        assertEquals(ThemeMode.SYSTEM, store.preferences().themeMode)
        assertEquals(ReminderSettings(), store.reminder())
    }

    @Test
    fun themeDefaultsToSystemAndAllOverridesSurviveReopeningAndRestoration() {
        assertEquals(ThemeMode.SYSTEM, store.preferences().themeMode)
        for (mode in ThemeMode.entries) {
            store.savePreferences(Preferences(themeMode = mode))
            val snapshot = store.fullBackup()
            store.close()
            store = TrackerStore(context)
            assertEquals(mode, store.preferences().themeMode)
            store.savePreferences(Preferences())
            store.restoreBackup(snapshot)
            assertEquals(mode, store.preferences().themeMode)
        }
    }

    private fun createLegacyDatabase(version: Int, dark: Boolean) {
        store.close()
        context.openOrCreateDatabase("still.db", Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL UNIQUE, weight_kg REAL NOT NULL, body_fat REAL, waist_cm REAL, note TEXT NOT NULL)")
            db.execSQL("CREATE TABLE preferences (id INTEGER PRIMARY KEY CHECK (id = 1), unit TEXT NOT NULL, goal_kg REAL, height_cm REAL, dark_mode INTEGER NOT NULL)")
            db.execSQL("INSERT INTO entries VALUES (42, '2020-01-02', 72.4, 21.5, 81.2, 'Existing journal')")
            db.execSQL("INSERT INTO preferences VALUES (1, 'LB', 70.0, 175.0, ?)", arrayOf(if (dark) 1 else 0))
            if (version == 2) {
                db.execSQL("CREATE TABLE reminder (id INTEGER PRIMARY KEY CHECK (id = 1), enabled INTEGER NOT NULL, hour INTEGER NOT NULL, minute INTEGER NOT NULL)")
                db.execSQL("INSERT INTO reminder VALUES (1, 1, 22, 45)")
            }
            db.version = version
        }
    }
}

// Every test gets its own files; never clear or seed the user's journal.
private class IsolatedContext(base: Context) : ContextWrapper(base) {
    private val prefix = "instrumentation_${UUID.randomUUID()}_"
    private val databases = mutableSetOf<String>()
    private val preferences = mutableSetOf<String>()

    override fun getApplicationContext(): Context = this

    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
        super.openOrCreateDatabase(prefix + name.also { databases.add(it) }, mode, factory)

    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
        super.openOrCreateDatabase(prefix + name.also { databases.add(it) }, mode, factory, errorHandler)

    override fun getDatabasePath(name: String): File = super.getDatabasePath(prefix + name)

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        super.getSharedPreferences(prefix + name.also { preferences.add(it) }, mode)

    fun cleanUp() {
        databases.forEach { baseContext.deleteDatabase(prefix + it) }
        preferences.forEach { baseContext.deleteSharedPreferences(prefix + it) }
    }
}
