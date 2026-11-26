package desu.inugram.ui.settings

import android.content.Context
import android.view.View
import desu.inugram.InuConfig
import desu.inugram.helpers.InuUtils
import desu.inugram.helpers.academics.AcademicFilters
import desu.inugram.helpers.academics.AcademicStore
import desu.inugram.helpers.profile.LocalNameHelper
import desu.inugram.ui.showInputDialog
import org.telegram.messenger.ChatObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import java.util.Locale

// One academic folder: its groups and channels. Names changed here are local to this device.
class AcademicFolderActivity(private val folderId: Int) : SettingsPageActivity() {
    private var query: String? = null
    private var searching = false

    private fun folder(): AcademicStore.Folder? = AcademicStore.find(currentAccount, folderId)

    override fun getTitle(): CharSequence = folder()?.name ?: ""

    override fun createView(context: Context): View {
        val view = super.createView(context)
        simpleActionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id == -1) finishFragment()
            }
        })
        addSearch(MENU_SEARCH, Locale.ROOT, trim = true, onExpand = { expanded ->
            searching = expanded
            if (!expanded) {
                query = null
                listView?.adapter?.update(true)
            }
        }) {
            query = it
            listView?.adapter?.update(true)
        }
        return view
    }

    override fun onBackPressed(invoked: Boolean): Boolean {
        if (!searching) return super.onBackPressed(invoked)
        if (invoked) simpleActionBar.closeSearchField()
        return false
    }

    override fun onResume() {
        super.onResume()
        listView?.adapter?.update(true)
    }

    private fun candidates(): List<TLRPC.Chat> {
        val seen = HashSet<Long>()
        val result = ArrayList<TLRPC.Chat>()
        for (dialog in messagesController.allDialogs) {
            if (dialog.id >= 0) continue
            val chat = messagesController.getChat(-dialog.id) ?: continue
            if (ChatObject.isNotInChat(chat) || !seen.add(chat.id)) continue
            result.add(chat)
        }
        return result
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        val f = folder() ?: return
        val q = query
        val noQuery = q.isNullOrEmpty()
        if (noQuery) {
            items.add(SoftIcons.button(BUTTON_RENAME_FOLDER, R.drawable.msg_edit, LocaleController.getString(R.string.InuAcademicRenameFolder)))
            items.add(UItem.asShadow(null))
        }

        val all = ArrayList(candidates())
        if (noQuery) {
            // chats already in the folder stay listed even when Telegram has not loaded them in the chat list
            val known = all.map { it.id }.toHashSet()
            for (did in f.chats) {
                val chat = messagesController.getChat(-did) ?: continue
                if (known.add(chat.id)) all.add(chat)
            }
        }
        all.sortBy { it.title?.lowercase(Locale.ROOT) ?: "" }

        val selected = ArrayList<TLRPC.Chat>()
        val others = ArrayList<TLRPC.Chat>()
        for (chat in all) {
            if (!noQuery && chat.title?.lowercase(Locale.ROOT)?.contains(q!!) != true) continue
            if (f.chats.contains(-chat.id)) selected.add(chat) else others.add(chat)
        }

        if (selected.isNotEmpty()) {
            items.add(UItem.asHeader(LocaleController.getString(R.string.InuAcademicSelected)))
            for ((index, chat) in selected.withIndex()) {
                items.add(UItem.asUserCheckbox(CHAT_BASE + index, chat).setChecked(true))
            }
        }
        if (others.isNotEmpty()) {
            if (selected.isNotEmpty()) items.add(UItem.asShadow(null))
            items.add(UItem.asHeader(LocaleController.getString(R.string.InuAcademicAddChats)))
            for ((index, chat) in others.withIndex()) {
                items.add(UItem.asUserCheckbox(CHAT_BASE + OTHERS_OFFSET + index, chat).setChecked(false))
            }
        }
        if (selected.isEmpty() && others.isEmpty()) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.InuAcademicNoChats)))
        }
        if (noQuery) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.InuAcademicFolderInfo)))
            items.add(SoftIcons.button(BUTTON_DELETE_FOLDER, R.drawable.entiny_trash, LocaleController.getString(R.string.InuAcademicDeleteFolder)))
            items.add(UItem.asShadow(null))
        }
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        val f = folder() ?: return
        val chat = item.`object` as? TLRPC.Chat
        when {
            item.id == BUTTON_RENAME_FOLDER -> renameFolder(f)
            item.id == BUTTON_DELETE_FOLDER -> confirmDelete(f)
            chat != null -> {
                val dialogId = -chat.id
                if (f.chats.contains(dialogId)) {
                    showChatOptions(f, chat)
                } else {
                    AcademicStore.setChat(currentAccount, f.id, dialogId, true)
                    AcademicFilters.sync(this, currentAccount, f)
                    listView?.adapter?.update(true)
                }
            }
        }
    }

    private fun showChatOptions(f: AcademicStore.Folder, chat: TLRPC.Chat) {
        val ctx = parentActivity ?: return
        val dialogId = -chat.id
        val labels = arrayOf<CharSequence>(
            LocaleController.getString(R.string.InuAcademicRenameLocally),
            LocaleController.getString(R.string.InuAcademicResetName),
            LocaleController.getString(R.string.InuAcademicRemoveFromFolder),
        )
        val dialog = AlertDialog.Builder(ctx, resourceProvider)
            .setTitle(chat.title ?: "")
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> {
                        // local names are what keeps the Telegram name untouched, so it has to be on
                        if (!InuConfig.LOCAL_NAMES.value) {
                            InuConfig.LOCAL_NAMES.value = true
                            LocalNameHelper.onToggle()
                        }
                        LocalNameHelper.showEditor(this, currentAccount, dialogId, Runnable { listView?.adapter?.update(true) })
                    }

                    1 -> {
                        LocalNameHelper.remove(currentAccount, dialogId)
                        listView?.adapter?.update(true)
                    }

                    2 -> {
                        AcademicStore.setChat(currentAccount, f.id, dialogId, false)
                        AcademicFilters.sync(this, currentAccount, f)
                        listView?.adapter?.update(true)
                    }
                }
            }
            .create()
        showDialog(dialog)
    }

    private fun renameFolder(f: AcademicStore.Folder) {
        showInputDialog(
            this,
            title = LocaleController.getString(R.string.InuAcademicRenameFolder),
            hint = LocaleController.getString(R.string.InuAcademicFolderName),
            initialText = f.name,
            selectAll = true,
        ) { raw ->
            val name = raw.trim()
            if (name.isEmpty()) {
                false
            } else {
                AcademicStore.rename(currentAccount, f.id, name)
                AcademicFilters.sync(this, currentAccount, f)
                simpleActionBar?.setTitle(name)
                listView?.adapter?.update(true)
                true
            }
        }
    }

    private fun confirmDelete(f: AcademicStore.Folder) {
        val ctx = parentActivity ?: return
        val dialog = AlertDialog.Builder(ctx, resourceProvider)
            .setTitle(LocaleController.getString(R.string.InuAcademicDeleteFolder))
            .setMessage(LocaleController.getString(R.string.InuAcademicDeleteFolderConfirm))
            .setPositiveButton(LocaleController.getString(R.string.Delete)) { _, _ ->
                AcademicFilters.remove(this, currentAccount, f)
                AcademicStore.delete(currentAccount, f.id)
                finishFragment()
            }
            .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
            .create()
        showDialog(dialog)
    }

    companion object {
        private const val CHAT_BASE = 100_000
        private const val OTHERS_OFFSET = 50_000
        private const val MENU_SEARCH = 0
        private val BUTTON_RENAME_FOLDER = InuUtils.generateId()
        private val BUTTON_DELETE_FOLDER = InuUtils.generateId()
    }
}
