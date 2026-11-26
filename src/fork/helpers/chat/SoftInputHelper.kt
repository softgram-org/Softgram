package desu.inugram.helpers.chat

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser
import desu.inugram.InuConfig
import desu.inugram.helpers.theme.NonIslandHelper
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.ChatActivityEnterViewAnimatedIconView

/**
 * "Appearance Soft": one light pill (emoji, divider, field, folder) and a round action button next to it.
 * The pill and the round button reuse the iOS glass bubbles, so the Liquid Glass settings apply to them.
 * Every icon is drawn in code in the theme icon color, so no resources are needed.
 */
object SoftInputHelper {
    @JvmStatic
    fun isSoft(): Boolean = InuConfig.SOFT_INPUT_APPEARANCE.value && !InuConfig.NON_ISLAND_CHAT_ELEMENTS.value

    @JvmStatic
    fun iconColor(): Int = Theme.getColor(Theme.key_glass_defaultIcon)

    /** called from the emoji / voice / video icon view; returns true when it drew the icon itself */
    @JvmStatic
    fun drawIcon(view: View, canvas: Canvas, state: ChatActivityEnterViewAnimatedIconView.State?): Boolean {
        if (!isSoft() || state == null) return false
        val cx = view.width / 2f
        val cy = view.height / 2f
        val size = minOf(view.width, view.height).toFloat()
        when (state) {
            ChatActivityEnterViewAnimatedIconView.State.VOICE ->
                SoftIcons.drawVoice(canvas, cx, cy, size - AndroidUtilities.dpf2(2f), iconColor())
            ChatActivityEnterViewAnimatedIconView.State.VIDEO ->
                SoftIcons.drawRecord(canvas, cx, cy, size - AndroidUtilities.dpf2(2f), iconColor())
            ChatActivityEnterViewAnimatedIconView.State.SMILE ->
                SoftIcons.drawSmile(canvas, cx, cy, size * 0.235f, iconColor())
            else -> return false
        }
        return true
    }

    /** the Softgram plane for every send button; any other icon (schedule, done) stays as it was */
    @JvmStatic
    fun sendIcon(context: Context, resId: Int, fallback: Drawable): Drawable =
        if (isSoft() && (resId == NonIslandHelper.chatSendIcon() || resId == R.drawable.softgram_send_24 || resId == R.drawable.softgram_send_21)) SoftPlaneDrawable() else fallback

    @JvmStatic
    fun attachIcon(): Drawable? = if (isSoft()) SoftFolderDrawable() else null

    /** voice / video message glyph used inside the recording circle */
    @JvmStatic
    fun recordIcon(video: Boolean): Drawable = SoftRecordDrawable(video)

    /** the recording circle is a light bubble with a dark icon instead of the blue one */
    @JvmStatic
    fun recordCircleColor(fallback: Int): Int = if (isSoft()) Theme.getColor(Theme.key_chat_messagePanelBackground) else fallback

    @JvmStatic
    fun recordWaveColor(fallback: Int): Int = if (isSoft()) 0xFF8E98A4.toInt() else fallback

    /** keeps the text inside the pill when the paperclip is gone (editing a message) */
    @JvmStatic
    fun minFieldRightPx(gapDp: Int): Int = if (isSoft()) AndroidUtilities.dp((gapDp + 10).toFloat()) else 0

    /** the thin vertical line between the emoji button and the text; the canvas is in field container coordinates */
    @JvmStatic
    fun drawDivider(canvas: Canvas, emoji: View) {
        val x = emoji.right + AndroidUtilities.dpf2(4f)
        val cy = (emoji.top + emoji.bottom) / 2f
        SoftIcons.drawDivider(canvas, x, cy, iconColor(), emoji.alpha)
    }
}

