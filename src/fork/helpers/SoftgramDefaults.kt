package desu.inugram.helpers

import android.content.Context
import androidx.core.content.edit
import desu.inugram.helpers.cloud.CloudSettingsHelper
import desu.inugram.helpers.cloud.SettingsBackupHelper
import org.telegram.messenger.R

// softgram: factory defaults come from the bundled softgram_defaults.json (same format as a settings export)
object SoftgramDefaults {
    private const val SEED_PREFS = "softgram_seed"
    private const val SEED_DONE = "defaults_v1"
    private const val SEED_PENDING = "defaults_v1_pending"

    // true only on a clean install, before any setting has been written
    @JvmStatic
    fun isFreshInstall(context: Context): Boolean {
        val seed = context.getSharedPreferences(SEED_PREFS, Context.MODE_PRIVATE)
        if (seed.getBoolean(SEED_DONE, false)) return false
        if (seed.getBoolean(SEED_PENDING, false)) return true
        val fresh = context.getSharedPreferences("inugram", Context.MODE_PRIVATE).all.isEmpty()
        if (fresh) seed.edit { putBoolean(SEED_PENDING, true) }
        return fresh
    }

    @JvmStatic
    fun seed(context: Context) {
        try {
            if (applyBundled(context)) {
                context.getSharedPreferences(SEED_PREFS, Context.MODE_PRIVATE).edit {
                    putBoolean(SEED_DONE, true)
                    remove(SEED_PENDING)
                }
            }
        } catch (_: Throwable) {
        }
    }

    @JvmStatic
    fun applyBundled(context: Context): Boolean {
        val json = context.resources.openRawResource(R.raw.softgram_defaults).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val parsed = SettingsBackupHelper.parse(json) as? SettingsBackupHelper.ParseResult.Ok ?: return false
        SettingsBackupHelper.apply(parsed)
        return true
    }

    // "reset to defaults" goes back to the Softgram defaults, not to the stock code defaults
    @JvmStatic
    fun reapplyAfterReset(context: Context) {
        if (CloudSettingsHelper.restoring) return
        try {
            applyBundled(context)
        } catch (_: Throwable) {
        }
    }
}
