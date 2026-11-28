package desu.inugram.ui.feed

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import desu.inugram.InuConfig
import desu.inugram.helpers.chat.ChatHelper
import desu.inugram.helpers.dialogs.FolderHelper
import desu.inugram.helpers.dialogs.MainTabsHelper
import desu.inugram.helpers.feed.FeedConfig
import desu.inugram.helpers.feed.FeedController
import desu.inugram.helpers.feed.FeedMessageUtils
import desu.inugram.helpers.feed.FeedScope
import desu.inugram.ui.settings.FeedExcludedChannelsSettingsActivity
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.FileLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaController
import org.telegram.messenger.MessageObject
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.SendMessagesHelper
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.ItemOptions
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.DialogsActivity
import org.telegram.ui.MainTabsActivity
import org.telegram.ui.PhotoViewer
import org.telegram.utils.glass.source.GlassSourceRenderNode

class FeedCardsActivity @JvmOverloads constructor(
    @Suppress("UNUSED_PARAMETER") scope: FeedScope = FeedScope.Global,
    private val hasMainTabs: Boolean = false,
) : BaseFragment(), NotificationCenter.NotificationCenterDelegate, MainTabsActivity.TabFragmentDelegate, FeedCardHost {

    private val classGuid = ConnectionsManager.generateClassGuid()
    private var list: RecyclerView? = null
    private var pull: PullRefreshLayout? = null
    private var chipsRow: LinearLayout? = null
    private var emptyView: TextView? = null
    private var header: FrameLayout? = null
    private var titleView: TextView? = null
    private var lastInsets: WindowInsetsCompat? = null
    private var tabsGlassCallback: Runnable? = null
    private var uiActiveHeld = false
    private var lastGeneration = 0
    private var filterDialogId = 0L
    private var animateIn = true
    private var all = ArrayList<FeedItem>()
    private var items = ArrayList<FeedItem>()
    private val adapter = CardAdapter()
    private val pendingDownloads = HashMap<String, MessageObject>()
    private val extraCache = HashMap<String, ArrayList<MessageObject>>()
    private val extraLoading = HashSet<String>()
    private val extraPeers = HashMap<String, Long>()
    private val extraOldest = HashMap<String, Int>()
    private val extraExhausted = HashSet<String>()
    private val extraPaging = HashSet<String>()
    private val reloadNewer = Runnable { FeedController.getInstance(currentAccount).loadNewer(classGuid, 0) }

    override fun onFragmentCreate(): Boolean {
        val nc = NotificationCenter.getInstance(currentAccount)
        nc.addObserver(this, NotificationCenter.messagesDidLoad)
        nc.addObserver(this, NotificationCenter.feedNeedReload)
        nc.addObserver(this, NotificationCenter.didReceiveNewMessages)
        nc.addObserver(this, NotificationCenter.fileLoaded)
        nc.addObserver(this, NotificationCenter.messagePlayingDidStart)
        nc.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged)
        nc.addObserver(this, NotificationCenter.messagePlayingDidReset)
        lastGeneration = FeedConfig.getInstance(currentAccount).generation
        return super.onFragmentCreate()
    }

    override fun onFragmentDestroy() {
        AndroidUtilities.cancelRunOnUIThread(reloadNewer)
        if (uiActiveHeld) {
            uiActiveHeld = false
            FeedController.getInstance(currentAccount).setUiActive(false)
        }
        val nc = NotificationCenter.getInstance(currentAccount)
        nc.removeObserver(this, NotificationCenter.messagesDidLoad)
        nc.removeObserver(this, NotificationCenter.feedNeedReload)
        nc.removeObserver(this, NotificationCenter.didReceiveNewMessages)
        nc.removeObserver(this, NotificationCenter.fileLoaded)
        nc.removeObserver(this, NotificationCenter.messagePlayingDidStart)
        nc.removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged)
        nc.removeObserver(this, NotificationCenter.messagePlayingDidReset)
        super.onFragmentDestroy()
    }

    override fun createView(context: Context): View {
        actionBar.setAddToContainer(false)
        actionBar.visibility = View.GONE

        val root = FrameLayout(context)
        fragmentView = root
        root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray))

        val recycler = RecyclerView(context)
        list = recycler
        recycler.overScrollMode = View.OVER_SCROLL_NEVER
        recycler.itemAnimator = null
        recycler.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        recycler.adapter = adapter
        recycler.clipToPadding = false
        recycler.setPadding(AndroidUtilities.dp(8f), 0, AndroidUtilities.dp(8f), 0)
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                updateParallax()
                val lm = rv.layoutManager as? StaggeredGridLayoutManager ?: return
                val last = IntArray(2)
                lm.findLastVisibleItemPositions(last)
                if (dy > 0 && maxOf(last[0], last[1]) >= items.size - 6) {
                    FeedController.getInstance(currentAccount).loadMore(classGuid, 0)
                    loadMoreExtras()
                }
            }
        })
        recycler.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateParallax() }
        val puller = PullRefreshLayout(context, recycler)
        pull = puller
        puller.setSpinnerColor(getThemedColor(Theme.key_featuredStickers_addButton))
        puller.onRefresh = { refresh() }
        root.addView(puller, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL))

        val empty = TextView(context)
        emptyView = empty
        empty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
        empty.gravity = Gravity.CENTER
        empty.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText))
        empty.visibility = View.GONE
        root.addView(empty, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))

        val head = FrameLayout(context)
        header = head
        head.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray))
        val title = TextView(context)
        titleView = title
        title.text = LocaleController.getString(R.string.InuFeed)
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26f)
        title.setTypeface(Typeface.DEFAULT_BOLD)
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText))
        val titleStart = if (hasMainTabs) 18f else 56f
        head.addView(title, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.CENTER_VERTICAL, titleStart, 0f, 0f, 0f))
        if (!hasMainTabs) {
            head.addView(iconButton(context, R.drawable.ic_ab_back, LocaleController.getString(R.string.AccDescrGoBack)) { finishFragment() }, LayoutHelper.createFrame(44, 44f, Gravity.LEFT or Gravity.CENTER_VERTICAL, 6f, 0f, 0f, 0f))
        }
        val actions = LinearLayout(context)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.addView(iconButton(context, R.drawable.msg_add, LocaleController.getString(R.string.InuFeedAddChannel)) { showAddDialog() }, LinearLayout.LayoutParams(AndroidUtilities.dp(42f), AndroidUtilities.dp(42f)))
        var folderButton: View? = null
        folderButton = iconButton(context, R.drawable.msg_folders, LocaleController.getString(R.string.InuFeedFolder)) { showFolderPicker(folderButton ?: return@iconButton) }
        actions.addView(folderButton, LinearLayout.LayoutParams(AndroidUtilities.dp(42f), AndroidUtilities.dp(42f)))
        head.addView(actions, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.RIGHT or Gravity.CENTER_VERTICAL, 0f, 0f, 6f, 0f))

        val scroller = HorizontalScrollView(context)
        scroller.isHorizontalScrollBarEnabled = false
        val chips = LinearLayout(context)
        chipsRow = chips
        chips.orientation = LinearLayout.HORIZONTAL
        chips.setPadding(AndroidUtilities.dp(14f), 0, AndroidUtilities.dp(14f), 0)
        scroller.addView(chips, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val top = LinearLayout(context)
        top.orientation = LinearLayout.VERTICAL
        top.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray))
        top.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(56f)))
        top.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(44f)))
        root.addView(top, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP))
        top.tag = "top"

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            lastInsets = insets
            val effective = if (hasMainTabs) extendInsetsByTabsHeight(insets) else insets
            val bars = effective.getInsets(WindowInsetsCompat.Type.systemBars())
            top.setPadding(0, bars.top, 0, 0)
            pull?.topOffset = bars.top + AndroidUtilities.dp(100f)
            recycler.setPadding(AndroidUtilities.dp(8f), bars.top + AndroidUtilities.dp(100f), AndroidUtilities.dp(8f), bars.bottom + AndroidUtilities.dp(12f))
            effective
        }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                val insets = lastInsets
                if (insets != null) ViewCompat.dispatchApplyWindowInsets(view, insets) else view.requestApplyInsets()
            }

            override fun onViewDetachedFromWindow(view: View) {}
        })

        if (!uiActiveHeld) {
            uiActiveHeld = true
            FeedController.getInstance(currentAccount).setUiActive(true)
        }
        FeedController.getInstance(currentAccount).loadInitial(classGuid, 0)
        rebuild()
        AndroidUtilities.runOnUIThread({ animateIn = false }, 900L)
        return root
    }

    private fun iconButton(context: Context, res: Int, description: String, onClick: () -> Unit): ImageView {
        val button = ImageView(context)
        button.scaleType = ImageView.ScaleType.CENTER
        button.setImageResource(res)
        button.colorFilter = PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY)
        button.background = Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 1)
        button.contentDescription = description
        button.setOnClickListener { onClick() }
        return button
    }

    private fun extendInsetsByTabsHeight(windowInsets: WindowInsetsCompat): WindowInsetsCompat {
        val tabsHeight = if (MainTabsHelper.isHidden) 0 else AndroidUtilities.dp(MainTabsHelper.mainTabsHeightWithMargins.toFloat())
        if (tabsHeight == 0) return windowInsets
        val types = WindowInsetsCompat.Type.systemBars()
        val nav = WindowInsetsCompat.Type.navigationBars()
        return WindowInsetsCompat.Builder(windowInsets)
            .setInsets(types, extendBottom(windowInsets.getInsets(types), tabsHeight))
            .setInsets(nav, extendBottom(windowInsets.getInsets(nav), tabsHeight))
            .setInsetsIgnoringVisibility(types, extendBottom(windowInsets.getInsetsIgnoringVisibility(types), tabsHeight))
            .setInsetsIgnoringVisibility(nav, extendBottom(windowInsets.getInsetsIgnoringVisibility(nav), tabsHeight))
            .build()
    }

    private fun extendBottom(insets: Insets, extra: Int): Insets =
        Insets.of(insets.left, insets.top, insets.right, insets.bottom + extra)

    private fun palette() = FeedPalette(
        card = getThemedColor(Theme.key_windowBackgroundWhite),
        text = getThemedColor(Theme.key_windowBackgroundWhiteBlackText),
        sub = getThemedColor(Theme.key_windowBackgroundWhiteGrayText),
        accent = getThemedColor(Theme.key_featuredStickers_addButton),
        soft = getThemedColor(Theme.key_windowBackgroundGray),
    )

    private fun rebuild() {
        val controller = FeedController.getInstance(currentAccount)
        val feedMessages = ArrayList<MessageObject>(controller.store.getVisibleMessages())
        val feedDialogs = HashSet<Long>()
        for (m in feedMessages) feedDialogs.add(m.dialogId)
        val extras = InuConfig.FEED_EXTRA_CHANNELS.value
        val merged = ArrayList<Pair<MessageObject, Boolean>>()
        for (m in feedMessages) merged.add(Pair(m, false))
        for (username in extras) {
            val cached = extraCache[username]
            if (cached == null) {
                loadExtra(username)
                continue
            }
            for (m in cached) {
                if (!feedDialogs.contains(m.dialogId)) merged.add(Pair(m, true))
            }
        }
        merged.sortByDescending { it.first.messageOwner.date }
        val result = ArrayList<FeedItem>()
        val groups = HashMap<String, FeedItem>()
        val seen = HashSet<String>()
        for ((m, isExtra) in merged) {
            val key = m.dialogId.toString() + ":" + (if (m.realId > 0) m.realId else m.id)
            if (!seen.add(key)) continue
            if (m.hasValidGroupId()) {
                val groupKey = m.dialogId.toString() + "_" + m.messageOwner.grouped_id
                val existing = groups[groupKey]
                if (existing != null) {
                    existing.addToGroup(m)
                    continue
                }
                val created = FeedItem(m, isExtra)
                groups[groupKey] = created
                result.add(created)
            } else {
                result.add(FeedItem(m, isExtra))
            }
        }
        all = result
        applyFilter()
    }

    private fun applyFilter() {
        items = if (filterDialogId == 0L) all else ArrayList(all.filter { it.message.dialogId == filterDialogId })
        if (filterDialogId != 0L && items.isEmpty()) {
            filterDialogId = 0L
            items = all
        }
        adapter.notifyDataSetChanged()
        rebuildChips()
        val empty = emptyView
        if (empty != null) {
            empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            empty.text = LocaleController.getString(if (FeedController.getInstance(currentAccount).hasChannels()) R.string.InuFeedEmpty else R.string.InuFeedNoChannels)
        }
        list?.post { updateParallax() }
    }

    private fun rebuildChips() {
        val row = chipsRow ?: return
        row.removeAllViews()
        val context = row.context
        addChip(context, row, LocaleController.getString(R.string.InuFeedAll), 0L, null)
        val seen = LinkedHashMap<Long, FeedItem>()
        for (item in all) if (!seen.containsKey(item.message.dialogId)) seen[item.message.dialogId] = item
        for ((dialogId, item) in seen) {
            val chat = MessagesController.getInstance(currentAccount).getChat(-dialogId) ?: continue
            addChip(context, row, chat.title, dialogId, if (item.extra) ChatObject.getPublicUsername(chat) else null)
        }
    }

    private fun addChip(context: Context, row: LinearLayout, text: String, dialogId: Long, extraUsername: String?) {
        val selected = filterDialogId == dialogId
        val chip = TextView(context)
        chip.text = text
        chip.maxLines = 1
        chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
        chip.setTypeface(Typeface.DEFAULT_BOLD)
        chip.setTextColor(if (selected) getThemedColor(Theme.key_windowBackgroundGray) else getThemedColor(Theme.key_windowBackgroundWhiteGrayText))
        chip.background = GradientDrawable().apply {
            setColor(if (selected) getThemedColor(Theme.key_windowBackgroundWhiteBlackText) else getThemedColor(Theme.key_windowBackgroundWhite))
            cornerRadius = AndroidUtilities.dpf2(99f)
        }
        chip.setPadding(AndroidUtilities.dp(13f), 0, AndroidUtilities.dp(13f), 0)
        chip.gravity = Gravity.CENTER_VERTICAL
        chip.setOnClickListener {
            filterDialogId = dialogId
            applyFilter()
            list?.scrollToPosition(0)
        }
        if (extraUsername != null) {
            chip.setOnLongClickListener {
                ItemOptions.makeOptions(this, chip)
                    .add(R.drawable.msg_delete, LocaleController.getString(R.string.InuFeedRemoveExtra)) { removeExtra(extraUsername) }
                    .show()
                true
            }
        }
        row.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(34f)).also {
            it.setMargins(AndroidUtilities.dp(3f), AndroidUtilities.dp(5f), AndroidUtilities.dp(3f), AndroidUtilities.dp(5f))
        })
    }

    private fun refresh() {
        FeedController.getInstance(currentAccount).loadNewer(classGuid, 0)
        for (username in InuConfig.FEED_EXTRA_CHANNELS.value) loadExtra(username)
        AndroidUtilities.runOnUIThread({ pull?.finishRefresh() }, 1300L)
    }

    private fun updateParallax() {
        val recycler = list ?: return
        val h = recycler.height.toFloat().coerceAtLeast(1f)
        for (i in 0 until recycler.childCount) {
            val child = recycler.getChildAt(i) as? FeedCardView ?: continue
            child.applyParallax(((child.top + child.bottom) / 2f - h / 2f) / h)
        }
    }

    override fun channelTitle(item: FeedItem): String =
        MessagesController.getInstance(currentAccount).getChat(-item.message.dialogId)?.title ?: ""

    override fun isPlaying(item: FeedItem): Boolean {
        val controller = MediaController.getInstance()
        return controller.isPlayingMessage(item.message) && !controller.isMessagePaused
    }

    private fun messageIdOf(m: MessageObject): Int = if (m.realId > 0) m.realId else m.id

    override fun onOpenChannel(item: FeedItem) {
        val args = Bundle()
        args.putLong("chat_id", -item.message.dialogId)
        args.putInt("message_id", messageIdOf(item.message))
        presentFragment(ChatActivity(args))
    }

    override fun onOpenMedia(item: FeedItem) {
        if (item.kind != FeedKind.PHOTO && item.kind != FeedKind.VIDEO && item.kind != FeedKind.GIF) {
            onOpenChannel(item)
            return
        }
        val album = ArrayList<MessageObject>()
        for (m in item.group) {
            if (m.isVideo || m.isGif || m.isPhoto || FeedItem.thumbsOf(m) != null) album.add(m)
        }
        if (album.isEmpty()) album.add(item.message)
        val viewer = PhotoViewer.getInstance()
        viewer.setParentActivity(this)
        viewer.openPhoto(album, 0, 0L, 0L, 0L, object : PhotoViewer.EmptyPhotoViewerProvider() {})
    }

    override fun onTogglePlay(item: FeedItem) {
        val m = item.message
        val controller = MediaController.getInstance()
        if (controller.isPlayingMessage(m)) {
            if (controller.isMessagePaused) controller.playMessage(m) else controller.pauseMessage(m)
            return
        }
        val doc = m.document
        val file = FileLoader.getInstance(currentAccount).getPathToMessage(m.messageOwner)
        if (file != null && file.exists()) {
            controller.playMessage(m)
        } else if (doc != null) {
            pendingDownloads[FileLoader.getAttachFileName(doc)] = m
            FileLoader.getInstance(currentAccount).loadFile(doc, m, FileLoader.PRIORITY_NORMAL, 0)
        }
    }

    override fun onMenu(item: FeedItem, anchor: View) {
        val m = item.message
        val options = ItemOptions.makeOptions(this, anchor)
        options.setGravity(Gravity.LEFT)
        options.add(R.drawable.msg_channel, LocaleController.getString(R.string.InuFeedOpenPost)) { onOpenChannel(item) }
        options.add(R.drawable.msg_forward, LocaleController.getString(R.string.Forward)) { forward(item) }
        options.add(R.drawable.msg_saved, LocaleController.getString(R.string.InuFeedSaveMessage)) { saveMessage(item) }
        val chat = MessagesController.getInstance(currentAccount).getChat(-m.dialogId)
        val username = if (chat != null) ChatObject.getPublicUsername(chat) else null
        if (!username.isNullOrEmpty()) {
            options.add(R.drawable.msg_link, LocaleController.getString(R.string.InuFeedCopyLink)) {
                AndroidUtilities.addToClipboard("https://t.me/" + username + "/" + messageIdOf(m))
                BulletinFactory.of(this).createCopyLinkBulletin().show()
            }
        }
        options.show()
    }

    private fun forwardable(item: FeedItem): ArrayList<MessageObject> {
        val result = ArrayList<MessageObject>()
        for (m in item.group.sortedBy { messageIdOf(it) }) {
            val forwarding = FeedMessageUtils.getForwardingMessageObject(currentAccount, !item.extra, m) ?: m
            result.add(forwarding)
        }
        return result
    }

    private fun saveMessage(item: FeedItem) {
        ChatHelper.forwardToSavedMessages(this, forwardable(item))
    }

    private fun forward(item: FeedItem) {
        val args = Bundle().apply {
            putBoolean("onlySelect", true)
            putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD)
            putBoolean("canSelectTopics", true)
        }
        val picker = DialogsActivity(args)
        picker.setDelegate { _, dids, _, _, _, _, _, _ ->
            val messages = forwardable(item)
            for (key in dids) {
                SendMessagesHelper.getInstance(currentAccount).sendMessage(messages, key.dialogId, false, false, true, 0, 0L)
            }
            picker.finishFragment()
            true
        }
        presentFragment(picker)
    }

    private fun showAddDialog() {
        val activity = parentActivity ?: return
        val edit = EditText(activity)
        edit.hint = "@username"
        edit.setSingleLine(true)
        edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_URI
        edit.setTextColor(getThemedColor(Theme.key_dialogTextBlack))
        edit.setHintTextColor(getThemedColor(Theme.key_dialogTextHint))
        val holder = FrameLayout(activity)
        holder.setPadding(AndroidUtilities.dp(22f), AndroidUtilities.dp(4f), AndroidUtilities.dp(22f), 0)
        holder.addView(edit, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val builder = AlertDialog.Builder(activity, resourceProvider)
        builder.setTitle(LocaleController.getString(R.string.InuFeedAddChannel))
        builder.setMessage(LocaleController.getString(R.string.InuFeedAddChannelHint))
        builder.setView(holder)
        builder.setPositiveButton(LocaleController.getString(R.string.Add)) { _, _ -> addChannel(edit.text.toString()) }
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null)
        showDialog(builder.create())
    }

    private fun addChannel(raw: String) {
        val username = raw.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("t.me/").removePrefix("@").substringBefore('/').substringBefore('?')
        if (username.length < 4) {
            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.InuFeedChannelNotFound)).show()
            return
        }
        if (InuConfig.FEED_EXTRA_CHANNELS.value.contains(username)) {
            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.InuFeedAlreadyAdded)).show()
            return
        }
        val request = TLRPC.TL_contacts_resolveUsername()
        request.username = username
        ConnectionsManager.getInstance(currentAccount).sendRequest(request) { response, _ ->
            AndroidUtilities.runOnUIThread {
                val resolved = response as? TLRPC.TL_contacts_resolvedPeer
                val chat = resolved?.chats?.firstOrNull { it.broadcast }
                if (resolved == null || chat == null) {
                    BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.InuFeedChannelNotFound)).show()
                    return@runOnUIThread
                }
                val controller = MessagesController.getInstance(currentAccount)
                controller.putUsers(resolved.users, false)
                controller.putChats(resolved.chats, false)
                if (!ChatObject.isNotInChat(chat)) {
                    BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.InuFeedAlreadyMember)).show()
                    return@runOnUIThread
                }
                val updated = HashSet(InuConfig.FEED_EXTRA_CHANNELS.value)
                updated.add(username)
                InuConfig.FEED_EXTRA_CHANNELS.value = updated
                loadExtra(username)
            }
        }
    }

    private fun removeExtra(username: String) {
        val updated = HashSet(InuConfig.FEED_EXTRA_CHANNELS.value)
        updated.remove(username)
        InuConfig.FEED_EXTRA_CHANNELS.value = updated
        extraCache.remove(username)
        extraPeers.remove(username)
        extraOldest.remove(username)
        extraExhausted.remove(username)
        if (filterDialogId != 0L) filterDialogId = 0L
        rebuild()
    }

    private fun buildExtra(data: TLRPC.messages_Messages): ArrayList<MessageObject> {
        val usersMap = HashMap<Long, TLRPC.User>()
        for (user in data.users) usersMap[user.id] = user
        val chatsMap = HashMap<Long, TLRPC.Chat>()
        for (c in data.chats) chatsMap[c.id] = c
        val built = ArrayList<MessageObject>()
        for (message in data.messages) {
            if (message is TLRPC.TL_messageEmpty || message is TLRPC.TL_messageService) continue
            built.add(MessageObject(currentAccount, message, null, usersMap, chatsMap, null, null, true, true, 0L, false, false, false, 0))
        }
        return built
    }

    private fun oldestId(data: TLRPC.messages_Messages): Int {
        var oldest = Int.MAX_VALUE
        for (message in data.messages) if (message.id in 1 until oldest) oldest = message.id
        return oldest
    }

    private fun loadExtra(username: String) {
        if (!extraLoading.add(username)) return
        val resolve = TLRPC.TL_contacts_resolveUsername()
        resolve.username = username
        val connections = ConnectionsManager.getInstance(currentAccount)
        connections.sendRequest(resolve) { response, _ ->
            AndroidUtilities.runOnUIThread {
                val resolved = response as? TLRPC.TL_contacts_resolvedPeer
                val chat = resolved?.chats?.firstOrNull { it.broadcast }
                if (resolved == null || chat == null) {
                    extraLoading.remove(username)
                    if (extraCache[username] == null) extraCache[username] = ArrayList()
                    return@runOnUIThread
                }
                val controller = MessagesController.getInstance(currentAccount)
                controller.putUsers(resolved.users, false)
                controller.putChats(resolved.chats, false)
                extraPeers[username] = -chat.id
                val history = TLRPC.TL_messages_getHistory()
                history.peer = controller.getInputPeer(-chat.id)
                history.limit = EXTRA_LIMIT
                connections.sendRequest(history) { res, _ ->
                    AndroidUtilities.runOnUIThread {
                        extraLoading.remove(username)
                        val data = res as? TLRPC.messages_Messages
                        if (data == null) {
                            if (extraCache[username] == null) extraCache[username] = ArrayList()
                            return@runOnUIThread
                        }
                        controller.putUsers(data.users, false)
                        controller.putChats(data.chats, false)
                        val firstLoad = extraCache[username] == null
                        extraCache[username] = buildExtra(data)
                        extraOldest[username] = oldestId(data)
                        if (data.messages.size < EXTRA_LIMIT) extraExhausted.add(username) else extraExhausted.remove(username)
                        if (InuConfig.FEED_EXTRA_CHANNELS.value.contains(username)) {
                            rebuild()
                            if (firstLoad) BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, LocaleController.formatString(R.string.InuFeedChannelAdded, chat.title)).show()
                        }
                    }
                }
            }
        }
    }

    private fun loadMoreExtras() {
        val controller = MessagesController.getInstance(currentAccount)
        val connections = ConnectionsManager.getInstance(currentAccount)
        for (username in InuConfig.FEED_EXTRA_CHANNELS.value) {
            val peer = extraPeers[username] ?: continue
            val oldest = extraOldest[username] ?: continue
            if (extraExhausted.contains(username) || extraCache[username] == null || !extraPaging.add(username)) continue
            val history = TLRPC.TL_messages_getHistory()
            history.peer = controller.getInputPeer(peer)
            history.offset_id = oldest
            history.limit = EXTRA_LIMIT
            connections.sendRequest(history) { res, _ ->
                AndroidUtilities.runOnUIThread {
                    extraPaging.remove(username)
                    val data = res as? TLRPC.messages_Messages ?: return@runOnUIThread
                    controller.putUsers(data.users, false)
                    controller.putChats(data.chats, false)
                    if (data.messages.isEmpty()) {
                        extraExhausted.add(username)
                        return@runOnUIThread
                    }
                    extraCache[username]?.addAll(buildExtra(data))
                    extraOldest[username] = oldestId(data)
                    if (data.messages.size < EXTRA_LIMIT) extraExhausted.add(username)
                    rebuild()
                }
            }
        }
    }

    private fun pickableFolders(): List<MessagesController.DialogFilter> =
        MessagesController.getInstance(currentAccount).dialogFilters?.filter { !it.isDefault }.orEmpty()

    private fun showFolderPicker(anchor: View) {
        val folders = pickableFolders()
        if (folders.isEmpty()) {
            presentFragment(FeedExcludedChannelsSettingsActivity())
            return
        }
        val current = FeedConfig.getInstance(currentAccount).folderFilterId
        val options = ItemOptions.makeOptions(this, anchor)
        options.setGravity(Gravity.RIGHT)
        if (current != 0) {
            options.add(R.drawable.msg_channel, LocaleController.getString(R.string.InuFeedAllChannels)) { selectFolder(0) }
        }
        for (filter in folders) {
            if (filter.id == current) continue
            val info = FolderHelper.getTabInfo(filter)
            val name = info.first.takeIf { it.isNotEmpty() } ?: continue
            options.add(FolderHelper.getTabIcon(info.second), name) { selectFolder(filter.id) }
        }
        options.add(R.drawable.msg_settings, LocaleController.getString(R.string.InuFeedManageChannels)) { presentFragment(FeedExcludedChannelsSettingsActivity()) }
        options.show()
    }

    private fun selectFolder(filterId: Int) {
        val config = FeedConfig.getInstance(currentAccount)
        config.setFolder(filterId)
        lastGeneration = config.generation
        filterDialogId = 0L
        FeedController.getInstance(currentAccount).applyConfigChange { _ -> rebuild() }
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        when (id) {
            NotificationCenter.messagesDidLoad -> {
                if (args.size > 10 && args[10] == classGuid) rebuild()
            }
            NotificationCenter.feedNeedReload -> {
                FeedController.getInstance(currentAccount).loadInitial(classGuid, 0)
                rebuild()
            }
            NotificationCenter.didReceiveNewMessages -> {
                val scheduled = args[2] as Boolean
                if (scheduled || !FeedController.getInstance(currentAccount).isIncludedChannelPost(args[0] as Long)) return
                AndroidUtilities.cancelRunOnUIThread(reloadNewer)
                AndroidUtilities.runOnUIThread(reloadNewer, 1000L)
            }
            NotificationCenter.fileLoaded -> {
                val name = args[0] as? String ?: return
                val pending = pendingDownloads.remove(name) ?: return
                MediaController.getInstance().playMessage(pending)
            }
            NotificationCenter.messagePlayingDidStart,
            NotificationCenter.messagePlayingPlayStateChanged,
            NotificationCenter.messagePlayingDidReset -> refreshPlayStates()
        }
    }

    private fun refreshPlayStates() {
        val recycler = list ?: return
        for (i in 0 until recycler.childCount) (recycler.getChildAt(i) as? FeedCardView)?.refreshPlayState()
    }

    override fun onResume() {
        super.onResume()
        fragmentView?.let {
            val insets = lastInsets
            if (insets != null) ViewCompat.dispatchApplyWindowInsets(it, insets) else it.requestApplyInsets()
        }
        val generation = FeedConfig.getInstance(currentAccount).generation
        if (generation != lastGeneration) {
            lastGeneration = generation
            FeedController.getInstance(currentAccount).applyConfigChange { _ -> rebuild() }
        } else {
            rebuild()
        }
    }

    override fun isLightStatusBar(): Boolean = !Theme.isCurrentThemeDark()

    override fun isSupportEdgeToEdge(): Boolean = true

    override fun drawEdgeNavigationBar(): Boolean = false

    override fun canParentTabsSlide(ev: MotionEvent, forward: Boolean): Boolean = true

    override fun onParentScrollToTop() {
        list?.smoothScrollToPosition(0)
    }

    override fun getGlassSource(): GlassSourceRenderNode? = null

    override fun getFrostedGlassSource(): GlassSourceRenderNode? = null

    override fun onParentBecomeFullyVisible() {
        updateParallax()
    }

    override fun setParentTabsGlassInvalidationCallback(callback: Runnable?) {
        tabsGlassCallback = callback
    }

    private inner class CardAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val card = FeedCardView(parent.context, this@FeedCardsActivity)
            card.layoutParams = StaggeredGridLayoutManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.setMargins(AndroidUtilities.dp(4f), AndroidUtilities.dp(4f), AndroidUtilities.dp(4f), AndroidUtilities.dp(4f))
            }
            return object : RecyclerView.ViewHolder(card) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val card = holder.itemView as FeedCardView
            card.bind(items[position], palette())
            if (animateIn && position < 10) {
                card.alpha = 0f
                card.translationY = AndroidUtilities.dp(16f).toFloat()
                card.animate().alpha(1f).translationY(0f).setStartDelay(position * 45L).setDuration(420L).start()
            } else {
                card.alpha = 1f
                card.translationY = 0f
            }
            card.post { updateParallax() }
        }

        override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
            val position = holder.adapterPosition
            if (position < 0 || position >= items.size) return
            val item = items[position]
            if (!item.extra && InuConfig.FEED_MARK_READ_ON_SCROLL.value) {
                FeedController.getInstance(currentAccount).onPostSeen(item.message.dialogId, item.message.realId)
            }
        }
    }

    private companion object {
        const val EXTRA_LIMIT = 30
    }
}