object SoftIcons {
    private const val SIL_PATH = "M323.0,336.0 C318.2,336.9 312.7,336.3 308.0,334.9 C303.3,333.4 298.9,330.4 294.9,327.4 C290.8,324.5 287.4,320.6 283.8,317.1 C280.2,313.5 276.8,309.7 273.4,306.1 C270.0,302.4 266.6,298.6 263.1,294.9 C259.7,291.2 256.1,287.6 252.7,283.9 C249.2,280.2 245.9,276.4 242.5,272.7 C239.0,269.0 235.5,265.4 232.0,261.8 C228.5,258.1 225.1,253.4 221.5,250.8 C218.0,248.2 214.0,244.9 210.9,246.4 C207.9,247.8 205.8,255.1 203.2,259.4 C200.6,263.7 198.0,268.1 195.3,272.4 C192.7,276.7 190.1,281.0 187.4,285.3 C184.7,289.6 182.0,293.8 179.2,298.1 C176.5,302.3 173.8,306.6 170.9,310.7 C167.9,314.8 165.1,319.2 161.6,322.7 C158.0,326.2 154.0,329.8 149.6,331.8 C145.1,333.8 139.6,335.0 134.9,334.6 C130.1,334.2 125.4,331.8 121.1,329.4 C116.9,327.0 112.9,323.9 109.5,320.4 C106.2,316.8 103.7,312.2 101.0,308.0 C98.2,303.8 95.6,299.4 92.9,295.2 C90.1,290.9 87.4,286.6 84.7,282.4 C82.0,278.1 79.2,273.9 76.5,269.6 C73.8,265.3 71.1,261.1 68.3,256.8 C65.6,252.6 62.8,248.4 60.0,244.2 C57.2,239.9 54.5,235.7 51.7,231.5 C48.9,227.3 46.0,223.1 43.2,218.9 C40.3,214.8 37.4,210.6 34.6,206.5 C31.7,202.3 28.8,198.1 25.9,194.0 C23.0,189.9 19.9,185.8 16.9,181.8 C13.9,177.7 10.4,174.0 7.7,169.7 C5.1,165.4 2.3,160.9 1.0,156.2 C-0.2,151.4 -0.9,145.9 0.2,141.2 C1.2,136.5 4.3,131.9 7.5,128.1 C10.6,124.4 14.9,121.2 19.1,118.6 C23.4,116.0 28.3,114.3 33.0,112.5 C37.7,110.7 42.6,109.4 47.4,107.8 C52.2,106.2 57.0,104.6 61.8,103.0 C66.6,101.4 71.4,99.9 76.2,98.3 C81.0,96.8 85.8,95.1 90.6,93.5 C95.4,92.0 100.3,90.5 105.1,88.9 C109.9,87.3 114.7,85.7 119.5,84.1 C124.2,82.5 129.0,80.9 133.8,79.3 C138.6,77.7 143.5,76.2 148.3,74.6 C153.1,73.0 157.9,71.4 162.7,69.9 C167.5,68.3 172.3,66.8 177.1,65.2 C181.9,63.6 186.7,62.0 191.5,60.4 C196.3,58.8 201.1,57.3 205.9,55.8 C210.7,54.2 215.5,52.6 220.3,51.0 C225.1,49.4 229.9,47.9 234.8,46.3 C239.6,44.8 244.4,43.3 249.2,41.7 C254.0,40.1 258.8,38.5 263.6,36.9 C268.4,35.3 273.2,33.8 278.0,32.2 C282.8,30.7 287.6,29.0 292.4,27.4 C297.2,25.9 302.0,24.4 306.8,22.8 C311.6,21.2 316.4,19.6 321.2,18.0 C326.0,16.4 330.8,14.8 335.6,13.2 C340.4,11.6 345.2,10.0 350.0,8.4 C354.8,6.8 359.6,5.2 364.4,3.8 C369.3,2.4 374.2,0.4 379.1,0.0 C384.0,-0.3 389.4,-0.0 394.0,1.6 C398.6,3.2 403.3,6.3 406.6,9.8 C409.9,13.4 412.4,18.3 413.9,23.0 C415.3,27.7 415.5,33.0 415.2,38.0 C415.0,43.0 413.7,48.0 412.6,52.9 C411.5,57.8 409.9,62.6 408.6,67.5 C407.3,72.4 405.9,77.3 404.6,82.1 C403.2,87.0 401.8,91.9 400.5,96.7 C399.1,101.6 397.8,106.5 396.5,111.4 C395.2,116.3 393.8,121.1 392.5,126.0 C391.2,130.9 389.9,135.8 388.6,140.7 C387.3,145.6 386.0,150.4 384.7,155.3 C383.4,160.2 382.2,165.1 380.9,170.0 C379.7,174.9 378.5,179.8 377.3,184.7 C376.0,189.6 374.8,194.5 373.6,199.4 C372.4,204.3 371.2,209.3 370.0,214.2 C368.8,219.1 367.6,224.0 366.5,228.9 C365.3,233.8 364.2,238.8 363.0,243.7 C361.9,248.6 360.7,253.5 359.6,258.5 C358.4,263.4 357.3,268.3 356.2,273.3 C355.1,278.2 354.0,283.1 352.9,288.1 C351.9,293.0 351.0,298.0 349.7,302.9 C348.4,307.8 347.4,312.9 345.2,317.3 C343.0,321.8 340.2,326.5 336.5,329.6 C332.8,332.7 327.8,335.2 323.0,336.0 Z"
    private const val FOLD_PATH = "M141.0,334.6 C137.1,335.2 132.6,335.0 129.0,334.0 C125.3,333.0 122.0,330.9 119.1,328.7 C116.2,326.6 114.1,323.8 111.5,321.1 C109.0,318.3 106.2,315.5 103.8,312.3 C101.5,309.2 99.4,305.6 97.3,302.2 C95.1,298.8 92.9,295.4 90.8,292.0 C88.6,288.6 86.5,285.2 84.3,281.8 C82.2,278.4 80.0,275.0 77.8,271.6 C75.7,268.3 73.5,264.9 71.3,261.5 C69.1,258.1 67.0,254.7 64.8,251.3 C62.6,248.0 60.3,244.6 58.1,241.3 C55.9,237.9 53.7,234.5 51.5,231.2 C49.2,227.9 47.0,224.5 44.7,221.2 C42.4,217.9 40.2,214.5 37.9,211.2 C35.6,207.9 33.3,204.6 31.0,201.3 C28.7,198.0 26.4,194.7 24.0,191.5 C21.7,188.2 19.3,185.0 16.9,181.7 C14.5,178.5 11.8,175.4 9.5,172.1 C7.3,168.8 4.8,165.6 3.1,161.9 C1.5,158.3 0.2,154.3 -0.2,150.4 C-0.5,146.5 -0.5,141.9 1.0,138.5 C2.5,135.0 5.7,131.8 8.9,129.7 C12.2,127.6 16.4,126.4 20.3,125.8 C24.2,125.2 28.3,125.6 32.3,126.1 C36.3,126.5 40.2,127.5 44.1,128.5 C48.0,129.4 51.9,130.7 55.7,131.9 C59.5,133.2 63.3,134.6 67.1,136.0 C70.8,137.4 74.5,139.0 78.2,140.6 C81.9,142.2 85.6,143.8 89.2,145.6 C92.9,147.3 96.5,149.1 100.0,151.0 C103.6,152.8 107.1,154.8 110.6,156.8 C114.1,158.8 117.5,160.9 120.9,163.1 C124.3,165.2 127.6,167.5 131.0,169.7 C134.3,172.0 137.6,174.3 140.9,176.7 C144.1,179.0 147.4,181.4 150.5,183.9 C153.7,186.3 156.9,188.9 160.0,191.4 C163.1,194.0 166.1,196.6 169.2,199.2 C172.2,201.8 175.2,204.5 178.2,207.2 C181.2,209.9 184.2,212.6 187.1,215.4 C190.0,218.1 192.9,220.9 195.8,223.7 C198.7,226.5 201.7,229.3 204.4,232.2 C207.2,235.1 211.8,237.8 212.4,241.1 C212.9,244.5 209.4,248.5 207.6,252.0 C205.8,255.5 203.5,258.9 201.4,262.4 C199.4,265.8 197.3,269.2 195.2,272.7 C193.0,276.1 190.9,279.5 188.8,282.9 C186.7,286.4 184.6,289.8 182.4,293.2 C180.3,296.6 178.1,300.0 175.9,303.3 C173.6,306.7 171.4,310.1 169.0,313.3 C166.7,316.5 164.3,319.9 161.5,322.7 C158.7,325.6 155.7,328.4 152.2,330.4 C148.8,332.4 144.9,334.0 141.0,334.6 Z"

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val rect = RectF()
    private val tmpPath = Path()

