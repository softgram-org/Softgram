package desu.inugram.helpers.academics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Alarm and the two notification buttons (cross = archive, 2 = ring again in two minutes)
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminder = ReminderStore.find(intent.getLongExtra(ReminderScheduler.EXTRA_ID, 0L)) ?: return
        when (intent.action) {
            ReminderScheduler.ACTION_FIRE -> ReminderNotifier.show(context, reminder)

            ReminderScheduler.ACTION_DISMISS -> {
                ReminderNotifier.cancel(context, reminder)
                reminder.state = ReminderStore.ARCHIVED
                ReminderStore.save()
            }

            ReminderScheduler.ACTION_SNOOZE -> {
                ReminderNotifier.cancel(context, reminder)
                reminder.time = System.currentTimeMillis() + ReminderScheduler.SNOOZE_MS
                reminder.state = ReminderStore.ACTIVE
                ReminderStore.save()
                ReminderScheduler.schedule(reminder)
            }
        }
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) ReminderScheduler.restoreAll(context)
    }
}
