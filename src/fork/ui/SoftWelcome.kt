package desu.inugram.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import kotlin.math.cos
import kotlin.math.sin

// softgram: welcome screen with three spring-driven 3D shapes, laid over the stock intro
object SoftWelcome {
    private const val TAG = "softgram_welcome"

    @JvmStatic
    fun install(context: Context, container: View?, button: View?) {
        val group = container as? ViewGroup ?: return
        if (group.findViewWithTag<View>(TAG) != null) return
        val view = SoftWelcomeView(context, group, button)
        view.tag = TAG
        group.addView(view, 0, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        view.hideStock()
    }
}

class SoftWelcomeView(context: Context, private val host: ViewGroup, private val button: View?) : FrameLayout(context) {
    private class Spring(var target: Float = 0f) {
        var value = 0f
        var velocity = 0f

        fun step(dt: Float, stiffness: Float, damping: Float) {
            val accel = -stiffness * (value - target) - damping * velocity
            velocity += accel * dt
            value += velocity * dt
        }
    }

    private class Shape(
        val res: Int,
        val left: Float,
        val top: Float,
        val width: Float,
        val originX: Float,
        val originY: Float,
        val delay: Float,
        val period: Float,
        val lift: Boolean,
    ) {
        var bitmap: Bitmap? = null
        val enter = Spring(0f)
        val tap = Spring(0f)
        val box = RectF()
    }