    private val silPath: Path by lazy { PathParser.createPathFromPathData(SIL_PATH) }
    private val foldPath: Path by lazy { PathParser.createPathFromPathData(FOLD_PATH) }

    // the folder is drawn in a 24 x 24 box
    private val folderBack: Path by lazy {
        Path().apply {
            moveTo(2.6f, 7f)
            quadTo(2.6f, 4.6f, 5f, 4.6f)
            lineTo(9.2f, 4.6f)
            quadTo(10.1f, 4.6f, 10.7f, 5.3f)
            lineTo(11.8f, 6.6f)
            lineTo(19f, 6.6f)
            quadTo(21.4f, 6.6f, 21.4f, 9f)
            lineTo(21.4f, 17.4f)
            quadTo(21.4f, 19.8f, 19f, 19.8f)
            lineTo(5f, 19.8f)
            quadTo(2.6f, 19.8f, 2.6f, 17.4f)
            close()
        }
    }
    private val folderGap: Path by lazy {
        Path().apply {
            moveTo(2.4f, 10.9f)
            lineTo(9.6f, 10.9f)
            quadTo(10.5f, 10.9f, 11.2f, 10.2f)
            lineTo(12.3f, 9.1f)
            quadTo(13f, 8.4f, 14f, 8.4f)
            lineTo(21.6f, 8.4f)
        }
    }

