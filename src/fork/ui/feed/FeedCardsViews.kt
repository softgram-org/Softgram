package desu.inugram.ui.feed

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessageObject
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.BackupImageView

enum class FeedKind { PHOTO, VIDEO, GIF, TEXT, MUSIC, VOICE, POLL }

class FeedItem(val message: MessageObject, val extra: Boolean) {
    val group = ArrayList<MessageObject>().also { it.add(message) }
    val kind: FeedKind = kindOf(message)
    var caption: CharSequence? = captionOf(message, kind)

    fun addToGroup(other: MessageObject) {
        group.add(other)
        if (caption.isNullOrEmpty()) {
            caption = captionOf(other, kind)
        }
    }

    companion object {
        private fun kindOf(m: MessageObject): FeedKind {
            if (m.messageOwner.media is TLRPC.TL_messageMediaPoll) return FeedKind.POLL
            if (m.isVoice()) return FeedKind.VOICE
            if (m.isMusic()) return FeedKind.MUSIC
            if (m.isGif) return FeedKind.GIF
            if (m.isVideo || m.isRoundVideo) return FeedKind.VIDEO
            if (m.isPhoto) return FeedKind.PHOTO
            if (thumbsOf(m) != null) return FeedKind.PHOTO
            return FeedKind.TEXT
        }

        private fun captionOf(m: MessageObject, kind: FeedKind): CharSequence? {
            val raw = if (kind == FeedKind.TEXT) m.messageText else m.caption
            val text = raw?.toString()?.trim()
            return if (text.isNullOrEmpty()) null else text
        }

        fun thumbsOf(m: MessageObject): Pair<TLRPC.PhotoSize, Any>? {
            val media = m.messageOwner.media
            val photo = media?.photo ?: media?.webpage?.photo
            if (photo != null) {
                val size = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 800) ?: return null
                return Pair(size, photo)
            }
            val doc = m.document ?: return null
            val size = FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, 800) ?: return null
            return Pair(size, doc)
        }
    }
}

interface FeedCardHost {
    fun onOpenChannel(item: FeedItem)
    fun onOpenMedia(item: FeedItem)
    fun onTogglePlay(item: FeedItem)
    fun onMenu(item: FeedItem, anchor: View)
    fun isPlaying(item: FeedItem): Boolean
    fun channelTitle(item: FeedItem): String
}

class FeedPalette(
    val card: Int,
    val text: Int,
    val sub: Int,
    val accent: Int,
    val soft: Int,
)

class RatioFrame(context: Context) : FrameLayout(context) {
    var ratio = 1f

    init {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, AndroidUtilities.dpf2(17f))
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((w * ratio).toInt(), MeasureSpec.EXACTLY),
        )
    }
}

class PlayDisc(context: Context) : View(context) {
    var playing = false
        set(value) {
            field = value
            invalidate()
        }
    var discColor = Color.BLACK
    var glyphColor = Color.WHITE
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        paint.color = discColor
        canvas.drawCircle(cx, cy, minOf(cx, cy), paint)
        paint.color = glyphColor
        val s = minOf(cx, cy) * 0.42f
        if (playing) {
            canvas.drawRoundRect(cx - s * 0.9f, cy - s, cx - s * 0.2f, cy + s, 3f, 3f, paint)
            canvas.drawRoundRect(cx + s * 0.2f, cy - s, cx + s * 0.9f, cy + s, 3f, 3f, paint)
        } else {
            path.reset()
            path.moveTo(cx - s * 0.6f, cy - s)
            path.lineTo(cx + s, cy)
            path.lineTo(cx - s * 0.6f, cy + s)
            path.close()
            canvas.drawPath(path, paint)
        }
    }
}

class WaveView(context: Context, private val seed: Int) : View(context) {
    var barColor = Color.GRAY
    var active = false
        set(value) {
            field = value
            invalidate()
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        paint.color = barColor
        paint.alpha = if (active) 255 else 110
        val count = 32
        val gap = width / count.toFloat()
        val bar = gap * 0.55f
        var x = seed * 7 + 3
        for (i in 0 until count) {
            x = (x * 37 + 11) % 97
            val h = height * (0.22f + (x % 70) / 100f)
            val left = i * gap + (gap - bar) / 2f
            canvas.drawRoundRect(RectF(left, (height - h) / 2f, left + bar, (height + h) / 2f), bar / 2f, bar / 2f, paint)
        }
    }
}

