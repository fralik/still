package app.still

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.still.data.Entry
import app.still.data.Preferences
import app.still.data.TrackerStore
import app.still.data.WeightUnit
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
        store.savePreferences(Preferences(WeightUnit.LB, 70.0, 175.0, true))
        store.close()
        store = TrackerStore(context)
        assertEquals(72.1, store.entries().single().weightKg, 0.00001)
        assertEquals("Edited", store.entries().single().note)
        assertEquals(Preferences(WeightUnit.LB, 70.0, 175.0, true), store.preferences())
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
