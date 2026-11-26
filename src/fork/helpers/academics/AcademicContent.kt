package desu.inugram.helpers.academics

import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLoader
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.BaseFragment
import java.util.Locale

// Files, voice, video and polls of the chats in an academic folder.
// An item that was forwarded to several chats is listed once (documents and polls are told apart by their Telegram id).
object AcademicContent {
    enum class Kind { FILES, VOICE, VIDEO, VMSG, POLLS }

    class Item(
        val key: String,
        val dialogId: Long,
        val messageId: Int,
        val date: Int,
        val title: String,
        val ext: String,
        val size: Long,
        val duration: Int,
        val options: Int,
        val votes: Int,
        var chats: Int = 1,
    )

    class Result(val items: List<Item>, val duplicates: Int, val time: Long)

    private const val FRESH_MS = 120_000L
    private val cache = HashMap<String, Result>()
    private val loading = HashSet<String>()

    private fun cacheKey(account: Int, folder: AcademicStore.Folder, kind: Kind) =
        "$account|${kind.name}|${folder.chats.sorted().joinToString(",")}"

    fun cached(account: Int, folder: AcademicStore.Folder, kind: Kind): Result? = cache[cacheKey(account, folder, kind)]

    fun isLoading(account: Int, folder: AcademicStore.Folder, kind: Kind): Boolean = loading.contains(cacheKey(account, folder, kind))

    /** loads in the background; a result younger than two minutes is reused. onDone runs on the UI thread. */
    fun load(fragment: BaseFragment, folder: AcademicStore.Folder, kind: Kind, onDone: () -> Unit) {
        val account = fragment.currentAccount
        val key = cacheKey(account, folder, kind)
        val old = cache[key]
        if (loading.contains(key)) return
        if (old != null && System.currentTimeMillis() - old.time < FRESH_MS) return
        val chats = folder.chats.toList()
        if (chats.isEmpty()) {
            cache[key] = Result(emptyList(), 0, System.currentTimeMillis())
            return
        }
        loading.add(key)
        val raw = ArrayList<Item>()
        var pending = chats.size
        val finishOne = {
            pending--
            if (pending == 0) {
                cache[key] = merge(raw)
                loading.remove(key)
                onDone()
            }
        }
        for (dialogId in chats) {
            val peer = fragment.messagesController.getInputPeer(dialogId)
            if (peer == null) {
                finishOne()
                continue
            }
            val req = TLRPC.TL_messages_search()
            req.peer = peer
            req.q = ""
            req.filter = when (kind) {
                Kind.FILES -> TLRPC.TL_inputMessagesFilterDocument()
                Kind.VOICE -> TLRPC.TL_inputMessagesFilterVoice()
                Kind.VIDEO -> TLRPC.TL_inputMessagesFilterVideo()
                Kind.VMSG -> TLRPC.TL_inputMessagesFilterRoundVideo()
                Kind.POLLS -> TLRPC.TL_inputMessagesFilterPoll()
            }
            req.limit = 100
            fragment.connectionsManager.sendRequest(req) { response, _ ->
                val res = response as? TLRPC.messages_Messages
                AndroidUtilities.runOnUIThread {
                    if (res != null) {
                        for (msg in res.messages) parse(kind, dialogId, msg)?.let { raw.add(it) }
                    }
                    finishOne()
                }
            }
        }
    }

    private fun parse(kind: Kind, dialogId: Long, msg: TLRPC.Message): Item? {
        val media = msg.media ?: return null
        if (kind == Kind.POLLS) {
            val pm = media as? TLRPC.TL_messageMediaPoll ?: return null
            val poll = pm.poll ?: return null
            return Item("p${poll.id}", dialogId, msg.id, msg.date, poll.question.text ?: "", "", 0L, 0, poll.answers.size, pm.results?.total_voters ?: 0)
        }
        val doc = media.document ?: return null
        val name = FileLoader.getDocumentFileName(doc) ?: ""
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        var duration = 0
        for (attr in doc.attributes) {
            if (attr.duration > 0) {
                duration = attr.duration.toInt()
                break
            }
        }
        return Item("d${doc.id}", dialogId, msg.id, msg.date, name, ext, doc.size, duration, 0, 0)
    }

    private fun merge(raw: List<Item>): Result {
        val map = LinkedHashMap<String, Item>()
        var duplicates = 0
        for (item in raw.sortedByDescending { it.date }) {
            val first = map[item.key]
            if (first == null) {
                map[item.key] = item
            } else {
                first.chats++
                duplicates++
            }
        }
        return Result(map.values.toList(), duplicates, System.currentTimeMillis())
    }

    fun formatOf(ext: String): String = when (ext) {
        "pdf" -> "PDF"
        "doc", "docx", "rtf", "odt" -> "Word"
        "xls", "xlsx", "csv", "ods" -> "Excel"
        "ppt", "pptx", "odp" -> "PowerPoint"
        else -> "Other"
    }
}
