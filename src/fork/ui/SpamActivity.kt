package desu.inugram.ui

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import desu.inugram.helpers.dialogs.SpamFolderHelper
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ActionBar.ThemeDescription
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.ItemOptions
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView

// entiny: chats of people who are not contacts and never got a message from you; swipe right takes a chat out, swipe left blocks and deletes it
class SpamActivity : BaseFragment(), NotificationCenter.NotificationCenterDelegate {

    private val items = ArrayList<TLRPC.Dialog>()
    private var listView: RecyclerListView? = null
    private var emptyView: TextView? = null

    override fun onFragmentCreate(): Boolean {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.dialogsNeedReload)
        items.addAll(SpamFolderHelper.dialogs(currentAccount))
        return super.onFragmentCreate()
    }

    override fun onFragmentDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.dialogsNeedReload)
        super.onFragmentDestroy()
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id == NotificationCenter.dialogsNeedReload && account == currentAccount) reload()
    }

    private fun reload() {
        val fresh = SpamFolderHelper.dialogs(currentAccount)
        val adapter = listView?.adapter
        val oldIds = items.map { it.id }
        val newIds = fresh.map { it.id }
        items.clear()
        items.addAll(fresh)
        if (adapter != null) {
            val removed = oldIds.indices.filter { oldIds[it] !in newIds }
            if (removed.size == 1 && oldIds.size - 1 == newIds.size && oldIds.filter { it in newIds } == newIds) {
                adapter.notifyItemRemoved(removed[0])
                adapter.notifyItemRangeChanged(0, items.size)
            } else {
                adapter.notifyDataSetChanged()
            }
        }
        updateState()
    }

    override fun createView(context: Context): View {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back)
        actionBar.setAllowOverlayTitle(true)
        actionBar.setTitle(LocaleController.getString(R.string.InuSpamTitle))
        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id == -1) finishFragment()
            }
        })

        val frame = FrameLayout(context)
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))

        val recycler = RecyclerListView(context)
        recycler.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        recycler.adapter = SpamAdapter(context)
        recycler.setOnItemClickListener { _, position -> open(items.getOrNull(position)) }
        recycler.setOnItemLongClickListener { view, position ->
            val dialog = items.getOrNull(position)
            if (dialog != null) showMenu(view, dialog)
            true
        }
        SpamFolderHelper.attachSwipe(recycler, currentAccount, null)
        listView = recycler
        frame.addView(recycler, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

        val empty = TextView(context)
        empty.text = LocaleController.getString(R.string.InuSpamEmpty)
        empty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
        empty.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
        empty.gravity = Gravity.CENTER
        empty.setPadding(AndroidUtilities.dp(32f), 0, AndroidUtilities.dp(32f), 0)
        emptyView = empty
        frame.addView(empty, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))

        fragmentView = frame
        updateState()
        return frame
    }

    private fun updateState() {
        emptyView?.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun open(dialog: TLRPC.Dialog?) {
        if (dialog == null) return
        val args = Bundle()
        args.putLong("user_id", dialog.id)
        presentFragment(ChatActivity(args))
    }

    private fun showMenu(anchor: View, dialog: TLRPC.Dialog) {
        ItemOptions.makeOptions(this, anchor)
            .add(R.drawable.msg_openin, LocaleController.getString(R.string.InuSpamRestore)) {
                SpamFolderHelper.release(currentAccount, dialog.id)
            }
            .add(R.drawable.msg_block, LocaleController.getString(R.string.InuSpamBlock), true) {
                SpamFolderHelper.blockAndDelete(currentAccount, dialog.id)
            }
            .show()
    }

    override fun getThemeDescriptions(): ArrayList<ThemeDescription> = ArrayList()

    private inner class SpamAdapter(private val context: Context) : RecyclerListView.SelectionAdapter() {
        override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean = true

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val cell = DialogCell(null, context, true, false, currentAccount, resourceProvider)
            cell.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
            return RecyclerListView.Holder(cell)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val dialog = items.getOrNull(position) ?: return
            (holder.itemView as DialogCell).setDialog(dialog, 0, 0)
        }
    }
}
