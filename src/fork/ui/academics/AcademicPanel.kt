package desu.inugram.ui.academics

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import desu.inugram.helpers.academics.AcademicContent
import desu.inugram.helpers.academics.AcademicStore
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.DialogsActivity

// Shown on top of an academic folder's tab on the Chats screen:
// Groups, Channels, Files (a tab per format), Voice & Video (collapsible sections) and Polls.
class AcademicPanel(context: Context, private val fragment: DialogsActivity) : FrameLayout(context) {
    private sealed class Row {
        class Chat(val dialogId: Long) : Row()
        class Entry(val kind: AcademicContent.Kind, val item: AcademicContent.Item) : Row()
        class Section(val kind: AcademicContent.Kind, val title: String, val count: Int, val open: Boolean) : Row()
        class Note(val text: String) : Row()
    }

    private val account get() = fragment.currentAccount
    private var folder: AcademicStore.Folder? = null
    private var signature = ""
    private var tab = 0
    private var format = 0
    private val collapsed = HashSet<AcademicContent.Kind>()
    private var rows: List<Row> = emptyList()

    private val formats = listOf("PDF", "Word", "Excel", "PowerPoint", "Other")
    private val tabsView = TabStrip(context)
    private val formatsView = TabStrip(context)
    private val content = LinearLayout(context)
    private val list = RecyclerView(context)

