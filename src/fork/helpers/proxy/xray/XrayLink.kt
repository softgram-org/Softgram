package desu.inugram.helpers.proxy.xray

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

object XrayLink {
    private val schemes = listOf("vless://", "vmess://", "trojan://", "ss://")

    fun isXrayLink(line: String): Boolean = schemes.any { line.startsWith(it, ignoreCase = true) }

    fun toOutbound(link: String): JSONObject? = try {
        when {
            link.startsWith("vless://", true) -> vless(link)
            link.startsWith("vmess://", true) -> vmess(link)
            link.startsWith("trojan://", true) -> trojan(link)
            link.startsWith("ss://", true) -> shadowsocks(link)
            else -> null
        }
    } catch (_: Throwable) {
        null
    }

    fun protocolOf(link: String): String = when {
        link.startsWith("vless://", true) -> "VLESS"
        link.startsWith("vmess://", true) -> "VMess"
        link.startsWith("trojan://", true) -> "Trojan"
        link.startsWith("ss://", true) -> "Shadowsocks"
        else -> "Xray"
    }

    fun nameOf(link: String): String = try {
        val raw = if (link.startsWith("vmess://", true)) JSONObject(b64(link.substring(8))).optString("ps")
        else Uri.decode(link.substringAfter('#', ""))
        raw.trim().take(40)
    } catch (_: Throwable) {
        ""
    }

    private fun b64(input: String): String {
        var s = input.trim().replace('-', '+').replace('_', '/')
        while (s.length % 4 != 0) s += "="
        return String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
    }

    private fun query(uri: Uri): Map<String, String> {
        val m = HashMap<String, String>()
        for (k in uri.queryParameterNames) uri.getQueryParameter(k)?.let { m[k] = it }
        return m
    }

    private fun hostOf(uri: Uri): String = uri.host.orEmpty().removePrefix("[").removeSuffix("]")

