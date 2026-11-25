package desu.inugram.ui.settings

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper

// Softgram Academics: section title with its slogan directly under it
class AcademicsHeaderView(context: Context) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        val side = AndroidUtilities.dp(21f)
        setPadding(side, AndroidUtilities.dp(15f), side, AndroidUtilities.dp(10f))
        val align = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT

        val title = TextView(context).apply {
            text = LocaleController.getString(R.string.InuAcademics)
            textSize = 15f
            typeface = AndroidUtilities.bold()
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader))
            gravity = align
        }
        addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

        val slogan = TextView(context).apply {
            text = LocaleController.getString(R.string.InuAcademicsSlogan)
            textSize = 13f
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2))
            gravity = align
        }
        addView(slogan, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 3f, 0f, 0f))
    }
}
