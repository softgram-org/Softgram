package desu.inugram.helpers.academics

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessageObject
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.AlertsCreator
import java.util.Date

// "Add to calendar": Telegram's own date and time picker (the one used for reminders and scheduled messages),
// with a note on top and a translucent, blurred sheet like the message menu.
object ReminderSheet {
    fun show(activity: ChatActivity, message: MessageObject) {
        val context = activity.parentActivity ?: return
        val provider = activity.resourceProvider
        val account = activity.currentAccount

        val note = EditText(context)
        note.setText((message.messageText?.toString() ?: "").trim().take(140))
        note.hint = LocaleController.getString(R.string.InuReminderNoteHint)
        note.textSize = 16f
        note.maxLines = 3
        note.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        note.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, provider))
        note.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint, provider))
        val shape = GradientDrawable()
        shape.cornerRadius = AndroidUtilities.dp(12f).toFloat()
        shape.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_dialogTextBlack, provider), 0x14))
        note.background = shape
        note.setPadding(AndroidUtilities.dp(14f), AndroidUtilities.dp(10f), AndroidUtilities.dp(14f), AndroidUtilities.dp(10f))
        AlertsCreator.inu_extraView = note

        val base = AlertsCreator.ScheduleDatePickerColors(provider)
        val colors = AlertsCreator.ScheduleDatePickerColors(
            base.textColor, ColorUtils.setAlphaComponent(base.backgroundColor, 0xCC), base.iconColor, base.iconSelectorColor,
            base.subMenuTextColor, base.subMenuBackgroundColor, base.subMenuSelectorColor,
            base.buttonTextColor, base.buttonBackgroundColor, base.buttonBackgroundPressedColor,
        )
        val selfId = UserConfig.getInstance(account).getClientUserId()
        val builder = AlertsCreator.createScheduleDatePickerDialog(
            context, selfId, -1L, 0,
            AlertsCreator.ScheduleDatePickerDelegate { _, scheduleDate, _ ->
                val time = scheduleDate * 1000L
                val reminder = ReminderStore.add(account, message.dialogId, message.id, note.text.toString().trim(), time)
                ReminderScheduler.schedule(reminder)
                val date = Date(time)
                val label = android.text.format.DateFormat.getMediumDateFormat(context).format(date) + " " +
                    android.text.format.DateFormat.getTimeFormat(context).format(date)
                Toast.makeText(context, LocaleController.formatString(R.string.InuReminderSet, label), Toast.LENGTH_SHORT).show()
            },
            null, colors, provider,
        ) ?: return
        val sheet = builder.create()
        activity.showDialog(sheet)
        blurBehind(context, sheet)
    }

    private fun blurBehind(context: Context, sheet: android.app.Dialog) {
        if (Build.VERSION.SDK_INT < 31) return
        AndroidUtilities.runOnUIThread({
            val window = sheet.window ?: return@runOnUIThread
            val windows = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (!windows.isCrossWindowBlurEnabled) return@runOnUIThread
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            val params = window.attributes
            params.blurBehindRadius = AndroidUtilities.dp(24f)
            window.attributes = params
        }, 60)
    }
}
