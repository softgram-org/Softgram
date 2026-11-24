package desu.inugram.helpers.dialogs

import android.content.SharedPreferences
import androidx.recyclerview.widget.RecyclerView
import desu.inugram.InuConfig
import desu.inugram.helpers.InuPrefs
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.MessageObject
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import java.util.concurrent.ConcurrentHashMap

// entiny: strangers (not a contact, never written to) go to a virtual Spam folder; membership is local only, nothing is sent to the server
object SpamFolderHelper {

    fun interface SelectionProbe {
        fun active(): Boolean
    }

    private const val PREFS = "inu_spam_folder"
    private const val SEPARATOR = ","
    private const val SERVICE_NOTIFICATIONS_ID = 777000L
    private const val TALKED_LIMIT = 20000

    private class State(
        val spam: MutableSet<Long>,
        val released: MutableSet<Long>,
        val talked: MutableSet<Long>,
    ) {
        val checking: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    }

    private val states = HashMap<Int, State>()

    private fun state(account: Int): State = synchronized(states) {
        states.getOrPut(account) {
            val prefs = InuPrefs.of(PREFS)
            State(read(prefs, "spam_$account"), read(prefs, "released_$account"), read(prefs, "talked_$account"))
        }
    }

    private fun read(prefs: SharedPreferences, key: String): MutableSet<Long> {
        val set: MutableSet<Long> = ConcurrentHashMap.newKeySet()
        val raw = prefs.getString(key, "") ?: ""
        if (raw.isNotEmpty()) {
            for (part in raw.split(SEPARATOR)) part.toLongOrNull()?.let { set.add(it) }
        }
        return set
    }

    private fun persist(account: Int, name: String, set: Set<Long>) {
        InuPrefs.of(PREFS).edit().putString("${name}_$account", set.joinToString(SEPARATOR)).apply()
    }

    @JvmStatic
    fun isEnabled(): Boolean = InuConfig.SPAM_FOLDER.value

    @JvmStatic
    fun contains(account: Int, dialogId: Long): Boolean {
        if (dialogId <= 0 || !isEnabled()) return false
        if (!state(account).spam.contains(dialogId)) return false
        val user = MessagesController.getInstance(account).getUser(dialogId)
        return user == null || !user.contact
    }

    private fun isStranger(account: Int, userId: Long): Boolean {
        if (userId <= 0 || userId == SERVICE_NOTIFICATIONS_ID) return false
        if (userId == UserConfig.getInstance(account).clientUserId) return false
        val user = MessagesController.getInstance(account).getUser(userId) ?: return false
        return !(user.contact || user.bot || user.self || user.support || user.verified)
    }

    private fun noteTalked(account: Int, dialogId: Long) {
        val st = state(account)
        if (st.talked.size >= TALKED_LIMIT) st.talked.clear()
        if (st.talked.add(dialogId)) persist(account, "talked", st.talked)
    }

    @JvmStatic
    fun onNewMessage(account: Int, message: MessageObject) {
        if (!isEnabled()) return
        val dialogId = message.dialogId
        if (dialogId <= 0) return
        val st = state(account)
        if (message.isOut) {
            noteTalked(account, dialogId)
            if (st.spam.contains(dialogId)) release(account, dialogId)
            return
        }
        if (st.spam.contains(dialogId) || st.released.contains(dialogId) || st.talked.contains(dialogId)) return
        if (!isStranger(account, dialogId)) return
        verifyNeverSpoken(account, dialogId)
    }

    private fun verifyNeverSpoken(account: Int, dialogId: Long) {
        val st = state(account)
        if (!st.checking.add(dialogId)) return
        val storage = MessagesStorage.getInstance(account)
        storage.storageQueue.postRunnable {
            var spoken = true
            try {
                val cursor = storage.database.queryFinalized("SELECT 1 FROM messages_v2 WHERE uid = $dialogId AND out = 1 LIMIT 1")
                spoken = cursor.next()
                cursor.dispose()
            } catch (_: Throwable) {
            }
            AndroidUtilities.runOnUIThread {
                st.checking.remove(dialogId)
                if (spoken) noteTalked(account, dialogId) else addSpam(account, dialogId)
            }
        }
    }

