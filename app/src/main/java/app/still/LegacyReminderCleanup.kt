package app.still

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Removes OS state left by versions that supported reminders; never schedules anything. */
internal object LegacyReminderCleanup {
    fun run(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        for (action in setOf("app.still.DAILY_CHECK_IN", "${context.packageName}.DAILY_CHECK_IN")) {
            val intent = Intent(action).setClassName(context.packageName, "app.still.ReminderReceiver")
            PendingIntent.getBroadcast(context, 104, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
                alarms.cancel(it)
                it.cancel()
            }
        }
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.cancel(104)
        notifications.deleteNotificationChannel("daily_check_in")
        if (context.getSharedPreferences("reminder", Context.MODE_PRIVATE).all.isNotEmpty()) {
            check(context.deleteSharedPreferences("reminder")) { "Could not remove retired reminder settings. Reopen the app to retry." }
        }
    }
}