    private fun vless(link: String): JSONObject {
        val uri = Uri.parse(link)
        val host = hostOf(uri)
        val port = uri.port
        val id = uri.userInfo.orEmpty()
        require(host.isNotEmpty() && port in 1..65535 && id.isNotEmpty())
        val p = query(uri)
        val user = JSONObject().put("id", id).put("encryption", p["encryption"] ?: "none").put("level", 0)
        p["flow"]?.takeIf { it.isNotEmpty() }?.let { user.put("flow", it) }
        return JSONObject().put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(
                JSONObject().put("address", host).put("port", port).put("users", JSONArray().put(user)))))
            .put("streamSettings", stream(p, host))
    }

    private fun trojan(link: String): JSONObject {
        val uri = Uri.parse(link)
        val host = hostOf(uri)
        val port = uri.port
        val password = uri.userInfo.orEmpty()
        require(host.isNotEmpty() && port in 1..65535 && password.isNotEmpty())
        val p = HashMap(query(uri))
        if (p["security"].isNullOrEmpty()) p["security"] = "tls"
        return JSONObject().put("protocol", "trojan")
            .put("settings", JSONObject().put("servers", JSONArray().put(
                JSONObject().put("address", host).put("port", port).put("password", password).put("level", 0))))
            .put("streamSettings", stream(p, host))
    }

    private fun vmess(link: String): JSONObject {
        val j = JSONObject(b64(link.substring(8)))
        val host = j.optString("add")
        val port = j.optString("port").toInt()
        val id = j.optString("id")
        require(host.isNotEmpty() && port in 1..65535 && id.isNotEmpty())
        val p = HashMap<String, String>()
        val net = j.optString("net").ifEmpty { "tcp" }
        p["type"] = net
        p["headerType"] = j.optString("type")
        p["security"] = if (j.optString("tls") == "tls") "tls" else "none"
        for (k in listOf("sni", "alpn", "fp", "host", "path")) j.optString(k).takeIf { it.isNotEmpty() }?.let { p[k] = it }
        if (net == "grpc") {
            p["serviceName"] = j.optString("path")
            if (j.optString("type") == "multi") p["mode"] = "multi"
        }
        val user = JSONObject().put("id", id).put("alterId", j.optString("aid").toIntOrNull() ?: 0)
            .put("security", j.optString("scy").ifEmpty { "auto" }).put("level", 0)
        return JSONObject().put("protocol", "vmess")
            .put("settings", JSONObject().put("vnext", JSONArray().put(
                JSONObject().put("address", host).put("port", port).put("users", JSONArray().put(user)))))
            .put("streamSettings", stream(p, host))
    }

    private fun shadowsocks(link: String): JSONObject {
        var rest = link.substring(5)
        rest = rest.substringBefore('#')
        require(!rest.substringAfter('?', "").contains("plugin="))
        rest = rest.substringBefore('?')
        val creds: String
        val hostPort: String
        if (rest.contains('@')) {
            val userInfo = rest.substringBeforeLast('@')
            hostPort = rest.substringAfterLast('@')
            creds = if (userInfo.contains(':')) Uri.decode(userInfo) else b64(Uri.decode(userInfo))
        } else {
            val all = b64(rest)
            creds = all.substringBeforeLast('@')
            hostPort = all.substringAfterLast('@')
        }
        val method = creds.substringBefore(':')
        val password = creds.substringAfter(':')
        val host = hostPort.substringBeforeLast(':').removePrefix("[").removeSuffix("]")
        val port = hostPort.substringAfterLast(':').toInt()
        require(method.isNotEmpty() && password.isNotEmpty() && host.isNotEmpty() && port in 1..65535)
        return JSONObject().put("protocol", "shadowsocks")
            .put("settings", JSONObject().put("servers", JSONArray().put(
                JSONObject().put("address", host).put("port", port).put("method", method).put("password", password).put("level", 0))))
    }

    private fun stream(p: Map<String, String>, address: String): JSONObject {
        val net = when (val t = (p["type"] ?: "tcp").lowercase()) {
            "", "raw" -> "tcp"
            "splithttp" -> "xhttp"
            "tcp", "ws", "grpc", "httpupgrade", "xhttp" -> t
            else -> throw IllegalArgumentException("unsupported transport $t")
        }
        val sec = (p["security"] ?: "none").lowercase().let { if (it.isEmpty()) "none" else it }
        val s = JSONObject().put("network", net).put("security", sec)
        when (sec) {
            "none" -> {}
            "tls" -> {
                val tls = JSONObject().put("serverName", p["sni"]?.takeIf { it.isNotEmpty() } ?: p["host"]?.takeIf { it.isNotEmpty() } ?: address)
                p["fp"]?.takeIf { it.isNotEmpty() }?.let { tls.put("fingerprint", it) }
                p["alpn"]?.takeIf { it.isNotEmpty() }?.let { tls.put("alpn", JSONArray(it.split(','))) }
                s.put("tlsSettings", tls)
            }
            "reality" -> {
                val pbk = p["pbk"].orEmpty()
                require(pbk.isNotEmpty())
                s.put("realitySettings", JSONObject()
                    .put("serverName", p["sni"].orEmpty())
                    .put("fingerprint", p["fp"]?.takeIf { it.isNotEmpty() } ?: "chrome")
                    .put("publicKey", pbk)
                    .put("shortId", p["sid"].orEmpty())
                    .put("spiderX", p["spx"].orEmpty()))
            }
            else -> throw IllegalArgumentException("unsupported security $sec")
        }
        val path = p["path"]?.takeIf { it.isNotEmpty() } ?: "/"
        val host = p["host"]?.takeIf { it.isNotEmpty() }
        when (net) {
            "tcp" -> if (p["headerType"] == "http") {
                s.put("tcpSettings", JSONObject().put("header", JSONObject().put("type", "http").put("request", JSONObject()
                    .put("path", JSONArray().put(path))
                    .put("headers", JSONObject().put("Host", JSONArray().put(host ?: address))))))
            }
            "ws" -> s.put("wsSettings", JSONObject().put("path", path).apply {
                if (host != null) put("headers", JSONObject().put("Host", host))
            })
            "grpc" -> s.put("grpcSettings", JSONObject().put("serviceName", p["serviceName"].orEmpty()).put("multiMode", p["mode"] == "multi"))
            "httpupgrade" -> s.put("httpupgradeSettings", JSONObject().put("path", path).apply { if (host != null) put("host", host) })
            "xhttp" -> s.put("xhttpSettings", JSONObject().put("path", path).apply {
                if (host != null) put("host", host)
                p["mode"]?.takeIf { it.isNotEmpty() }?.let { put("mode", it) }
                p["extra"]?.takeIf { it.isNotEmpty() }?.let { runCatching { put("extra", JSONObject(it)) } }
            })
        }
        return s
    }
}
