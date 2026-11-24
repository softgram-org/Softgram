package desu.inugram.helpers.chat

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser
import desu.inugram.InuConfig
import desu.inugram.helpers.theme.NonIslandHelper
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.ChatActivityEnterViewAnimatedIconView

/**
 * "Appearance Soft": one light pill (emoji, divider, field, paperclip) and a round action button next to it.
 * The pill and the round button reuse the iOS glass bubbles, so the Liquid Glass settings apply to them.
 * Everything drawn here (icons) is drawn in code, so no resources are needed.
 */
object SoftInputHelper {
    const val BLUE = 0xFF0A6CFF.toInt()

    @JvmStatic
    fun isSoft(): Boolean = InuConfig.SOFT_INPUT_APPEARANCE.value && !InuConfig.NON_ISLAND_CHAT_ELEMENTS.value

    /** called from the emoji / voice / video icon view; returns true when it drew the icon itself */
    @JvmStatic
    fun drawIcon(view: View, canvas: Canvas, state: ChatActivityEnterViewAnimatedIconView.State?): Boolean {
        if (!isSoft() || state == null) return false
        val cx = view.width / 2f
        val cy = view.height / 2f
        val size = minOf(view.width, view.height).toFloat()
        when (state) {
            ChatActivityEnterViewAnimatedIconView.State.VOICE ->
                SoftIcons.drawVoice(canvas, cx, cy, size - AndroidUtilities.dpf2(2f), BLUE)
            ChatActivityEnterViewAnimatedIconView.State.VIDEO ->
                SoftIcons.drawVideo(canvas, cx, cy, size - AndroidUtilities.dpf2(2f), BLUE)
            ChatActivityEnterViewAnimatedIconView.State.SMILE ->
                SoftIcons.drawSmile(canvas, cx, cy, size * 0.235f, Theme.getColor(Theme.key_glass_defaultIcon))
            else -> return false
        }
        return true
    }

    /** the Softgram plane for the send button; any other icon (schedule, done) stays as it was */
    @JvmStatic
    fun sendIcon(context: Context, resId: Int, fallback: Drawable): Drawable =
        if (isSoft() && resId == NonIslandHelper.chatSendIcon()) SoftPlaneDrawable() else fallback

    @JvmStatic
    fun attachIcon(): Drawable? = if (isSoft()) SoftClipDrawable() else null

    /** the thin vertical line between the emoji button and the text; the canvas is in field container coordinates */
    @JvmStatic
    fun drawDivider(canvas: Canvas, emoji: View) {
        val x = emoji.right + AndroidUtilities.dpf2(4f)
        val cy = (emoji.top + emoji.bottom) / 2f
        SoftIcons.drawDivider(canvas, x, cy, Theme.getColor(Theme.key_glass_defaultIcon), emoji.alpha)
    }
}

