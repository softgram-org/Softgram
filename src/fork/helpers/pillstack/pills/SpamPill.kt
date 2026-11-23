package desu.inugram.helpers.pillstack.pills

import android.annotation.SuppressLint
import android.content.Context
import desu.inugram.helpers.dialogs.SpamFolderHelper
import desu.inugram.helpers.pillstack.PillType
import desu.inugram.ui.SpamActivity
import org.telegram.messenger.LocaleController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.LaunchActivity

// entiny: stranger chats live behind this pill; the number is how many of them are unread
@SuppressLint("ViewConstructor")
class SpamPill(context: Context, resourcesProvider: Theme.ResourcesProvider?) :
    BasePill(context, resourcesProvider), NotificationCenter.NotificationCenterDelegate {

    private var lastAccount = 0
    private var unread = 0

    init {
        setupPill(R.drawable.msg_report, startPaddingDp = 8f, endPaddingDp = 10f, iconGapDp = 4f)
        onUpdateData(false)
    }

    override fun getPillId(): Int = PillType.SPAM.id

    override fun getRefreshInterval(): Long = 0

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastAccount = UserConfig.selectedAccount
        val center = NotificationCenter.getInstance(lastAccount)
        center.addObserver(this, NotificationCenter.dialogsNeedReload)
        center.addObserver(this, NotificationCenter.updateInterfaces)
        center.addObserver(this, NotificationCenter.dialogsUnreadCounterChanged)
        onUpdateData(true)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        val center = NotificationCenter.getInstance(lastAccount)
        center.removeObserver(this, NotificationCenter.dialogsNeedReload)
        center.removeObserver(this, NotificationCenter.updateInterfaces)
        center.removeObserver(this, NotificationCenter.dialogsUnreadCounterChanged)
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (account != lastAccount) return
        if (id == NotificationCenter.dialogsNeedReload || id == NotificationCenter.updateInterfaces || id == NotificationCenter.dialogsUnreadCounterChanged) {
            onUpdateData(false)
        }
    }

    override fun onUpdateData(force: Boolean) {
        val count = SpamFolderHelper.unreadCount(UserConfig.selectedAccount)
        val title = LocaleController.getString(R.string.InuPillStackSpam)
        val text = if (count > 0) "$title $count" else title
        if (force || count != unread || textView.text?.toString() != text) {
            if (count != unread) animateSizeChange()
            unread = count
            textView.setText(text, force)
        }
        updateColors()
    }

    override fun onPillClicked() {
        LaunchActivity.getSafeLastFragment()?.presentFragment(SpamActivity())
    }

    override fun onPillLongClicked(): Boolean = showPillMenu {
        add(R.drawable.msg_report, LocaleController.getString(R.string.InuSpamTitle)) { onPillClicked() }
        addSettingsEntry()
    }

    override fun updateColors() = applyPillColors(if (unread > 0) getThemedColor(Theme.key_text_RedRegular) else defaultAccent())
}
