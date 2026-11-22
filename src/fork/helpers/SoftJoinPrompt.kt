package desu.inugram.helpers

import android.content.Context
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.browser.Browser
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.LaunchActivity

object SoftJoinPrompt {
    private const val PREFS = "softgram_join_prompt"
    private const val KEY_SHOWN = "shown"
    private const val CHANNEL_URL = "https://t.me/Softgram_org"

    @JvmStatic
    fun maybeShow(activity: LaunchActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SHOWN, false)) return
        if (!UserConfig.getInstance(UserConfig.selectedAccount).isClientActivated) return
        prefs.edit().putBoolean(KEY_SHOWN, true).apply()
        AlertDialog.Builder(activity)
            .setTitle(LocaleController.getString(R.string.InuSoftJoinTitle))
            .setMessage(LocaleController.getString(R.string.InuSoftJoinText))
            .setPositiveButton(LocaleController.getString(R.string.InuSoftJoinAction)) { _, _ -> Browser.openUrl(activity, CHANNEL_URL) }
            .setNegativeButton(LocaleController.getString(R.string.InuSoftJoinLater), null)
            .show()
    }

    @JvmStatic
    fun scheduleAfterLogin() {
        AndroidUtilities.runOnUIThread({ LaunchActivity.instance?.let { maybeShow(it) } }, 2500)
    }
}
