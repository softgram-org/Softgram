package desu.inugram.helpers.feed

import desu.inugram.InuConfig
import desu.inugram.ui.feed.FeedActivity
import desu.inugram.ui.feed.FeedCardsActivity
import org.telegram.ui.ActionBar.BaseFragment

object FeedHelper {
    @JvmStatic
    fun isEnabled(): Boolean = InuConfig.FEED_ENABLED.value

    @JvmStatic
    fun setEnabled(enabled: Boolean) {
        InuConfig.FEED_ENABLED.value = enabled
    }

    @JvmStatic
    fun createFragment(): BaseFragment =
        if (InuConfig.FEED_CARDS.value) FeedCardsActivity() else FeedActivity()

    @JvmStatic
    fun createTabFragment(): BaseFragment =
        if (InuConfig.FEED_CARDS.value) FeedCardsActivity(FeedScope.Global, true) else FeedActivity(FeedScope.Global, true)
}