    private val shapes = listOf(
        Shape(R.drawable.softgram_welcome_torus, 0.4088f, 0f, 0.5912f, 0f, 1f, 0.05f, 9f, true),
        Shape(R.drawable.softgram_welcome_loop, 0f, 0.1489f, 0.465f, 1f, 0.5f, 0.2f, 11f, false),
        Shape(R.drawable.softgram_welcome_capsule, 0.5621f, 0.2051f, 0.4379f, 0f, 0.5f, 0.35f, 8f, false),
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    private val liftFilter = ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                1.5f, 0f, 0f, 0f, 48f,
                0f, 1.5f, 0f, 0f, 48f,
                0f, 0f, 1.5f, 0f, 48f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
    private val calm = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    private var startTime = 0L
    private var lastTime = 0L
    private var bgColor = 0
    private var dark = false

    private val align = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT

    private val title = TextView(context).apply {
        text = LocaleController.getString(R.string.InuWelcomeTitle)
        textSize = 34f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setLineSpacing(0f, 1.04f)
        letterSpacing = -0.03f
        gravity = align
    }

    private val subtitle = TextView(context).apply {
        text = LocaleController.getString(R.string.InuWelcomeSubtitle)
        textSize = 16f
        gravity = align
    }

    init {
        setWillNotDraw(false)
        shapes.forEach { it.bitmap = decode(it.res) }
        addView(title, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        addView(subtitle, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        button?.addOnLayoutChangeListener { _, _, top, _, _, _, oldTop, _, _ ->
            if (top != oldTop) post { requestLayout() }
        }
        syncTheme()
    }

    private fun decode(res: Int): Bitmap? = try {
        BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inScaled = false })
    } catch (_: Throwable) {
        null
    }

    fun hideStock() {
        for (i in 0 until host.childCount) {
            val child = host.getChildAt(i)
            if (child === this || child === button || child is TextView) continue
            if (child.visibility != View.GONE) child.visibility = View.GONE
        }
    }

    private fun syncTheme() {
        val bg = Theme.getColor(Theme.key_windowBackgroundWhite)
        val isDark = Theme.isCurrentThemeDark()
        if (bg == bgColor && isDark == dark) return
        bgColor = bg
        dark = isDark
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2))
        (button as? TextView)?.let {
            it.background = GradientDrawable().apply {
                cornerRadius = AndroidUtilities.dp(30f).toFloat()
                setColor(if (isDark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
            }
            it.setTextColor(if (isDark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val w = r - l
        val h = b - t
        val side = AndroidUtilities.dp(26f)
        val buttonTop = button?.takeIf { it.top > 0 }?.top ?: (h - AndroidUtilities.dp(132f))
        val spec = View.MeasureSpec.makeMeasureSpec(w - 2 * side, View.MeasureSpec.EXACTLY)
        val free = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        subtitle.measure(spec, free)
        title.measure(spec, free)
        val subBottom = buttonTop - AndroidUtilities.dp(30f)
        val subTop = subBottom - subtitle.measuredHeight
        subtitle.layout(side, subTop, w - side, subBottom)
        val titleBottom = subTop - AndroidUtilities.dp(14f)
        title.layout(side, titleBottom - title.measuredHeight, w - side, titleBottom)
    }

    private fun advance(dt: Float, age: Float) {
        var left = dt
        while (left > 0f) {
            val h = minOf(left, 1f / 120f)
            for (s in shapes) {
                if (age >= s.delay) s.enter.target = 1f
                s.enter.step(h, 120f, 9.5f)
                s.tap.step(h, 260f, 8f)
            }
            left -= h
        }
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        if (startTime == 0L) {
            startTime = now
            lastTime = now
        }
        val dt = ((now - lastTime) / 1000f).coerceAtMost(0.05f)
        lastTime = now
        val age = (now - startTime) / 1000f
        syncTheme()
        hideStock()
        if (calm) {
            for (s in shapes) {
                s.enter.value = 1f
                s.enter.target = 1f
            }
        } else {
            advance(dt, age)
        }
        canvas.drawColor(bgColor)
        val w = width.toFloat()
        val h = height.toFloat()
        shapes.forEachIndexed { i, s -> drawShape(canvas, s, i, w, h, age) }
        postInvalidateOnAnimation()
    }

    private fun drawShape(canvas: Canvas, s: Shape, index: Int, w: Float, h: Float, age: Float) {
        val bmp = s.bitmap ?: return
        val sw = w * s.width
        val sh = sw * bmp.height / bmp.width
        val x0 = w * s.left
        val y0 = h * s.top
        s.box.set(x0, y0, x0 + sw, y0 + sh)
        val e = s.enter.value
        val phase = age * 6.2831855f / s.period + index * 1.7f
        val floatX = if (calm) 0f else sin(phase) * AndroidUtilities.dp(5f)
        val floatY = if (calm) 0f else cos(phase * 0.8f + index) * AndroidUtilities.dp(7f)
        val floatR = if (calm) 0f else sin(phase * 0.7f) * 3f
        val scale = (0.55f + 0.45f * e).coerceAtLeast(0.01f)
        val squash = s.tap.value
        paint.alpha = (255f * e.coerceIn(0f, 1f)).toInt()
        paint.colorFilter = if (dark && s.lift) liftFilter else null
        canvas.save()
        canvas.translate(x0 + sw / 2f + floatX, y0 + sh / 2f + floatY - (1f - e) * AndroidUtilities.dp(220f))
        canvas.rotate(floatR - (1f - e) * 35f)
        canvas.scale(scale * (1f + squash), scale * (1f - squash * 0.6f))
        canvas.translate(-sw / 2f, -sh / 2f)
        canvas.scale(1.1f, 1.1f, sw * s.originX, sh * s.originY)
        dst.set(0f, 0f, sw, sh)
        canvas.drawBitmap(bmp, null, dst, paint)
        canvas.restore()
    }

    private fun hit(s: Shape, x: Float, y: Float): Boolean {
        val bmp = s.bitmap ?: return false
        if (!s.box.contains(x, y)) return false
        val px = ((x - s.box.left) / s.box.width() * bmp.width).toInt().coerceIn(0, bmp.width - 1)
        val py = ((y - s.box.top) / s.box.height() * bmp.height).toInt().coerceIn(0, bmp.height - 1)
        return (bmp.getPixel(px, py) ushr 24) > 40
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return true
        val index = shapes.indexOfFirst { hit(it, event.x, event.y) }
        if (index < 0) return false
        shapes.forEachIndexed { i, s -> s.tap.velocity += if (i == index) 2.8f else 0.7f }
        return true
    }
}
