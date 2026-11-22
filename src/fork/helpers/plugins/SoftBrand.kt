package desu.inugram.helpers.plugins

// Softgram: third-party plugin metadata may still carry the upstream brand; users only ever see "Softgram"
object SoftBrand {
    private val RX = Regex("(entiny|inu)[\\s_-]*gram", RegexOption.IGNORE_CASE)

    @JvmStatic
    fun clean(text: String?): String? = text?.let { RX.replace(it, "Softgram") }
}
