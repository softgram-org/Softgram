package desu.inugram.helpers.academics

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.ApplicationLoader

// Academic Workspace data. Everything stays on this device; Telegram never sees it.
object AcademicStore {
    class Folder(val id: Int, var name: String, val chats: LinkedHashSet<Long> = LinkedHashSet(), var filterId: Int = 0)

    private const val PREFS = "softgram_academics"
    private val cache = HashMap<Int, MutableList<Folder>>()

    private fun prefs() = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs().getBoolean("enabled", false)
        set(value) {
            prefs().edit().putBoolean("enabled", value).apply()
        }

    fun folders(account: Int): MutableList<Folder> = cache.getOrPut(account) { load(account) }

    fun find(account: Int, id: Int): Folder? = folders(account).firstOrNull { it.id == id }

    fun create(account: Int, name: String): Folder {
        val list = folders(account)
        val folder = Folder((list.maxOfOrNull { it.id } ?: 0) + 1, name)
        list.add(folder)
        save(account)
        return folder
    }

    fun rename(account: Int, id: Int, name: String) {
        find(account, id)?.name = name
        save(account)
    }

    fun setFilterId(account: Int, id: Int, filterId: Int) {
        find(account, id)?.filterId = filterId
        save(account)
    }

    fun delete(account: Int, id: Int) {
        folders(account).removeAll { it.id == id }
        save(account)
    }

    fun setChat(account: Int, id: Int, dialogId: Long, selected: Boolean) {
        val folder = find(account, id) ?: return
        if (selected) folder.chats.add(dialogId) else folder.chats.remove(dialogId)
        save(account)
    }

    fun isPinned(account: Int, key: String): Boolean =
        prefs().getStringSet("pins_$account", emptySet())?.contains(key) == true

    /** returns true when the item is pinned after the toggle */
    fun togglePin(account: Int, key: String): Boolean {
        val set = HashSet(prefs().getStringSet("pins_$account", emptySet()) ?: emptySet())
        val pinned = if (set.contains(key)) {
            set.remove(key)
            false
        } else {
            set.add(key)
            true
        }
        prefs().edit().putStringSet("pins_$account", set).apply()
        return pinned
    }

    private fun key(account: Int) = "folders_$account"

    private fun load(account: Int): MutableList<Folder> {
        val result = ArrayList<Folder>()
        val raw = prefs().getString(key(account), null) ?: return result
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val folder = Folder(obj.getInt("id"), obj.getString("name"), LinkedHashSet(), obj.optInt("filter", 0))
                val chats = obj.optJSONArray("chats")
                if (chats != null) {
                    for (j in 0 until chats.length()) folder.chats.add(chats.getLong(j))
                }
                result.add(folder)
            }
        } catch (e: Exception) {
            android.util.Log.e("AcademicStore", "bad data", e)
        }
        return result
    }

    private fun save(account: Int) {
        val array = JSONArray()
        for (folder in folders(account)) {
            val chats = JSONArray()
            for (id in folder.chats) chats.put(id)
            array.put(JSONObject().put("id", folder.id).put("name", folder.name).put("chats", chats).put("filter", folder.filterId))
        }
        prefs().edit().putString(key(account), array.toString()).apply()
    }
}
