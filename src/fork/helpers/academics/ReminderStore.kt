package desu.inugram.helpers.academics

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.ApplicationLoader

// Reminders made from messages ("Add to calendar"). They stay on this device.
object ReminderStore {
    const val ACTIVE = 0
    const val FIRED = 1
    const val ARCHIVED = 2

    class Reminder(
        val id: Long,
        val account: Int,
        val dialogId: Long,
        val messageId: Int,
        var text: String,
        var time: Long,
        var state: Int,
    )

    private const val PREFS = "softgram_reminders"
    private var cache: MutableList<Reminder>? = null

    private fun prefs() = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun all(): MutableList<Reminder> = cache ?: load().also { cache = it }

    @Synchronized
    fun find(id: Long): Reminder? = all().firstOrNull { it.id == id }

    @Synchronized
    fun add(account: Int, dialogId: Long, messageId: Int, text: String, time: Long): Reminder {
        var id = System.currentTimeMillis()
        while (all().any { it.id == id }) id++
        val reminder = Reminder(id, account, dialogId, messageId, text, time, ACTIVE)
        all().add(reminder)
        save()
        return reminder
    }

    @Synchronized
    fun save() {
        val array = JSONArray()
        for (r in all()) {
            array.put(
                JSONObject()
                    .put("id", r.id).put("account", r.account).put("dialog", r.dialogId).put("message", r.messageId)
                    .put("text", r.text).put("time", r.time).put("state", r.state)
            )
        }
        prefs().edit().putString("items", array.toString()).apply()
    }

    private fun load(): MutableList<Reminder> {
        val result = ArrayList<Reminder>()
        val raw = prefs().getString("items", null) ?: return result
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                result.add(
                    Reminder(
                        o.getLong("id"), o.getInt("account"), o.getLong("dialog"), o.getInt("message"),
                        o.optString("text", ""), o.getLong("time"), o.optInt("state", ACTIVE),
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("ReminderStore", "bad data", e)
        }
        return result
    }
}
