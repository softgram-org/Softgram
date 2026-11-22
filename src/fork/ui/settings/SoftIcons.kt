package desu.inugram.ui.settings

import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.R
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Components.IconBackgroundColors
import org.telegram.ui.Components.UItem
import org.telegram.messenger.Utilities

// Softgram: colorful gradient plates behind settings icons, same look as the official Telegram settings
object SoftIcons {
    private val FALLBACK = listOf(
        IconBackgroundColors.BLUE, IconBackgroundColors.GREEN, IconBackgroundColors.ORANGE,
        IconBackgroundColors.RED, IconBackgroundColors.CYAN, IconBackgroundColors.PURPLE,
        IconBackgroundColors.BLUE_DEEP, IconBackgroundColors.ORANGE_DEEP,
    )

    private val MAP: Map<Int, IconBackgroundColors> by lazy {
        mapOf(
            R.drawable.entiny_appearance to IconBackgroundColors.PURPLE,
            R.drawable.entiny_home to IconBackgroundColors.BLUE,
            R.drawable.entiny_hide_clutter to IconBackgroundColors.GRAY,
            R.drawable.entiny_message_menu to IconBackgroundColors.GREEN,
            R.drawable.entiny_phone to IconBackgroundColors.ORANGE,
            R.drawable.entiny_network_notifications to IconBackgroundColors.RED,
            R.drawable.entiny_profile_accounts to IconBackgroundColors.CYAN,
            R.drawable.entiny_privacy to IconBackgroundColors.BLUE_DEEP,
            R.drawable.entiny_ghost to IconBackgroundColors.ORANGE_DEEP,
            R.drawable.entiny_proxy to IconBackgroundColors.BLUE,
            R.drawable.entiny_ai_translation to IconBackgroundColors.PURPLE,
            R.drawable.entiny_app_controls to IconBackgroundColors.GRAY,
            R.drawable.entiny_plugins to IconBackgroundColors.GREEN,
            R.drawable.entiny_about to IconBackgroundColors.BLUE_ALT,
            R.drawable.entiny_trash to IconBackgroundColors.RED,
            R.drawable.msg_delete to IconBackgroundColors.RED,
            R.drawable.entiny_telegram to IconBackgroundColors.BLUE_LIGHT,
            R.drawable.entiny_support to IconBackgroundColors.RED,
            R.drawable.entiny_bitcoin to IconBackgroundColors.ORANGE,
            R.drawable.entiny_ethereum to IconBackgroundColors.BLUE_DEEP,
            R.drawable.entiny_bnb_chain to IconBackgroundColors.ORANGE_DEEP,
            R.drawable.entiny_solana to IconBackgroundColors.PURPLE,
            R.drawable.entiny_trx to IconBackgroundColors.RED,
            R.drawable.entiny_ton to IconBackgroundColors.BLUE,
            R.drawable.entiny_tether to IconBackgroundColors.GREEN,
        )
    }

    @JvmStatic
    fun colors(res: Int): IconBackgroundColors = MAP[res] ?: FALLBACK[(res and 0x7fffffff) % FALLBACK.size]

    @JvmStatic
    fun apply(cell: TextCell, res: Int) {
        val c = colors(res)
        cell.setColorfulIcon(c.top, c.bottom, res, true)
    }

    @JvmStatic
    fun plate(iv: ImageView, res: Int) {
        val c = colors(res)
        iv.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c.top, c.bottom)).apply {
            cornerRadius = dp(7f).toFloat()
        }
        iv.setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
    }

    @JvmStatic
    fun button(id: Int, iconRes: Int, text: CharSequence): UItem =
        UItem.asButton(id, iconRes, text).also { bindIcon(it, iconRes) }

    @JvmStatic
    fun button(id: Int, iconRes: Int, text: CharSequence, value: CharSequence?): UItem =
        UItem.asButton(id, iconRes, text, value).also { bindIcon(it, iconRes) }

    private fun bindIcon(item: UItem, iconRes: Int) {
        item.bind = Utilities.Callback { view -> (view as? TextCell)?.let { apply(it, iconRes) } }
    }
}