class TextPlaceholder(context: Context) : View(context) {
    var tint = Color.GRAY
    var label = ""
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.style = Paint.Style.FILL
        paint.color = tint
        paint.alpha = 40
        canvas.drawRect(0f, 0f, w, h, paint)
        val bw = AndroidUtilities.dpf2(54f)
        val bh = AndroidUtilities.dpf2(40f)
        val l = (w - bw) / 2f
        val t = h / 2f - bh / 2f - AndroidUtilities.dpf2(8f)
        paint.alpha = 255
        paint.color = tint
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = AndroidUtilities.dpf2(3f)
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawRoundRect(RectF(l, t, l + bw, t + bh), AndroidUtilities.dpf2(9f), AndroidUtilities.dpf2(9f), paint)
        val lx = l + AndroidUtilities.dpf2(11f)
        canvas.drawLine(lx, t + bh * 0.35f, l + bw - AndroidUtilities.dpf2(11f), t + bh * 0.35f, paint)
        canvas.drawLine(lx, t + bh * 0.55f, l + bw - AndroidUtilities.dpf2(11f), t + bh * 0.55f, paint)
        canvas.drawLine(lx, t + bh * 0.75f, l + bw * 0.6f, t + bh * 0.75f, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = AndroidUtilities.dpf2(10.5f)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(label.uppercase(), w / 2f, t + bh + AndroidUtilities.dpf2(20f), paint)
    }
}

class FeedCardView(context: Context, private val host: FeedCardHost) : LinearLayout(context) {
    private val media = RatioFrame(context)
    private val image = BackupImageView(context)
    private val placeholder = TextPlaceholder(context)
    private val centerPlay = PlayDisc(context)
    private val cornerPlay = PlayDisc(context)
    private val durChip = chip()
    private val gifChip = chip()
    private val srcChip = chip()
    private val musicBox = LinearLayout(context)
    private val musicTitle = TextView(context)
    private val musicSub = TextView(context)
    private val voiceBox = LinearLayout(context)
    private val voiceDisc = PlayDisc(context)
    private var wave: WaveView? = null
    private val voiceDur = TextView(context)
    private val pollBox = LinearLayout(context)
    private val captionView = TextView(context)
    private val metaView = TextView(context)
    private var item: FeedItem? = null
    private var maxShift = 0f

