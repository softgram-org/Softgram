package desu.inugram.helpers

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import desu.inugram.helpers.proxy.xray.XrayManager
import org.telegram.messenger.LocaleController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.BulletinFactory
import org.telegram.utils.proxy.ProxySettings

object ProxyImportHelper {
    private const val TRUST_PREFS = "softgram_xray"
    private const val TRUST_KEY = "trust_ack"

    @JvmStatic
    fun importFromClipboard(fragment: BaseFragment) {
        val context = fragment.context ?: return
        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()

        val hasXray = text.lineSequence().any { XrayManager.isXrayLink(it.trim()) }
        val prefs = context.getSharedPreferences(TRUST_PREFS, Context.MODE_PRIVATE)
        if (hasXray && !prefs.getBoolean(TRUST_KEY, false)) {
            AlertDialog.Builder(context)
                .setTitle(LocaleController.getString(R.string.InuXrayTrustTitle))
                .setMessage(LocaleController.getString(R.string.InuXrayTrustText))
                .setPositiveButton(LocaleController.getString(R.string.InuXrayTrustImport)) { _, _ ->
                    prefs.edit().putBoolean(TRUST_KEY, true).apply()
                    runImport(fragment, text)
                }
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show()
            return
        }
        runImport(fragment, text)
    }

    private fun runImport(fragment: BaseFragment, text: String) {
        SharedConfig.loadProxyList()
        val known = SharedConfig.proxyList.mapTo(HashSet()) { it.settings }
        val imported = text.lineSequence()
            .mapNotNull { raw ->
                val line = raw.trim()
                val link = if (XrayManager.isXrayLink(line)) XrayManager.importLink(line) else line
                link?.let { ProxySettings.fromUri(Uri.parse(it)) }
            }
            .filter { it.isValid && known.add(it) }
            .map { SharedConfig.ProxyInfo(it) }
            .toList()

        if (imported.isEmpty()) {
            BulletinFactory.of(fragment).createErrorBulletin(
                LocaleController.getString(R.string.InuProxyImportNothing)
            ).show()
            return
        }
        SharedConfig.proxyList.addAll(0, imported)
        SharedConfig.saveProxyList()
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged)
        BulletinFactory.of(fragment).createSimpleBulletin(
            R.raw.contact_check,
            LocaleController.formatPluralString("InuProxyImported", imported.size)
        ).show()
    }
}
