package desu.inugram.helpers.academics

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.telegram.messenger.ApplicationLoader

// Rings a reminder at its time, even when the app is closed.
object ReminderScheduler {
    const val ACTION_FIRE = "desu.inugram.reminder.FIRE"
    const val ACTION_DISMISS = "desu.inugram.reminder.DISMISS"
    const val ACTION_SNOOZE = "desu.inugram.reminder.SNOOZE"
    const val EXTRA_ID = "reminder_id"
    const val SNOOZE_MS = 2 * 60 * 1000L

    fun pending(context: Context, id: Long, action: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(reminder: ReminderStore.Reminder) {
        val context = ApplicationLoader.applicationContext
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pending(context, reminder.id, ACTION_FIRE)
        val exact = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
        try {
            if (exact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.time, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.time, pending)
            }
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.time, pending)
        }
    }

    fun cancel(reminder: ReminderStore.Reminder) {
        val context = ApplicationLoader.applicationContext
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.cancel(pending(context, reminder.id, ACTION_FIRE))
    }

    /** puts every waiting reminder back after a restart of the phone; reminders that passed meanwhile ring now */
    fun restoreAll(context: Context) {
        val now = System.currentTimeMillis()
        for (reminder in ReminderStore.all().toList()) {
            if (reminder.state != ReminderStore.ACTIVE) continue
            if (reminder.time <= now) ReminderNotifier.show(context, reminder) else schedule(reminder)
        }
    }
}