    // the plane is traced in a 415 x 336 box
    private const val PLANE_W = 415.0f
    private const val PLANE_H = 336.0f

    private var shaderIcon = 0
    private var shaderBg = 0
    private var foldShader: Shader? = null

    private fun foldShader(icon: Int, bg: Int): Shader {
        val cached = foldShader
        if (cached != null && shaderIcon == icon && shaderBg == bg) return cached
        val s = LinearGradient(
            209.0f, 249.5f, 125.5f, 329.0f,
            ColorUtils.blendARGB(icon, bg, 0.45f),
            ColorUtils.blendARGB(icon, bg, 0.82f),
            Shader.TileMode.CLAMP
        )
        foldShader = s
        shaderIcon = icon
        shaderBg = bg
        return s
    }

    private fun reset(style: Paint.Style, color: Int, alpha: Int = 255) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = style
        paint.color = color
        paint.alpha = alpha
    }

    /** five rounded bars; d is the diameter of the round button the bars sit in */
    @JvmStatic
    fun drawVoice(canvas: Canvas, cx: Float, cy: Float, d: Float, color: Int, alpha: Int = 255) {
        val offsets = floatArrayOf(-0.179f, -0.0896f, 0f, 0.0896f, 0.179f)
        val heights = floatArrayOf(0.112f, 0.27f, 0.5f, 0.27f, 0.112f)
        val sw = d * 0.042f
        reset(Paint.Style.STROKE, color, alpha)
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = sw
        for (i in offsets.indices) {
            val half = heights[i] * d / 2f - sw / 2f
            canvas.drawLine(cx + offsets[i] * d, cy - half, cx + offsets[i] * d, cy + half, paint)
        }
    }

    /** video message glyph: a ring with a dot in the middle; d is the diameter of the round button it sits in */
    @JvmStatic
    fun drawRecord(canvas: Canvas, cx: Float, cy: Float, d: Float, color: Int, alpha: Int = 255) {
        val sw = d * 0.05f
        reset(Paint.Style.STROKE, color, alpha)
        paint.strokeWidth = sw
        canvas.drawCircle(cx, cy, d * 0.3f - sw / 2f, paint)
        reset(Paint.Style.FILL, color, alpha)
        canvas.drawCircle(cx, cy, d * 0.135f, paint)
    }

    @JvmStatic
    fun drawSmile(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Int = 255) {
        val k = r / 11.5f
        reset(Paint.Style.STROKE, color, alpha)
        paint.strokeWidth = 1.5f * k
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawCircle(cx, cy, r, paint)
        tmpPath.reset()
        tmpPath.moveTo(cx - 5f * k, cy + 2.2f * k)
        tmpPath.quadTo(cx, cy + 7.4f * k, cx + 5f * k, cy + 2.2f * k)
        canvas.drawPath(tmpPath, paint)
        reset(Paint.Style.FILL, color, alpha)
        canvas.drawCircle(cx - 3.5f * k, cy - 2.6f * k, 1.3f * k, paint)
        canvas.drawCircle(cx + 3.5f * k, cy - 2.6f * k, 1.3f * k, paint)
    }

    /** solid folder with a thin cut line along the front; unit is how many pixels one path unit takes (about 1dp) */
    @JvmStatic
    fun drawFolder(canvas: Canvas, cx: Float, cy: Float, unit: Float, color: Int, alpha: Int = 255) {
        val half = 13f * unit
        val layer = canvas.saveLayer(cx - half, cy - half, cx + half, cy + half, null)
        canvas.translate(cx, cy)
        canvas.scale(unit, unit)
        canvas.translate(-12f, -12f)
        reset(Paint.Style.FILL, color, alpha)
        canvas.drawPath(folderBack, paint)
        clearPaint.style = Paint.Style.STROKE
        clearPaint.strokeWidth = 0.95f
        clearPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(folderGap, clearPaint)
        canvas.restoreToCount(layer)
    }

    /** the Softgram plane in the theme icon color with a gray folded wing; width is the plane's width in pixels */
    @JvmStatic
    fun drawPlane(canvas: Canvas, cx: Float, cy: Float, width: Float, alpha: Int = 255) {
        val k = width / PLANE_W
        val icon = SoftInputHelper.iconColor()
        val bg = Theme.getColor(Theme.key_chat_messagePanelBackground)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(k, k)
        canvas.translate(-PLANE_W / 2f, -PLANE_H / 2f)
        reset(Paint.Style.FILL, icon, alpha)
        canvas.drawPath(silPath, paint)
        reset(Paint.Style.FILL, 0xFF000000.toInt(), alpha)
        paint.shader = foldShader(icon, bg)
        paint.alpha = alpha
        canvas.drawPath(foldPath, paint)
        canvas.restore()
    }

    @JvmStatic
    fun drawDivider(canvas: Canvas, x: Float, cy: Float, color: Int, alpha: Float) {
        reset(Paint.Style.STROKE, ColorUtils.setAlphaComponent(color, (0x4D * alpha.coerceIn(0f, 1f)).toInt()))
        paint.strokeWidth = AndroidUtilities.dpf2(1.2f)
        paint.strokeCap = Paint.Cap.ROUND
        val half = AndroidUtilities.dpf2(13.5f)
        canvas.drawLine(x, cy - half, x, cy + half, paint)
    }
}