    init {
        isClickable = true
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        content.orientation = LinearLayout.VERTICAL
        addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
        content.addView(tabsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        content.addView(formatsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = Adapter()
        list.clipToPadding = false
        content.addView(list, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f))

        tabsView.onSelect = { index ->
            tab = index
            updateTabs()
            rebuild()
        }
        formatsView.onSelect = { index ->
            format = index
            updateTabs()
            rebuild()
        }
        updateTabs()
    }

    private fun tabNames() = listOf(
        LocaleController.getString(R.string.InuTabGroups),
        LocaleController.getString(R.string.InuTabChannels),
        LocaleController.getString(R.string.InuTabFiles),
        LocaleController.getString(R.string.InuTabVoiceVideo),
        LocaleController.getString(R.string.InuTabPolls),
    )

    private fun updateTabs() {
        tabsView.set(tabNames(), tab)
        formatsView.visibility = if (tab == 2) View.VISIBLE else View.GONE
        formatsView.set(formats.map { if (it == "Other") LocaleController.getString(R.string.InuFmtOther) else it }, format)
    }

    private fun bind(f: AcademicStore.Folder, topInset: Int) {
        if (content.paddingTop != topInset) content.setPadding(0, topInset, 0, 0)
        val sig = "${f.id}|${f.name}|${f.chats.sorted().joinToString(",")}"
        if (folder?.id != f.id) {
            tab = 0
            format = 0
            collapsed.clear()
            updateTabs()
        }
        folder = f
        if (sig == signature) return
        signature = sig
        rebuild()
    }

    private fun chatKind(did: Long): Int {
        val chat = fragment.messagesController.getChat(-did) ?: return 0
        return if (ChatObject.isChannelAndNotMegaGroup(chat)) 1 else 0
    }

    private fun chatName(did: Long): String = fragment.messagesController.getChat(-did)?.title ?: ""

    private fun need(kind: AcademicContent.Kind): AcademicContent.Result? {
        val f = folder ?: return null
        AcademicContent.load(fragment, f, kind) { rebuild() }
        return AcademicContent.cached(account, f, kind)
    }

    private fun sorted(items: List<AcademicContent.Item>): List<AcademicContent.Item> =
        items.sortedWith(
            compareByDescending<AcademicContent.Item> { AcademicStore.isPinned(account, it.key) }.thenByDescending { it.date }
        )

    private fun noteFor(kind: AcademicContent.Kind, result: AcademicContent.Result?): String =
        if (result == null) LocaleController.getString(R.string.InuAcademicLoading)
        else LocaleController.getString(R.string.InuAcademicEmpty)

    private fun rebuild() {
        val f = folder ?: return
        val out = ArrayList<Row>()
        when (tab) {
            0, 1 -> {
                val ids = f.chats.filter { chatKind(it) == tab }.sortedByDescending {
                    fragment.messagesController.dialogs_dict.get(it)?.last_message_date ?: 0
                }
                if (ids.isEmpty()) out.add(Row.Note(LocaleController.getString(R.string.InuAcademicEmpty)))
                for (id in ids) out.add(Row.Chat(id))
            }

            2 -> {
                val result = need(AcademicContent.Kind.FILES)
                val wanted = formats[format]
                val items = sorted(result?.items?.filter { AcademicContent.formatOf(it.ext) == wanted } ?: emptyList())
                if (items.isEmpty()) out.add(Row.Note(noteFor(AcademicContent.Kind.FILES, result)))
                for (item in items) out.add(Row.Entry(AcademicContent.Kind.FILES, item))
                addDuplicates(out, result)
            }

            3 -> {
                var duplicates = 0
                val sections = listOf(
                    AcademicContent.Kind.VOICE to R.string.InuSecVoice,
                    AcademicContent.Kind.VIDEO to R.string.InuSecVideo,
                    AcademicContent.Kind.VMSG to R.string.InuSecVideoMessage,
                )
                for ((kind, title) in sections) {
                    val result = need(kind)
                    val items = sorted(result?.items ?: emptyList())
                    val open = !collapsed.contains(kind)
                    out.add(Row.Section(kind, LocaleController.getString(title), items.size, open))
                    if (open) {
                        if (items.isEmpty()) out.add(Row.Note(noteFor(kind, result)))
                        for (item in items) out.add(Row.Entry(kind, item))
                    }
                    duplicates += result?.duplicates ?: 0
                }
                if (duplicates > 0) out.add(Row.Note(LocaleController.formatString(R.string.InuAcademicDuplicates, duplicates)))
            }

            else -> {
                val result = need(AcademicContent.Kind.POLLS)
                val items = result?.items?.sortedByDescending { it.date } ?: emptyList()
                if (items.isEmpty()) out.add(Row.Note(noteFor(AcademicContent.Kind.POLLS, result)))
                for (item in items) out.add(Row.Entry(AcademicContent.Kind.POLLS, item))
                addDuplicates(out, result)
            }
        }
        rows = out
        list.adapter?.notifyDataSetChanged()
    }

    private fun addDuplicates(out: MutableList<Row>, result: AcademicContent.Result?) {
        val n = result?.duplicates ?: 0
        if (n > 0) out.add(Row.Note(LocaleController.formatString(R.string.InuAcademicDuplicates, n)))
    }

    private fun openChat(dialogId: Long, messageId: Int) {
        val args = Bundle()
        args.putLong("chat_id", -dialogId)
        if (messageId != 0) args.putInt("message_id", messageId)
        fragment.presentFragment(ChatActivity(args))
    }

    private fun duration(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

    private fun bindEntry(cell: ItemCell, row: Row.Entry) {
        val item = row.item
        val also = if (item.chats > 1) " · +${item.chats - 1}" else ""
        val source = chatName(item.dialogId) + also
        val date = LocaleController.stringForMessageListDate(item.date)
        var pinnable = true
        when (row.kind) {
            AcademicContent.Kind.FILES -> {
                val label = item.ext.take(4).uppercase().ifEmpty { "FILE" }
                cell.plate.show(PLATE_COLORS[AcademicContent.formatOf(item.ext)] ?: PLATE_OTHER, label, 0)
                cell.title.text = item.title.ifEmpty { label }
                cell.subtitle.text = AndroidUtilities.formatFileSize(item.size) + " · " + source + " · " + date
            }

            AcademicContent.Kind.VOICE -> {
                cell.plate.show(0xFF2B7CD3.toInt(), null, R.drawable.softgram_academic_voice)
                cell.title.text = LocaleController.getString(R.string.InuAcademicVoiceTitle) + " · " + duration(item.duration)
                cell.subtitle.text = source + " · " + date
            }

            AcademicContent.Kind.VIDEO -> {
                cell.plate.show(0xFF7B61D9.toInt(), null, R.drawable.softgram_academic_video)
                cell.title.text = item.title.ifEmpty { LocaleController.getString(R.string.InuAcademicVideoTitle) } + " · " + duration(item.duration)
                cell.subtitle.text = AndroidUtilities.formatFileSize(item.size) + " · " + source + " · " + date
            }

            AcademicContent.Kind.VMSG -> {
                cell.plate.show(0xFF21A366.toInt(), null, R.drawable.softgram_academic_vmsg)
                cell.title.text = LocaleController.getString(R.string.InuAcademicVideoMessageTitle) + " · " + duration(item.duration)
                cell.subtitle.text = source + " · " + date
            }

            AcademicContent.Kind.POLLS -> {
                pinnable = false
                cell.plate.show(0xFFE8702A.toInt(), null, R.drawable.softgram_academic_poll)
                cell.title.text = item.title
                cell.subtitle.text = LocaleController.formatString(R.string.InuAcademicPollOptions, item.options, item.votes) + " · " + source
            }
        }
        cell.setPin(pinnable, pinnable && AcademicStore.isPinned(account, item.key)) {
            AcademicStore.togglePin(account, item.key)
            rebuild()
        }
        cell.setOnClickListener { openChat(item.dialogId, item.messageId) }
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = rows.size

        override fun getItemViewType(position: Int) = when (rows[position]) {
            is Row.Chat -> 0
            is Row.Entry -> 1
            is Row.Section -> 2
            is Row.Note -> 3
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view: View = when (viewType) {
                0 -> DialogCell(fragment, context, false, false, account, null)
                1 -> ItemCell(context)
                2 -> SectionCell(context)
                else -> NoteCell(context)
            }
            view.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val view = holder.itemView
            when (val row = rows[position]) {
                is Row.Chat -> {
                    val cell = view as DialogCell
                    val dialog = fragment.messagesController.dialogs_dict.get(row.dialogId)
                        ?: TLRPC.TL_dialog().also { it.id = row.dialogId }
                    cell.setDialog(dialog, 0, 0)
                    view.setOnClickListener { openChat(row.dialogId, 0) }
                }

                is Row.Entry -> bindEntry(view as ItemCell, row)

                is Row.Section -> {
                    val cell = view as SectionCell
                    cell.set(row.title + " · " + row.count, row.open)
                    cell.setOnClickListener {
                        if (!collapsed.remove(row.kind)) collapsed.add(row.kind)
                        rebuild()
                    }
                }

                is Row.Note -> (view as NoteCell).set(row.text)
            }
        }
    }

    // a single row of tabs with an underline under the selected one
    private class TabStrip(context: Context) : HorizontalScrollView(context) {
        private val row = LinearLayout(context)
        var onSelect: ((Int) -> Unit)? = null

        init {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            row.orientation = LinearLayout.HORIZONTAL
            addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        fun set(labels: List<String>, selected: Int) {
            row.removeAllViews()
            val active = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader)
            val idle = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2)
            for ((index, label) in labels.withIndex()) {
                val tab = LinearLayout(context)
                tab.orientation = LinearLayout.VERTICAL
                val text = TextView(context)
                text.text = label
                text.textSize = 14f
                text.typeface = AndroidUtilities.bold()
                text.maxLines = 1
                text.setTextColor(if (index == selected) active else idle)
                text.setPadding(AndroidUtilities.dp(14f), AndroidUtilities.dp(11f), AndroidUtilities.dp(14f), AndroidUtilities.dp(9f))
                tab.addView(text, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))
                val line = View(context)
                line.setBackgroundColor(if (index == selected) active else 0)
                tab.addView(line, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 2))
                tab.setOnClickListener { onSelect?.invoke(index) }
                row.addView(tab, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))
            }
        }
    }

    private class Plate(context: Context) : FrameLayout(context) {
        private val label = TextView(context)
        private val icon = ImageView(context)

        init {
            label.gravity = Gravity.CENTER
            label.textSize = 11f
            label.typeface = AndroidUtilities.bold()
            label.setTextColor(0xFFFFFFFF.toInt())
            icon.scaleType = ImageView.ScaleType.CENTER
            addView(label, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
            addView(icon, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
        }

        fun show(color: Int, text: String?, iconRes: Int) {
            val shape = GradientDrawable()
            shape.cornerRadius = AndroidUtilities.dp(10f).toFloat()
            shape.setColor(color)
            background = shape
            label.visibility = if (text != null) View.VISIBLE else View.GONE
            label.text = text ?: ""
            icon.visibility = if (text == null) View.VISIBLE else View.GONE
            if (iconRes != 0) icon.setImageResource(iconRes)
        }
    }

    private class ItemCell(context: Context) : LinearLayout(context) {
        val plate = Plate(context)
        val title = TextView(context)
        val subtitle = TextView(context)
        private val pin = ImageView(context)

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(AndroidUtilities.dp(16f), AndroidUtilities.dp(8f), AndroidUtilities.dp(6f), AndroidUtilities.dp(8f))
            background = Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2)
            addView(plate, LayoutHelper.createLinear(44, 44, Gravity.CENTER_VERTICAL))
            title.textSize = 16f
            title.maxLines = 1
            title.ellipsize = TextUtils.TruncateAt.END
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
            subtitle.textSize = 14f
            subtitle.maxLines = 1
            subtitle.ellipsize = TextUtils.TruncateAt.END
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2))
            val column = LinearLayout(context)
            column.orientation = VERTICAL
            column.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
            column.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 2f, 0f, 0f))
            addView(column, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 8, 0))
            pin.setImageResource(R.drawable.softgram_academic_pin)
            pin.scaleType = ImageView.ScaleType.CENTER
            addView(pin, LayoutHelper.createLinear(40, 40))
        }

        fun setPin(visible: Boolean, pinned: Boolean, onToggle: () -> Unit) {
            pin.visibility = if (visible) View.VISIBLE else View.GONE
            pin.setColorFilter(
                if (pinned) Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon) else Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon)
            )
            pin.alpha = if (pinned) 1f else 0.6f
            pin.setOnClickListener { onToggle() }
        }
    }

    private class SectionCell(context: Context) : LinearLayout(context) {
        private val text = TextView(context)
        private val arrow = ImageView(context)

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(AndroidUtilities.dp(16f), AndroidUtilities.dp(10f), AndroidUtilities.dp(12f), AndroidUtilities.dp(10f))
            setBackgroundColor(Theme.getColor(Theme.key_graySection))
            text.textSize = 14f
            text.typeface = AndroidUtilities.bold()
            text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader))
            addView(text, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f))
            arrow.setImageResource(R.drawable.softgram_academic_chevron)
            arrow.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon))
            addView(arrow, LayoutHelper.createLinear(24, 24))
        }

        fun set(title: String, open: Boolean) {
            text.text = title
            arrow.rotation = if (open) 180f else 0f
        }
    }

    private class NoteCell(context: Context) : TextView(context) {
        init {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2))
            setPadding(AndroidUtilities.dp(24f), AndroidUtilities.dp(20f), AndroidUtilities.dp(24f), AndroidUtilities.dp(20f))
        }

        fun set(value: String) {
            text = value
        }
    }

    companion object {
        private val PLATE_OTHER = 0xFF6C7A89.toInt()
        private val PLATE_COLORS = mapOf(
            "PDF" to 0xFFE64646.toInt(),
            "Word" to 0xFF2B7CD3.toInt(),
            "Excel" to 0xFF21A366.toInt(),
            "PowerPoint" to 0xFFE8702A.toInt(),
        )

        /** called from the Chats screen: shows the panel on academic folder tabs and hides it elsewhere */
        @JvmStatic
        fun sync(fragment: DialogsActivity, page: DialogsActivity.ViewPage, selectedType: Int) {
            val folder = academicFolderAt(fragment, selectedType)
            val existing = page.inu_panel as? AcademicPanel
            if (folder == null) {
                if (existing != null && existing.visibility != View.GONE) existing.visibility = View.GONE
                return
            }
            val panel = existing ?: AcademicPanel(page.context, fragment).also {
                page.inu_panel = it
                page.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            if (panel.visibility != View.VISIBLE) panel.visibility = View.VISIBLE
            panel.bind(folder, page.listView.paddingTop)
        }

        private fun academicFolderAt(fragment: DialogsActivity, selectedType: Int): AcademicStore.Folder? {
            if (!AcademicStore.enabled) return null
            val filters = fragment.messagesController.dialogFilters
            if (selectedType < 0 || selectedType >= filters.size) return null
            val filter = filters[selectedType]
            return AcademicStore.folders(fragment.currentAccount).firstOrNull { it.filterId != 0 && it.filterId == filter.id }
        }
    }
}
