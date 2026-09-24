package app.still

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LegacyReminderCleanupTest {
    @Test
    fun removesBothOldAlarmIdentitiesChannelAndPreferencesAndCanRunAgain() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        check(base.packageName == "com.vadimfrolov.still.debug")
        val name = "cleanup_test_${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(ignored: String, mode: Int) = base.getSharedPreferences(name, mode)
            override fun deleteSharedPreferences(ignored: String) = base.deleteSharedPreferences(name)
        }
        val notifications = base.getSystemService(NotificationManager::class.java)
        val alarms = base.getSystemService(AlarmManager::class.java)
        val intents = listOf("app.still.DAILY_CHECK_IN", "${base.packageName}.DAILY_CHECK_IN").map {
            Intent(it).setClassName(base.packageName, "app.still.ReminderReceiver")
        }
        val pending = intents.map {
            checkNotNull(PendingIntent.getBroadcast(base, 104, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        try {
            pending.forEach { alarms.set(AlarmManager.RTC, System.currentTimeMillis() + 86_400_000, it) }
            notifications.createNotificationChannel(NotificationChannel("daily_check_in", "Retired test reminder", NotificationManager.IMPORTANCE_DEFAULT))
            assertTrue(context.getSharedPreferences("reminder", Context.MODE_PRIVATE).edit().putBoolean("enabled", true).commit())
            LegacyReminderCleanup.run(context)
            LegacyReminderCleanup.run(context)
            intents.forEach {
                assertNull(PendingIntent.getBroadcast(base, 104, it, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
            }
            assertNull(notifications.getNotificationChannel("daily_check_in"))
            assertTrue(context.getSharedPreferences("reminder", Context.MODE_PRIVATE).all.isEmpty())
        } finally {
            pending.forEach { alarms.cancel(it); it.cancel() }
            notifications.deleteNotificationChannel("daily_check_in")
            check(base.deleteSharedPreferences(name))
        }
    }
}