    private fun addSpam(account: Int, dialogId: Long) {
        if (!isEnabled()) return
        val st = state(account)
        if (st.released.contains(dialogId) || !isStranger(account, dialogId)) return
        if (!st.spam.add(dialogId)) return
        persist(account, "spam", st.spam)
        refresh(account)
    }

    @JvmStatic
    fun release(account: Int, dialogId: Long) {
        val st = state(account)
        if (st.released.add(dialogId)) persist(account, "released", st.released)
        if (st.spam.remove(dialogId)) {
            persist(account, "spam", st.spam)
            refresh(account)
        }
    }

    @JvmStatic
    fun blockAndDelete(account: Int, dialogId: Long) {
        val controller = MessagesController.getInstance(account)
        controller.blockPeer(dialogId)
        controller.deleteDialog(dialogId, 0, true)
        val st = state(account)
        if (st.spam.remove(dialogId)) {
            persist(account, "spam", st.spam)
            refresh(account)
        }
    }

    private fun refresh(account: Int) {
        val controller = MessagesController.getInstance(account) ?: return
        controller.sortDialogs(null)
        val center = NotificationCenter.getInstance(account)
        center.postNotificationName(NotificationCenter.dialogsNeedReload)
        center.postNotificationName(NotificationCenter.dialogFiltersUpdated)
        MessagesStorage.getInstance(account).inu_recalcUnread()
    }

    @JvmStatic
    fun onFeatureChanged() {
        for (i in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
            if (UserConfig.getInstance(i).isClientActivated) refresh(i)
        }
    }

    @JvmStatic
    fun filter(controller: MessagesController, account: Int) {
        if (!isEnabled() || state(account).spam.isEmpty()) return
        val pred = { d: TLRPC.Dialog -> contains(account, d.id) }
        // entiny: allDialogs is the source every sort rebuilds from, removing from it made released chats vanish until restart
        for (i in 0 until controller.dialogsByFolder.size()) {
            controller.dialogsByFolder.valueAt(i)?.removeAll(pred)
        }
        for (filter in controller.selectedDialogFilter) {
            if (filter == null) continue
            filter.dialogs.removeAll(pred)
        }
    }

    private fun shouldSilence(account: Int, dialogId: Long): Boolean {
        if (dialogId <= 0 || !isEnabled()) return false
        val st = state(account)
        if (st.spam.contains(dialogId)) return contains(account, dialogId)
        if (st.released.contains(dialogId) || st.talked.contains(dialogId) || !isStranger(account, dialogId)) return false
        return !spokeBefore(account, dialogId)
    }

    private fun spokeBefore(account: Int, dialogId: Long): Boolean {
        val last = MessagesController.getInstance(account).dialogMessage.get(dialogId) ?: return false
        for (message in last) {
            if (message != null && message.isOut) return true
        }
        return false
    }

    @JvmStatic
    fun filterNotifications(account: Int, messages: ArrayList<MessageObject>?): ArrayList<MessageObject>? {
        if (messages == null || messages.isEmpty() || !isEnabled()) return messages
        val kept = ArrayList<MessageObject>(messages.size)
        var dropped = false
        for (message in messages) {
            if (!message.isOut && shouldSilence(account, message.dialogId)) dropped = true else kept.add(message)
        }
        return if (dropped) kept else messages
    }

    @JvmStatic
    fun dialogs(account: Int): ArrayList<TLRPC.Dialog> {
        val result = ArrayList<TLRPC.Dialog>()
        if (!isEnabled()) return result
        val controller = MessagesController.getInstance(account)
        for (id in state(account).spam) {
            if (!contains(account, id)) continue
            controller.dialogs_dict.get(id)?.let { result.add(it) }
        }
        result.sortByDescending { it.last_message_date }
        return result
    }

    @JvmStatic
    fun unreadCount(account: Int): Int {
        var count = 0
        for (dialog in dialogs(account)) {
            if (dialog.unread_count > 0 || dialog.unread_mark) count++
        }
        return count
    }

    @JvmStatic
    fun attachSwipe(list: RecyclerView?, account: Int, probe: SelectionProbe?) {
        if (list == null) return
        for (i in 0 until list.itemDecorationCount) {
            if (list.getItemDecorationAt(i) is SpamSwipeController) return
        }
        SpamSwipeController.attach(list, account, probe)
    }
}