object SoftIcons {
    private const val SIL_PATH = "M237.8,508.3 C231.2,507.3 224.3,503.6 219.1,498.4 C216.0,495.2 215.2,494.2 209.6,485.9 C207.3,482.4 204.8,478.8 204.1,477.9 C203.4,476.9 201.7,474.4 200.3,472.4 C197.4,468.1 195.6,465.6 192.7,461.6 C191.7,460.1 189.1,456.5 187.0,453.5 C180.9,445.0 180.2,444.0 178.9,442.2 C177.7,440.8 176.6,439.2 172.4,433.4 C171.6,432.2 170.2,430.5 169.5,429.4 C168.7,428.4 166.9,426.1 165.5,424.1 C162.5,419.9 157.9,413.9 151.0,405.0 C149.4,402.9 147.2,400.1 146.2,398.8 C145.2,397.5 143.5,395.3 142.4,394.0 C141.3,392.7 139.8,390.8 139.1,389.8 C138.3,388.8 136.6,386.6 135.2,384.9 C133.8,383.2 132.1,381.3 131.6,380.5 C131.0,379.7 129.6,378.0 128.3,376.6 C127.1,375.2 125.1,372.9 123.9,371.4 C122.6,369.9 120.3,367.0 118.6,365.1 C100.6,344.0 96.1,334.9 98.3,323.5 C100.9,310.4 111.3,302.1 137.6,292.0 C143.0,289.9 163.2,282.8 176.9,278.1 C180.2,277.0 186.2,274.9 190.2,273.5 C194.3,272.2 200.8,269.9 204.6,268.6 C208.5,267.3 212.3,266.0 213.1,265.8 C213.9,265.5 217.9,264.2 222.0,262.8 C226.1,261.3 230.3,259.9 231.5,259.5 C232.7,259.1 236.8,257.7 240.6,256.4 C256.9,250.9 280.5,242.9 281.9,242.5 C282.7,242.2 287.9,240.5 293.4,238.6 C298.9,236.7 304.1,235.0 304.9,234.7 C306.7,234.2 310.9,232.8 317.1,230.6 C319.7,229.7 329.9,226.3 339.8,223.0 C349.6,219.7 360.4,216.0 363.9,214.9 C367.3,213.7 373.8,211.5 378.2,210.0 C382.7,208.5 388.5,206.5 391.1,205.6 C393.7,204.7 397.6,203.4 399.8,202.7 C401.9,202.0 405.3,200.9 407.4,200.1 C409.5,199.4 413.1,198.1 415.4,197.3 C424.9,194.1 431.5,191.9 437.8,189.7 C441.4,188.5 445.3,187.1 446.5,186.7 C447.7,186.4 450.8,185.3 453.4,184.4 C456.0,183.5 458.8,182.5 459.6,182.2 C460.4,181.9 462.8,181.1 464.8,180.5 C473.1,177.6 482.4,174.5 485.6,173.4 C487.6,172.7 489.8,171.9 490.6,171.6 C491.4,171.3 493.9,170.5 496.1,169.7 C498.3,169.0 502.0,167.8 504.2,167.0 C506.5,166.2 510.1,165.0 512.1,164.4 C514.2,163.7 516.7,162.9 517.7,162.6 C518.7,162.3 520.2,161.8 521.2,161.6 C522.1,161.4 523.2,161.2 523.6,161.0 C527.9,159.4 538.9,159.7 544.0,161.5 C563.4,168.6 569.0,186.4 560.0,212.6 C559.4,214.6 557.6,219.8 556.1,224.4 C554.7,228.9 552.7,234.8 551.9,237.4 C551.0,240.0 548.8,246.8 546.9,252.6 C545.0,258.4 543.1,264.2 542.7,265.6 C541.5,269.2 536.5,284.3 534.1,291.9 C532.9,295.4 531.3,300.6 530.4,303.2 C529.5,305.9 528.5,309.0 528.2,310.1 C527.8,311.2 526.0,316.8 524.2,322.5 C522.3,328.2 520.5,333.9 520.1,335.1 C519.5,337.2 517.5,343.5 512.4,359.4 C511.2,362.9 509.9,367.2 509.4,368.9 C508.9,370.5 508.2,372.7 507.9,373.6 C507.2,375.5 503.5,387.3 501.0,395.4 C500.2,398.1 498.7,402.7 497.8,405.5 C495.8,412.0 495.0,414.6 493.0,421.0 C492.1,423.8 490.9,427.9 490.1,430.1 C489.4,432.3 488.3,435.7 487.8,437.6 C487.2,439.6 486.3,442.2 485.9,443.6 C485.4,445.0 484.5,447.9 483.9,450.1 C483.2,452.3 482.2,455.6 481.6,457.4 C480.1,462.1 477.6,469.8 476.9,472.4 C470.1,495.3 463.5,504.6 451.7,507.7 C445.8,509.2 437.1,507.7 431.7,504.2 C431.0,503.7 429.6,502.8 428.6,502.2 C423.9,499.1 415.2,490.7 406.5,480.5 C403.9,477.4 399.7,472.7 397.4,470.3 C396.3,469.1 393.3,465.7 390.8,462.9 C388.2,460.0 384.6,455.9 382.6,453.8 C380.7,451.8 376.8,447.5 374.0,444.5 C362.6,432.2 358.7,428.0 348.4,417.2 C345.5,414.2 342.1,410.6 340.8,409.2 C337.2,405.5 336.2,405.1 334.9,406.8 C333.4,408.8 329.5,414.1 327.8,416.5 C326.8,417.9 322.8,423.3 318.9,428.5 C315.0,433.6 311.3,438.7 310.5,439.7 C309.7,440.8 308.5,442.4 307.8,443.4 C307.0,444.3 305.5,446.2 304.5,447.6 C300.6,452.9 295.8,459.2 293.6,462.0 C292.4,463.6 290.3,466.2 289.1,467.9 C287.8,469.5 285.9,472.1 284.8,473.5 C283.6,474.9 281.7,477.5 280.5,479.1 C268.5,494.8 261.7,501.5 253.6,505.3 C248.8,507.5 241.8,508.8 237.8,508.3 Z"
    private const val FOLD_PATH = "M237.8,508.3 C231.2,507.3 224.3,503.6 219.1,498.4 C216.0,495.2 215.2,494.2 209.6,485.9 C207.3,482.4 204.8,478.8 204.1,477.9 C203.4,476.9 201.7,474.4 200.3,472.4 C197.4,468.1 195.6,465.6 192.7,461.6 C191.7,460.1 189.1,456.5 187.0,453.5 C180.9,445.0 180.2,444.0 178.9,442.2 C177.7,440.8 176.6,439.2 172.4,433.4 C171.6,432.2 170.2,430.5 169.5,429.4 C168.7,428.4 166.9,426.1 165.5,424.1 C162.5,419.9 157.9,413.9 151.0,405.0 C149.4,402.9 147.2,400.1 146.2,398.8 C145.2,397.5 143.5,395.3 142.4,394.0 C141.3,392.7 139.8,390.8 139.1,389.8 C138.3,388.8 136.6,386.6 135.2,384.9 C133.8,383.2 132.1,381.3 131.6,380.5 C131.0,379.7 129.6,378.0 128.3,376.6 C127.1,375.2 125.1,372.9 123.9,371.4 C122.6,369.9 120.3,367.0 118.6,365.1 C105.8,350.1 102.2,344.8 99.3,336.4 C96.0,327.1 98.6,316.8 106.2,309.5 C109.2,306.5 110.0,306.2 115.4,305.9 C119.1,305.6 120.2,305.5 121.9,304.9 C123.3,304.4 124.8,304.1 127.4,303.9 C129.3,303.7 131.3,303.4 131.7,303.3 C133.2,302.8 148.5,302.9 150.2,303.4 C151.0,303.5 153.3,303.8 155.2,303.9 C157.2,304.0 159.5,304.2 160.2,304.4 C161.0,304.6 162.3,304.8 163.1,304.9 C164.7,305.0 166.5,305.4 173.8,307.0 C176.2,307.5 179.2,308.2 180.6,308.5 C182.0,308.8 184.6,309.6 186.5,310.2 C192.7,312.2 193.6,312.5 195.6,313.0 C197.4,313.4 206.5,316.8 213.1,319.4 C214.5,320.0 216.5,320.8 217.6,321.2 C220.0,322.2 221.4,322.7 224.6,324.2 C231.4,327.2 233.7,328.3 235.0,329.0 C236.2,329.7 239.5,331.3 242.5,332.7 C243.1,333.0 244.4,333.7 245.4,334.3 C249.1,336.5 250.2,337.1 252.6,338.5 C261.1,343.1 262.0,343.6 265.1,345.6 C266.5,346.5 269.4,348.3 271.5,349.6 C273.6,351.0 276.0,352.5 276.8,353.1 C278.2,354.2 280.1,355.5 285.6,359.2 C287.5,360.4 289.5,361.8 290.0,362.2 C290.5,362.6 291.8,363.6 292.9,364.4 C294.1,365.1 295.9,366.4 297.0,367.1 C298.1,367.9 300.1,369.4 301.3,370.6 C302.6,371.8 304.2,373.2 304.9,373.7 C305.6,374.2 307.2,375.5 308.4,376.7 C309.6,377.8 311.2,379.1 311.9,379.7 C312.6,380.2 314.0,381.3 315.0,382.2 C316.0,383.2 317.4,384.3 318.0,384.8 C318.6,385.2 320.9,387.3 323.1,389.4 C327.3,393.2 332.6,398.1 334.0,399.3 C334.7,399.9 334.7,399.9 334.8,403.2 C334.8,407.1 334.8,406.9 331.5,411.4 C330.2,413.2 328.4,415.6 327.5,416.9 C326.6,418.1 322.8,423.3 318.9,428.5 C315.0,433.6 311.3,438.7 310.5,439.7 C309.7,440.8 308.5,442.4 307.8,443.4 C307.0,444.3 305.5,446.2 304.5,447.6 C300.6,452.9 295.8,459.2 293.6,462.0 C292.4,463.6 290.3,466.2 289.1,467.9 C287.8,469.5 285.9,472.1 284.8,473.5 C283.6,474.9 281.7,477.5 280.5,479.1 C268.5,494.8 261.7,501.5 253.6,505.3 C248.8,507.5 241.8,508.8 237.8,508.3 Z"

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val tmpPath = Path()

