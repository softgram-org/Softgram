package desu.inugram.ui.settings

import android.view.View
import desu.inugram.helpers.InuUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter

// Softgram: support page, crypto wallets only (tap to copy)
class DonateActivity : SettingsPageActivity() {

    private class Method(val icon: Int, val title: String, val value: String) {
        val id = InuUtils.generateId()
        val shown get() = if (value.length <= 26) value else value.take(12) + "…" + value.takeLast(10)
    }

    private val coins = listOf(
        Method(R.drawable.entiny_bitcoin, "Bitcoin · BTC", "bc1pthdkr6y2v0tadzpkxa53fecewynty9mkujyv7lgvwjjahrzzecgqhdv2j3"),
        Method(R.drawable.entiny_ethereum, "Ethereum · ETH", "0x756752Aa313F9B0ea02cCe8D30eAa32c5e1B863a"),
        Method(R.drawable.entiny_bnb_chain, "BNB · BSC", "0x756752Aa313F9B0ea02cCe8D30eAa32c5e1B863a"),
        Method(R.drawable.entiny_solana, "Solana · SOL", "tFQyE7w48MdVBPEcvhP4pjB4UwCkcrndLAJ9MmQMkfu"),
        Method(R.drawable.softgram_polygon, "Polygon · POL", "0x756752Aa313F9B0ea02cCe8D30eAa32c5e1B863a"),
        Method(R.drawable.entiny_trx, "Tron · TRX", "TFkhuAJptzRxGpcaJ58aZwNWWxWxrNoDNk"),
        Method(R.drawable.softgram_hyperliquid, "Hyperliquid · HYPE", "0x756752Aa313F9B0ea02cCe8D30eAa32c5e1B863a"),
        Method(R.drawable.entiny_ton, "TON", "UQAdQqO5KIhOd2zcJWYrRm0T9rT5xupEp6ps2o2z5XO3cZVb"),
        Method(R.drawable.softgram_dogecoin, "Dogecoin · DOGE", "DNDWF3Jj9sRo7g6yizDAMyP39o2E9YHNNp"),
    )

    override fun getTitle(): CharSequence = str(R.string.InuDonateSheetTitle)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asShadow(str(R.string.InuDonateSheetInfo)))
        items.add(UItem.asHeader(str(R.string.InuDonateSectionCoins)))
        coins.forEach { items.add(mkTwoLineEntry(it.id, it.icon, it.title, it.shown)) }
        items.add(UItem.asShadow(null))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        val method = coins.firstOrNull { it.id == item.id } ?: return
        AndroidUtilities.addToClipboard(method.value)
        BulletinFactory.of(this).createCopyBulletin(str(R.string.InuDonateCopied)).show()
    }

    private fun str(res: Int) = LocaleController.getString(res)
}