    init {
        orientation = VERTICAL
        val pad = AndroidUtilities.dp(6f)
        setPadding(pad, pad, pad, pad)

        media.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        media.addView(placeholder, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        media.addView(centerPlay, FrameLayout.LayoutParams(AndroidUtilities.dp(46f), AndroidUtilities.dp(46f), Gravity.CENTER))
        media.addView(cornerPlay, FrameLayout.LayoutParams(AndroidUtilities.dp(38f), AndroidUtilities.dp(38f), Gravity.RIGHT or Gravity.BOTTOM).also {
            it.setMargins(0, 0, AndroidUtilities.dp(8f), AndroidUtilities.dp(8f))
        })
        media.addView(durChip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT or Gravity.TOP).also {
            it.setMargins(0, AndroidUtilities.dp(7f), AndroidUtilities.dp(7f), 0)
        })
        media.addView(gifChip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT or Gravity.TOP).also {
            it.setMargins(AndroidUtilities.dp(7f), AndroidUtilities.dp(7f), 0, 0)
        })
        media.addView(srcChip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT or Gravity.BOTTOM).also {
            it.setMargins(AndroidUtilities.dp(7f), 0, AndroidUtilities.dp(7f), AndroidUtilities.dp(7f))
        })
        srcChip.background = pill(0xCCFFFFFF.toInt())
        srcChip.setTextColor(0xFF14161A.toInt())
        srcChip.maxWidth = AndroidUtilities.dp(130f)
        srcChip.setSingleLine(true)
        srcChip.ellipsize = TextUtils.TruncateAt.END
        addView(media, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        musicTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
        musicTitle.setTypeface(Typeface.DEFAULT_BOLD)
        musicTitle.setSingleLine(true)
        musicTitle.ellipsize = TextUtils.TruncateAt.END
        musicSub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f)
        musicSub.setSingleLine(true)
        musicSub.ellipsize = TextUtils.TruncateAt.END
        musicBox.orientation = VERTICAL
        musicBox.addView(musicTitle)
        musicBox.addView(musicSub)
        addView(musicBox, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
            it.setMargins(AndroidUtilities.dp(8f), AndroidUtilities.dp(9f), AndroidUtilities.dp(8f), 0)
        })

        voiceBox.orientation = HORIZONTAL
        voiceBox.gravity = Gravity.CENTER_VERTICAL
        voiceBox.setPadding(AndroidUtilities.dp(10f), AndroidUtilities.dp(10f), AndroidUtilities.dp(10f), AndroidUtilities.dp(10f))
        voiceBox.addView(voiceDisc, LayoutParams(AndroidUtilities.dp(34f), AndroidUtilities.dp(34f)))
        voiceDur.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10.5f)
        addView(voiceBox, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        pollBox.orientation = VERTICAL
        val pp = AndroidUtilities.dp(12f)
        pollBox.setPadding(pp, pp, pp, pp)
        addView(pollBox, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        captionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f)
        captionView.maxLines = 4
        captionView.ellipsize = TextUtils.TruncateAt.END
        addView(captionView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
            it.setMargins(AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), 0)
        })
        metaView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10.5f)
        metaView.setSingleLine(true)
        metaView.ellipsize = TextUtils.TruncateAt.END
        addView(metaView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
            it.setMargins(AndroidUtilities.dp(8f), AndroidUtilities.dp(7f), AndroidUtilities.dp(8f), AndroidUtilities.dp(4f))
        })

        setOnClickListener { item?.let { host.onOpenChannel(it) } }
        setOnLongClickListener { item?.let { host.onMenu(it, this) }; true }
        media.setOnClickListener { item?.let { host.onOpenMedia(it) } }
        media.setOnLongClickListener { item?.let { host.onMenu(it, media) }; true }
        voiceBox.setOnClickListener { item?.let { host.onOpenChannel(it) } }
        voiceBox.setOnLongClickListener { item?.let { host.onMenu(it, voiceBox) }; true }
        voiceDisc.setOnClickListener { item?.let { host.onTogglePlay(it) } }
        voiceDisc.setOnLongClickListener { item?.let { host.onMenu(it, voiceBox) }; true }
        cornerPlay.setOnClickListener { item?.let { host.onTogglePlay(it) } }
        cornerPlay.setOnLongClickListener { item?.let { host.onMenu(it, media) }; true }
        pollBox.setOnClickListener { item?.let { host.onOpenChannel(it) } }
        pollBox.setOnLongClickListener { item?.let { host.onMenu(it, pollBox) }; true }
    }

    private fun chip(): TextView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10.5f)
        setTypeface(Typeface.DEFAULT_BOLD)
        setTextColor(Color.WHITE)
        background = pill(0x99000000.toInt())
        setPadding(AndroidUtilities.dp(7f), AndroidUtilities.dp(2f), AndroidUtilities.dp(7f), AndroidUtilities.dp(2f))
    }

    private fun pill(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = AndroidUtilities.dpf2(99f)
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        animate().cancel()
        animate().scaleX(if (pressed) 0.965f else 1f).scaleY(if (pressed) 0.965f else 1f)
            .setDuration(if (pressed) 110L else 380L).setInterpolator(OvershootInterpolator(2.2f)).start()
    }

    fun bind(it: FeedItem, palette: FeedPalette) {
        item = it
        val m = it.message
        background = GradientDrawable().apply {
            setColor(palette.card)
            cornerRadius = AndroidUtilities.dpf2(22f)
        }
        val kind = it.kind
        val hasFrame = kind == FeedKind.PHOTO || kind == FeedKind.VIDEO || kind == FeedKind.GIF || kind == FeedKind.TEXT || kind == FeedKind.MUSIC
        media.visibility = if (hasFrame) VISIBLE else GONE
        musicBox.visibility = if (kind == FeedKind.MUSIC) VISIBLE else GONE
        voiceBox.visibility = if (kind == FeedKind.VOICE) VISIBLE else GONE
        pollBox.visibility = if (kind == FeedKind.POLL) VISIBLE else GONE
        placeholder.visibility = if (kind == FeedKind.TEXT) VISIBLE else GONE
        image.visibility = if (kind == FeedKind.TEXT) GONE else VISIBLE
        centerPlay.visibility = if (kind == FeedKind.VIDEO) VISIBLE else GONE
        cornerPlay.visibility = if (kind == FeedKind.MUSIC) VISIBLE else GONE
        durChip.visibility = if (kind == FeedKind.VIDEO) VISIBLE else GONE
        gifChip.visibility = if (kind == FeedKind.GIF) VISIBLE else GONE
        media.isClickable = kind == FeedKind.PHOTO || kind == FeedKind.VIDEO || kind == FeedKind.GIF
        media.isLongClickable = true
        gifChip.text = "GIF"
        srcChip.text = host.channelTitle(it)
        srcChip.visibility = if (hasFrame) VISIBLE else GONE
        centerPlay.discColor = 0x73000000
        cornerPlay.discColor = 0xEBFFFFFF.toInt()
        cornerPlay.glyphColor = 0xFF111111.toInt()
        val thumb = FeedItem.thumbsOf(m)
        if (kind == FeedKind.PHOTO || kind == FeedKind.VIDEO || kind == FeedKind.GIF || kind == FeedKind.MUSIC) {
            var ratio = 1f
            if (kind == FeedKind.MUSIC) ratio = 1f
            else if (thumb != null && thumb.first.w > 0 && thumb.first.h > 0) {
                ratio = (thumb.first.h.toFloat() / thumb.first.w).coerceIn(0.8f, 1.35f)
            }
            media.ratio = ratio
            if (thumb != null) {
                val location = if (thumb.second is TLRPC.Photo) ImageLocation.getForPhoto(thumb.first, thumb.second as TLRPC.Photo)
                else ImageLocation.getForDocument(thumb.first, thumb.second as TLRPC.Document)
                image.setImage(location, "600_600", ColorDrawable(palette.soft), m)
            } else {
                image.imageReceiver.setImageBitmap(ColorDrawable(palette.soft))
            }
            image.scaleX = 1.16f
            image.scaleY = 1.16f
        } else if (kind == FeedKind.TEXT) {
            media.ratio = 0.62f
            placeholder.tint = palette.sub
            placeholder.label = LocaleController.getString(R.string.InuFeedTextPost)
            placeholder.invalidate()
            image.scaleX = 1f
            image.scaleY = 1f
        }
        if (kind == FeedKind.VIDEO) durChip.text = fmt(m.duration)
        if (kind == FeedKind.MUSIC) {
            musicTitle.text = m.musicTitle?.takeIf { s -> s.isNotEmpty() } ?: LocaleController.getString(R.string.InuFeedAudio)
            musicSub.text = (m.musicAuthor ?: "") + (if (m.duration > 0) " · " + fmt(m.duration) else "")
            musicTitle.setTextColor(palette.text)
            musicSub.setTextColor(palette.sub)
        }
        if (kind == FeedKind.VOICE) {
            voiceBox.background = GradientDrawable().apply {
                setColor(palette.soft)
                cornerRadius = AndroidUtilities.dpf2(17f)
            }
            voiceBox.removeView(wave)
            wave = WaveView(context, m.id).also { w ->
                w.barColor = palette.accent
                voiceBox.addView(w, 1, LayoutParams(0, AndroidUtilities.dp(30f), 1f).also { lp ->
                    lp.setMargins(AndroidUtilities.dp(9f), 0, AndroidUtilities.dp(9f), 0)
                })
            }
            voiceBox.removeView(voiceDur)
            voiceDur.text = fmt(m.duration)
            voiceDur.setTextColor(palette.sub)
            voiceBox.addView(voiceDur)
            voiceDisc.discColor = palette.accent
            voiceDisc.glyphColor = Color.WHITE
        }
        if (kind == FeedKind.POLL) bindPoll(m, palette)
        refreshPlayState()

        val cap = it.caption
        captionView.visibility = if (cap.isNullOrEmpty()) GONE else VISIBLE
        captionView.text = cap
        captionView.setTextColor(palette.text)
        metaView.text = host.channelTitle(it) + " · " + LocaleController.formatDateChat(m.messageOwner.date.toLong())
        metaView.setTextColor(palette.sub)
    }

    private fun bindPoll(m: MessageObject, palette: FeedPalette) {
        pollBox.removeAllViews()
        pollBox.background = GradientDrawable().apply {
            setColor(palette.soft)
            cornerRadius = AndroidUtilities.dpf2(17f)
        }
        val poll = (m.messageOwner.media as? TLRPC.TL_messageMediaPoll)?.poll ?: return
        val q = TextView(context)
        q.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
        q.setTypeface(Typeface.DEFAULT_BOLD)
        q.setTextColor(palette.text)
        q.text = poll.question?.text ?: LocaleController.getString(R.string.InuFeedPoll)
        pollBox.addView(q)
        var shown = 0
        for (answer in poll.answers) {
            if (shown == 4) break
            shown++
            val row = TextView(context)
            row.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
            row.setTextColor(palette.text)
            row.setSingleLine(true)
            row.ellipsize = TextUtils.TruncateAt.END
            row.text = answer.text?.text ?: ""
            row.background = GradientDrawable().apply {
                setColor(palette.card)
                cornerRadius = AndroidUtilities.dpf2(11f)
            }
            row.setPadding(AndroidUtilities.dp(10f), AndroidUtilities.dp(7f), AndroidUtilities.dp(10f), AndroidUtilities.dp(7f))
            pollBox.addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { lp ->
                lp.topMargin = AndroidUtilities.dp(7f)
            })
        }
    }

    fun refreshPlayState() {
        val it = item ?: return
        val playing = host.isPlaying(it)
        cornerPlay.playing = playing
        voiceDisc.playing = playing
        wave?.active = playing
    }

    fun applyParallax(offset: Float) {
        val it = item ?: return
        if (it.kind == FeedKind.TEXT || media.visibility != VISIBLE) return
        maxShift = media.height * 0.075f
        image.translationY = -offset.coerceIn(-1f, 1f) * maxShift
    }

    private fun fmt(seconds: Int): String = String.format("%d:%02d", seconds / 60, seconds % 60)
}
