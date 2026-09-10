package app.still.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

class TrackerStore(context: Context) :
    SQLiteOpenHelper(context, "still.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL UNIQUE,
                weight_kg REAL NOT NULL,
                body_fat REAL,
                waist_cm REAL,
                note TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE preferences (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                unit TEXT NOT NULL,
                goal_kg REAL,
                height_cm REAL,
                dark_mode INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.insertOrThrow("preferences", null, preferenceValues(Preferences()))
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Unsupported database upgrade from $oldVersion to $newVersion.")
    }

    fun entries(): List<Entry> =
        readableDatabase.query("entries", null, null, null, null, null, "date DESC, id DESC").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Entry(
                            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                            date = LocalDate.parse(cursor.getString(cursor.getColumnIndexOrThrow("date"))),
                            weightKg = cursor.getDouble(cursor.getColumnIndexOrThrow("weight_kg")),
                            bodyFat = cursor.nullableDouble("body_fat"),
                            waistCm = cursor.nullableDouble("waist_cm"),
                            note = cursor.getString(cursor.getColumnIndexOrThrow("note")),
                        ),
                    )
                }
            }
        }

    fun saveEntry(entry: Entry) {
        Metrics.validate(entry)
        require(entry.id >= 0) { "Entry ID must not be negative." }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val collision = db.query(
                "entries", arrayOf("id"), "date = ? AND id <> ?",
                arrayOf(entry.date.toString(), entry.id.toString()), null, null, null,
            ).use { it.moveToFirst() }
            require(!collision) { "An entry already exists for ${entry.date}. Edit that entry instead." }
            if (entry.id == 0L) {
                db.insertOrThrow("entries", null, entryValues(entry))
            } else {
                require(
                    db.update("entries", entryValues(entry), "id = ?", arrayOf(entry.id.toString())) == 1,
                ) { "Entry ${entry.id} no longer exists." }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteEntry(id: Long) {
        writableDatabase.delete("entries", "id = ?", arrayOf(id.toString()))
    }

    fun preferences(): Preferences =
        readableDatabase.query(
            "preferences", null, "id = ?", arrayOf("1"), null, null, null,
        ).use { cursor ->
            check(cursor.moveToFirst()) { "Preferences are missing from the database." }
            Preferences(
                unit = WeightUnit.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("unit"))),
                goalKg = cursor.nullableDouble("goal_kg"),
                heightCm = cursor.nullableDouble("height_cm"),
                darkMode = cursor.getInt(cursor.getColumnIndexOrThrow("dark_mode")) != 0,
            )
        }

    fun savePreferences(value: Preferences) {
        Metrics.validatePreferences(value)
        check(
            writableDatabase.update(
                "preferences", preferenceValues(value), "id = ?", arrayOf("1"),
            ) == 1,
        ) { "Preferences are missing from the database." }
    }

    fun importEntries(entries: List<Entry>): Int {
        require(entries.size <= 100_000) { "Import must contain at most 100000 entries." }
        val validated = entries.toList()
        validated.forEach(Metrics::validate)
        val db = writableDatabase
        var inserted = 0
        db.beginTransaction()
        try {
            for (entry in validated) {
                val exists = db.query(
                    "entries", arrayOf("id"), "date = ?", arrayOf(entry.date.toString()),
                    null, null, null,
                ).use { it.moveToFirst() }
                if (!exists) {
                    db.insertOrThrow("entries", null, entryValues(entry))
                    inserted++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return inserted
    }

    private fun entryValues(entry: Entry) = ContentValues().apply {
        put("date", entry.date.toString())
        put("weight_kg", entry.weightKg)
        put("body_fat", entry.bodyFat)
        put("waist_cm", entry.waistCm)
        put("note", entry.note)
    }

    private fun preferenceValues(value: Preferences) = ContentValues().apply {
        put("id", 1)
        put("unit", value.unit.name)
        put("goal_kg", value.goalKg)
        put("height_cm", value.heightCm)
        put("dark_mode", if (value.darkMode) 1 else 0)
    }

    private fun Cursor.nullableDouble(name: String): Double? {
        val index = getColumnIndexOrThrow(name)
        return if (isNull(index)) null else getDouble(index)
    }
}
