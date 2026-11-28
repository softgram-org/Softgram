package desu.inugram.helpers.academics

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.LaunchActivity

// The reminder notification: a bell, the note, and two round buttons on the right
object ReminderNotifier {
    private const val CHANNEL = "softgram_reminders"

    private fun notificationId(reminder: ReminderStore.Reminder) = (reminder.id % Int.MAX_VALUE).toInt()

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL) != null) return
        val channel = NotificationChannel(CHANNEL, LocaleController.getString(R.string.InuReminderChannel), NotificationManager.IMPORTANCE_HIGH)
        channel.enableVibration(true)
        manager.createNotificationChannel(channel)
    }

    private fun openChat(context: Context, reminder: ReminderStore.Reminder): PendingIntent {
        val intent = Intent(context, LaunchActivity::class.java)
        intent.action = "com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        intent.putExtra("currentAccount", reminder.account)
        if (reminder.dialogId < 0) intent.putExtra("chatId", -reminder.dialogId) else intent.putExtra("userId", reminder.dialogId)
        if (reminder.messageId != 0) intent.putExtra("messageId", reminder.messageId)
        return PendingIntent.getActivity(context, notificationId(reminder), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun show(context: Context, reminder: ReminderStore.Reminder) {
        ensureChannel(context)
        val view = RemoteViews(context.packageName, R.layout.softgram_reminder_notification)
        view.setTextViewText(R.id.reminder_title, LocaleController.getString(R.string.InuReminderTitle))
        view.setTextViewText(R.id.reminder_text, reminder.text.ifEmpty { LocaleController.getString(R.string.InuReminderNoNote) })
        view.setOnClickPendingIntent(R.id.reminder_close, ReminderScheduler.pending(context, reminder.id, ReminderScheduler.ACTION_DISMISS))
        view.setOnClickPendingIntent(R.id.reminder_snooze, ReminderScheduler.pending(context, reminder.id, ReminderScheduler.ACTION_SNOOZE))
        view.setContentDescription(R.id.reminder_close, LocaleController.getString(R.string.InuReminderDismiss))
        view.setContentDescription(R.id.reminder_snooze, LocaleController.getString(R.string.InuReminderSnooze))

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.notification)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(view)
            .setContentIntent(openChat(context, reminder))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setWhen(reminder.time)
            .setShowWhen(true)
            .build()
        reminder.state = ReminderStore.FIRED
        ReminderStore.save()
        try {
            NotificationManagerCompat.from(context).notify(notificationId(reminder), notification)
        } catch (e: SecurityException) {
            android.util.Log.e("ReminderNotifier", "notifications are not allowed", e)
        }
    }

    fun cancel(context: Context, reminder: ReminderStore.Reminder) {
        NotificationManagerCompat.from(context).cancel(notificationId(reminder))
    }
}
