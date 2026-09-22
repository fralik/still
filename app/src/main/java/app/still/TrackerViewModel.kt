package app.still

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.still.data.CsvCodec
import app.still.data.BackupCodec
import app.still.data.FullBackup
import app.still.data.Entry
import app.still.data.Preferences
import app.still.data.TrackerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class TrackerState(
    val entries: List<Entry> = emptyList(),
    val preferences: Preferences = Preferences(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val pendingImport: List<Entry>? = null,
    val reminder: ReminderSettings = ReminderSettings(),
    val pendingRestore: FullBackup? = null,
)

class TrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val store = TrackerStore(application)
    private val mutableState = MutableStateFlow(TrackerState())
    val state = mutableState.asStateFlow()

    init { reload() }

    fun clearError() = mutableState.update { it.copy(error = null) }
    fun clearMessage() = mutableState.update { it.copy(message = null) }
    fun cancelImport() = mutableState.update { it.copy(pendingImport = null) }
    fun cancelRestore() = mutableState.update { it.copy(pendingRestore = null) }
    fun notifyUser(message: String) = mutableState.update { it.copy(message = message) }

    fun refreshReminderSchedule() {
        val current = mutableState.value
        if (current.loading || current.busy) return
        // Do not acquire the operation gate during onResume: a document-picker result may follow it.
        try {
            Reminders.schedule(getApplication(), current.reminder)
        } catch (error: RuntimeException) {
            Log.e("Still", "Could not refresh reminder after returning to the app", error)
            mutableState.update {
                it.copy(error = "Your journal is unchanged, but Android could not update the reminder. Reopen the app to retry.")
            }
        }
    }

    private fun perform(work: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                work()
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("Still", "Tracking operation failed", error)
                mutableState.update {
                    it.copy(error = error.message ?: "Something went wrong. Your existing entries have not been removed.")
                }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun reload() = perform {
        val snapshot = withContext(Dispatchers.IO) { store.fullBackup() }
        mutableState.update {
            it.copy(entries = snapshot.entries, preferences = snapshot.preferences, reminder = snapshot.reminder, loading = false)
        }
        withContext(Dispatchers.IO) { Reminders.schedule(getApplication(), snapshot.reminder) }
    }

    fun saveEntry(entry: Entry, done: () -> Unit) = perform {
        val entries = withContext(Dispatchers.IO) {
            store.saveEntry(entry)
            store.entries()
        }
        mutableState.update { it.copy(entries = entries, message = "Check-in saved") }
        done()
    }

    fun deleteEntry(entry: Entry, done: () -> Unit) = perform {
        val entries = withContext(Dispatchers.IO) {
            store.deleteEntry(entry.id)
            store.entries()
        }
        mutableState.update { it.copy(entries = entries, message = "Entry deleted") }
        done()
    }

    fun savePreferences(preferences: Preferences, done: () -> Unit = {}) = perform {
        withContext(Dispatchers.IO) { store.savePreferences(preferences) }
        mutableState.update { it.copy(preferences = preferences) }
        done()
    }

    fun saveReminder(reminder: ReminderSettings) = perform {
        if (reminder.enabled) {
            require(Reminders.allowed(getApplication())) {
                "Notifications are disabled. Allow notifications for this app in Android Settings, then try again."
            }
        }
        withContext(Dispatchers.IO) { store.saveReminder(reminder) }
        mutableState.update { it.copy(reminder = reminder) }
        scheduleReminder(reminder, "Reminder settings saved")
    }

    fun createBackup(uri: Uri) = perform {
        val count = withContext(Dispatchers.IO) {
            val snapshot = store.fullBackup()
            val bytes = BackupCodec.encode(snapshot)
            val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Could not open the selected backup file.")
            try {
                stream.use { it.write(bytes) }
            } catch (error: IOException) {
                throw IOException("Backup could not be completed. Delete the incomplete file and try again.", error)
            }
            snapshot.entries.size
        }
        mutableState.update { it.copy(message = "Backup saved: $count check-ins and all settings.") }
    }

    fun prepareRestore(uri: Uri) = perform {
        val backup = withContext(Dispatchers.IO) {
            val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open the selected backup file.")
            stream.use(BackupCodec::decode)
        }
        mutableState.update { it.copy(pendingRestore = backup) }
    }

    fun confirmRestore() {
        val backup = mutableState.value.pendingRestore ?: return
        perform {
            val restored = withContext(Dispatchers.IO) { store.restoreBackup(backup) }
            val blocked = restored.reminder.enabled && !Reminders.allowed(getApplication())
            mutableState.update {
                it.copy(
                    entries = restored.entries, preferences = restored.preferences, reminder = restored.reminder,
                    pendingRestore = null,
                    message = if (blocked) "Backup restored. Reminders are paused until you allow notifications in Android Settings."
                    else "Restored ${restored.entries.size} check-ins and all settings.",
                )
            }
            scheduleReminder(restored.reminder, "Your journal and settings were restored")
        }
    }

    private suspend fun scheduleReminder(reminder: ReminderSettings, completed: String) {
        try {
            withContext(Dispatchers.IO) { Reminders.schedule(getApplication(), reminder) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: RuntimeException) {
            throw IOException("$completed, but Android could not update the reminder. Reopen the app to retry.", error)
        }
    }

    fun export(uri: Uri) = perform {
        val snapshot = mutableState.value.entries
        withContext(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("Could not open the selected file.")
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(CsvCodec.encode(snapshot)) }
        }
        mutableState.update { it.copy(message = "Exported ${snapshot.size} entries") }
    }

    fun prepareImport(uri: Uri) = perform {
        val entries = withContext(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val stream = resolver.openInputStream(uri) ?: throw IOException("Could not open the selected file.")
            // Bound untrusted files before decoding; a CSV backup does not need unbounded memory.
            val text = stream.bufferedReader(Charsets.UTF_8).use { reader ->
                val result = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (result.length + count > 10_000_000) throw IOException("This file is too large. Choose a CSV smaller than 10 MB.")
                    result.append(buffer, 0, count)
                }
                result.toString()
            }
            CsvCodec.decode(text)
        }
        mutableState.update { it.copy(pendingImport = entries) }
    }

    fun confirmImport() {
        val pending = mutableState.value.pendingImport ?: return
        perform {
            val (count, entries) = withContext(Dispatchers.IO) {
                val count = store.importEntries(pending)
                count to store.entries()
            }
            mutableState.update {
                it.copy(entries = entries, pendingImport = null, message = "Imported $count entries. Existing dates kept unchanged.")
            }
        }
    }

    override fun onCleared() {
        store.close()
        super.onCleared()
    }
}
