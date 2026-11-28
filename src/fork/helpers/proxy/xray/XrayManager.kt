package desu.inugram.helpers.proxy.xray

import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.Utilities
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.security.SecureRandom

// entiny: every saved xray link is a normal SOCKS5 entry on 127.0.0.1 in the stock proxy list; one core process serves all of them,
// so the stock check shows a real ping for each and switching entries never restarts anything
object XrayManager : NotificationCenter.NotificationCenterDelegate {
    private const val PREFS = "softgram_xray"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_ENV = "env_key"
    private const val LOCAL_HOST = "127.0.0.1"
    private const val GRACE_MS = 60_000L

    private class Entry(val port: Int, val user: String, val pass: String, val link: String, val name: String, val created: Long)

    private val lock = Any()
    private var cache: MutableList<Entry>? = null
    private var controller: CoreController? = null
    private var runningKey = ""
    private var envReady = false
    private var observing = false
    @Volatile private var announce = false

    private val handler = object : CoreCallbackHandler {
        override fun startup(): Long = 0L
        override fun shutdown(): Long = 0L
        override fun onEmitStatus(code: Long, message: String?): Long = 0L
    }

    @JvmStatic
    fun isXrayLink(line: String): Boolean = XrayLink.isXrayLink(line)

    @JvmStatic
    fun init() {
        ensureObserver()
        reconcile()
    }

    // returns a tg://socks link to the local core, null when the link is not a usable xray config
    @JvmStatic
    fun importLink(link: String): String? {
        if (XrayLink.toOutbound(link) == null) return null
        ensureObserver()
        announce = true
        val entry = synchronized(lock) {
            val list = entries()
            list.firstOrNull { it.link == link } ?: run {
                val name = XrayLink.nameOf(link).ifEmpty { "Xray " + (list.size + 1) }
                Entry(freePort(list), randomHex(8), randomHex(16), link, name, System.currentTimeMillis()).also {
                    list.add(it)
                    save(list)
                }
            }
        }
        return "tg://socks?server=$LOCAL_HOST&port=${entry.port}&user=${entry.user}&pass=${entry.pass}"
    }

    @JvmStatic
    fun displayName(address: String?, port: Int): String? {
        if (address != LOCAL_HOST) return null
        val e = synchronized(lock) { entries().firstOrNull { it.port == port } } ?: return null
        return e.name + " (" + XrayLink.protocolOf(e.link) + ")"
    }