/** icons ignore color filters on purpose: the soft icons carry their own colors */
@Suppress("DEPRECATION")
abstract class SoftIconDrawable(private val sizeDp: Float) : Drawable() {
    protected var alphaValue = 255

    override fun setAlpha(alpha: Int) {
        alphaValue = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int = alphaValue

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = AndroidUtilities.dp(sizeDp)

    override fun getIntrinsicHeight(): Int = AndroidUtilities.dp(sizeDp)
}

class SoftPlaneDrawable : SoftIconDrawable(46f) {
    override fun draw(canvas: Canvas) {
        val b = bounds
        SoftIcons.drawPlane(canvas, b.exactCenterX(), b.exactCenterY(), b.width() * 0.5f, alphaValue)
    }
}

class SoftFolderDrawable : SoftIconDrawable(24f) {
    override fun draw(canvas: Canvas) {
        val b = bounds
        SoftIcons.drawFolder(canvas, b.exactCenterX(), b.exactCenterY(), b.width() / 24f * 1.05f, SoftInputHelper.iconColor(), alphaValue)
    }
}

/** voice or video message glyph for the recording circle; the glyph is laid out as inside a button 1.92 times the bounds */
class SoftRecordDrawable(private val video: Boolean) : SoftIconDrawable(24f) {
    override fun draw(canvas: Canvas) {
        val b = bounds
        val d = b.width() * 1.92f
        if (video) {
            SoftIcons.drawRecord(canvas, b.exactCenterX(), b.exactCenterY(), d, SoftInputHelper.iconColor(), alphaValue)
        } else {
            SoftIcons.drawVoice(canvas, b.exactCenterX(), b.exactCenterY(), d, SoftInputHelper.iconColor(), alphaValue)
        }
    }
}