    private val silPath: Path by lazy { PathParser.createPathFromPathData(SIL_PATH) }
    private val foldPath: Path by lazy { PathParser.createPathFromPathData(FOLD_PATH) }
    private val clipPath: Path by lazy {
        Path().apply {
            moveTo(-5.2f, 1f)
            lineTo(-5.2f, -4.8f)
            arcTo(RectF(-5.2f, -10f, 5.2f, 0.4f), 180f, 180f)
            lineTo(5.2f, 5.6f)
            arcTo(RectF(-1.8f, 2.1f, 5.2f, 9.1f), 0f, 180f)
            lineTo(-1.8f, -2.2f)
            arcTo(RectF(-1.8f, -4f, 1.8f, -0.4f), 180f, 180f)
            lineTo(1.8f, 3f)
        }
    }

    // the plane is traced in a 640 x 640 space: x 98..564, y 160..508
    private const val PLANE_W = 466f
    private const val PLANE_CX = 331f
    private const val PLANE_CY = 334f

    private val bodyShader: Shader by lazy {
        LinearGradient(330f, 160f, 330f, 508f, 0xFF1A82FF.toInt(), 0xFF0A62F2.toInt(), Shader.TileMode.CLAMP)
    }
    private val foldShader: Shader by lazy {
        LinearGradient(299f, 379f, 226f, 495f, 0xFF0A52CF.toInt(), 0xFF7DB4FF.toInt(), Shader.TileMode.CLAMP)
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

    /** blue rounded square with a white ring; d is the diameter of the round button it sits in */
    @JvmStatic
    fun drawVideo(canvas: Canvas, cx: Float, cy: Float, d: Float, color: Int, alpha: Int = 255) {
        val s = d * 0.44f
        rect.set(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f)
        reset(Paint.Style.FILL, color, alpha)
        canvas.drawRoundRect(rect, d * 0.108f, d * 0.108f, paint)
        reset(Paint.Style.STROKE, 0xFFFFFFFF.toInt(), alpha)
        paint.strokeWidth = d * 0.038f
        canvas.drawCircle(cx, cy + d * 0.004f, d * 0.096f, paint)
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

    /** paperclip tilted 45 degrees; unit is how many pixels one path unit takes (about 1dp) */
    @JvmStatic
    fun drawClip(canvas: Canvas, cx: Float, cy: Float, unit: Float, color: Int, alpha: Int = 255) {
        reset(Paint.Style.STROKE, color, alpha)
        paint.strokeWidth = 1.35f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(45f)
        canvas.scale(unit, unit)
        canvas.drawPath(clipPath, paint)
        canvas.restore()
    }

    /** the Softgram plane: blue body and a lighter folded wing; width is the plane's width in pixels */
    @JvmStatic
    fun drawPlane(canvas: Canvas, cx: Float, cy: Float, width: Float, alpha: Int = 255) {
        val k = width / PLANE_W
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(k, k)
        canvas.translate(-PLANE_CX, -PLANE_CY)
        reset(Paint.Style.FILL, 0xFF0A6CFF.toInt(), alpha)
        paint.shader = bodyShader
        canvas.drawPath(silPath, paint)
        reset(Paint.Style.FILL, 0xFF0A6CFF.toInt(), alpha)
        paint.shader = foldShader
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
class SoftPlaneDrawable : Drawable() {
    private var alphaValue = 255

    override fun draw(canvas: Canvas) {
        val b = bounds
        SoftIcons.drawPlane(canvas, b.exactCenterX(), b.exactCenterY(), b.width() * 0.47f, alphaValue)
    }

    override fun setAlpha(alpha: Int) {
        alphaValue = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = AndroidUtilities.dp(46f)

    override fun getIntrinsicHeight(): Int = AndroidUtilities.dp(46f)
}

@Suppress("DEPRECATION")
class SoftClipDrawable : Drawable() {
    private var alphaValue = 255

    override fun draw(canvas: Canvas) {
        val b = bounds
        val color = ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_glass_defaultIcon), 0xB4)
        SoftIcons.drawClip(canvas, b.exactCenterX(), b.exactCenterY(), b.width() / 24f * 1.05f, color, alphaValue)
    }

    override fun setAlpha(alpha: Int) {
        alphaValue = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = AndroidUtilities.dp(24f)

    override fun getIntrinsicHeight(): Int = AndroidUtilities.dp(24f)
}
