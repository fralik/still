package app.still

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.IOException
import java.time.ZonedDateTime

data class ReminderSettings(val enabled: Boolean = false, val hour: Int = 8, val minute: Int = 0)

object Reminders {
    private const val CHANNEL = "daily_check_in"
    private const val ACTION = "app.still.DAILY_CHECK_IN"
    private const val ID = 104

    fun settings(context: Context): ReminderSettings {
        val prefs = context.getSharedPreferences("reminder", Context.MODE_PRIVATE)
        return ReminderSettings(prefs.getBoolean("enabled", false), prefs.getInt("hour", 8), prefs.getInt("minute", 0))
    }

    fun allowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        val permission = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permission && manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun save(context: Context, value: ReminderSettings) {
        require(value.hour in 0..23 && value.minute in 0..59) { "Choose a valid reminder time." }
        if (value.enabled) {
            require(allowed(context)) { "Notifications are disabled. Enable Still notifications in Android Settings, then try again." }
            schedule(context, value)
        } else {
            context.getSystemService(AlarmManager::class.java).cancel(pending(context))
        }
        val saved = context.getSharedPreferences("reminder", Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", value.enabled).putInt("hour", value.hour).putInt("minute", value.minute).commit()
        if (!saved) {
            context.getSystemService(AlarmManager::class.java).cancel(pending(context))
            throw IOException("Couldn't save the reminder. Please try again.")
        }
    }

    internal fun nextTime(value: ReminderSettings, now: ZonedDateTime): ZonedDateTime {
        val today = now.toLocalDate().atTime(value.hour, value.minute).atZone(now.zone)
        return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(value.hour, value.minute).atZone(now.zone)
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, ID, Intent(context, ReminderReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(context: Context, value: ReminderSettings = settings(context)) {
        if (!value.enabled) return
        val time = nextTime(value, ZonedDateTime.now()).toInstant().toEpochMilli()
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending(context))
    }

    @SuppressLint("MissingPermission") // allowed() checks both runtime permission and channel settings.
    fun receive(context: Context, action: String?) {
        val value = settings(context)
        if (!value.enabled) return
        if (action == ACTION && allowed(context)) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Daily check-in", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(
                context, ID, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            manager.notify(ID, Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("A little time for you")
                .setContentText("Your daily check-in is here when you're ready.")
                .setContentIntent(open).setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .build())
        }
        schedule(context, value)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Reminders.receive(context, intent.action)
        } catch (error: RuntimeException) {
            Log.e("Still", "Could not deliver or reschedule check-in reminder", error)
        }
    }
}