    @JvmStatic
    fun shareLink(info: SharedConfig.ProxyInfo): String {
        val s = info.settings
        if (s.address == LOCAL_HOST) {
            val e = synchronized(lock) { entries().firstOrNull { it.port == s.port } }
            if (e != null) return e.link
        }
        return s.link
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id == NotificationCenter.proxySettingsChanged) reconcile()
    }

    private fun ensureObserver() {
        val first = synchronized(lock) { !observing.also { observing = true } }
        if (first) AndroidUtilities.runOnUIThread { NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged) }
    }

    private fun reconcile() {
        AndroidUtilities.runOnUIThread {
            val snapshot = synchronized(lock) {
                val all = entries()
                if (all.isNotEmpty()) {
                    SharedConfig.loadProxyList()
                    val alive = SharedConfig.proxyList.filter { it.settings.address == LOCAL_HOST }.map { it.settings.port }.toSet()
                    val now = System.currentTimeMillis()
                    val kept = all.filter { it.port in alive || now - it.created < GRACE_MS }
                    if (kept.size != all.size) {
                        all.clear()
                        all.addAll(kept)
                        save(all)
                    }
                }
                ArrayList(all)
            }
            Utilities.globalQueue.postRunnable { apply(snapshot) }
        }
    }

    private fun apply(list: List<Entry>) {
        synchronized(lock) {
            if (list.isEmpty()) {
                stopLocked()
                return
            }
            val key = list.joinToString("|") { it.port.toString() + ":" + it.user + ":" + it.pass + ":" + it.link }
            if (key == runningKey && controller?.isRunning == true) return
            stopLocked()
            try {
                ensureEnv()
                val config = buildConfig(list)
                if (config == null) {
                    toast("Xray: no usable config")
                    return
                }
                val c = Libv2ray.newCoreController(handler)
                c.startLoop(config, 0)
                controller = c
                runningKey = key
                if (announce) {
                    announce = false
                    toast("Xray core is running (" + list.size + " configs). " + Libv2ray.checkVersionX())
                }
            } catch (t: Throwable) {
                FileLog.e(t)
                controller = null
                runningKey = ""
                announce = false
                toast("Xray core failed: " + t.javaClass.simpleName + ": " + t.message)
            }
        }
    }

    private fun buildConfig(list: List<Entry>): String? {
        val inbounds = JSONArray()
        val outbounds = JSONArray()
        val rules = JSONArray()
        for (e in list) {
            val outbound = XrayLink.toOutbound(e.link) ?: continue
            outbound.put("tag", "out-" + e.port)
            outbounds.put(outbound)
            inbounds.put(JSONObject()
                .put("tag", "in-" + e.port)
                .put("listen", LOCAL_HOST)
                .put("port", e.port)
                .put("protocol", "socks")
                .put("settings", JSONObject()
                    .put("auth", "password")
                    .put("udp", false)
                    .put("accounts", JSONArray().put(JSONObject().put("user", e.user).put("pass", e.pass)))))
            rules.put(JSONObject()
                .put("type", "field")
                .put("inboundTag", JSONArray().put("in-" + e.port))
                .put("outboundTag", "out-" + e.port))
        }
        if (outbounds.length() == 0) return null
        return JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("inbounds", inbounds)
            .put("outbounds", outbounds)
            .put("routing", JSONObject().put("domainStrategy", "AsIs").put("rules", rules))
            .toString()
    }

    // entiny: short status messages while the xray integration is being tested
    private fun toast(msg: String) {
        AndroidUtilities.runOnUIThread {
            android.widget.Toast.makeText(ApplicationLoader.applicationContext, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun stopLocked() {
        val c = controller ?: return
        controller = null
        runningKey = ""
        try {
            c.stopLoop()
        } catch (t: Throwable) {
            FileLog.e(t)
        }
    }

    private fun ensureEnv() {
        if (envReady) return
        val prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0)
        var key = prefs.getString(KEY_ENV, null)
        if (key.isNullOrEmpty()) {
            key = randomHex(16)
            prefs.edit().putString(KEY_ENV, key).apply()
        }
        val dir = File(ApplicationLoader.applicationContext.filesDir, "xray")
        dir.mkdirs()
        Libv2ray.initCoreEnv(dir.absolutePath, key)
        envReady = true
    }

    private fun entries(): MutableList<Entry> {
        cache?.let { return it }
        val prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0)
        val out = ArrayList<Entry>()
        try {
            val arr = JSONArray(prefs.getString(KEY_ENTRIES, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Entry(o.getInt("port"), o.getString("user"), o.getString("pass"), o.getString("link"), o.optString("name"), o.optLong("created", 0L)))
            }
        } catch (t: Throwable) {
            FileLog.e(t)
        }
        cache = out
        return out
    }

    private fun save(list: List<Entry>) {
        val arr = JSONArray()
        for (e in list) {
            arr.put(JSONObject().put("port", e.port).put("user", e.user).put("pass", e.pass).put("link", e.link).put("name", e.name).put("created", e.created))
        }
        ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0).edit().putString(KEY_ENTRIES, arr.toString()).apply()
    }

    private fun freePort(list: List<Entry>): Int {
        val rnd = SecureRandom()
        val used = list.map { it.port }.toSet()
        repeat(200) {
            val port = 20000 + rnd.nextInt(40000)
            if (port in used) return@repeat
            try {
                ServerSocket(port, 0, InetAddress.getByName(LOCAL_HOST)).use { return port }
            } catch (_: Throwable) {
            }
        }
        throw IllegalStateException("no free local port")
    }

    private fun randomHex(bytes: Int): String {
        val b = ByteArray(bytes)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }
}
