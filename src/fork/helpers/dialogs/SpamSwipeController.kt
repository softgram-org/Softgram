package desu.inugram.helpers.dialogs

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.Components.CubicBezierInterpolator
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// entiny: row swipes of the Spam folder. right = block the user and delete the chat for both sides, left = take the chat out of Spam
class SpamSwipeController private constructor(
    private val list: RecyclerView,
    private val account: Int,
    private val probe: SpamFolderHelper.SelectionProbe?,
) : RecyclerView.ItemDecoration(), RecyclerView.OnItemTouchListener {

    private val touchSlop = ViewConfiguration.get(list.context).scaledTouchSlop
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val blockIcon: Drawable? = loadIcon(R.drawable.msg_block)
    private val restoreIcon: Drawable? = loadIcon(R.drawable.msg_markread)

    private var candidate: DialogCell? = null
    private var downX = 0f
    private var downY = 0f
    private var originX = 0f
    private var nearEdge = false
    private var swiping = false
    private var tracker: VelocityTracker? = null

    private var active: DialogCell? = null
    private var activeDialogId = 0L
    private var armed = false
    private var armProgress = 0f
    private var armAnimator: ValueAnimator? = null
    private var settleAnimator: ValueAnimator? = null

    private val childListener = object : RecyclerView.OnChildAttachStateChangeListener {
        override fun onChildViewAttachedToWindow(view: View) {}

        override fun onChildViewDetachedFromWindow(view: View) {
            if (view !is DialogCell) return
            if (view === active) abandon()
            if (view.translationX != 0f) view.translationX = 0f
            view.swipeCanceled = false
        }
    }

    private fun loadIcon(res: Int): Drawable? {
        val drawable = ContextCompat.getDrawable(list.context, res)?.mutate() ?: return null
        drawable.setColorFilter(PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN))
        return drawable
    }

    private fun dp(value: Float): Int = AndroidUtilities.dp(value)

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                candidate = null
                if (settleAnimator != null) settleAnimator?.end()
                if (probe?.active() == true) return false
                val child = rv.findChildViewUnder(e.x, e.y)
                if (child is DialogCell && SpamFolderHelper.contains(account, child.dialogId)) {
                    candidate = child
                    downX = e.x
                    downY = e.y
                    nearEdge = e.x < dp(24f)
                    if (!nearEdge) rv.parent?.requestDisallowInterceptTouchEvent(true)
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val cell = candidate ?: return swiping
                if (swiping) return true
                val dx = e.x - downX
                val dy = e.y - downY
                if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                    candidate = null
                    return false
                }
                if (abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.2f) {
                    if (dx > 0 && nearEdge) {
                        candidate = null
                        return false
                    }
                    begin(cell, e)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> candidate = null
        }
        return swiping
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        val cell = active ?: return
        if (!swiping) return
        tracker?.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> move(cell, e.x - originX)
            MotionEvent.ACTION_UP -> finish(cell, false)
            MotionEvent.ACTION_CANCEL -> finish(cell, true)
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}

    private fun begin(cell: DialogCell, e: MotionEvent) {
        settleAnimator?.cancel()
        armAnimator?.cancel()
        swiping = true
        active = cell
        activeDialogId = cell.dialogId
        armed = false
        armProgress = 0f
        originX = e.x
        cell.swipeCanceled = true
        cell.isPressed = false
        tracker?.recycle()
        tracker = VelocityTracker.obtain().also { it.addMovement(e) }
        list.parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun threshold(cell: View): Float = max(dp(96f).toFloat(), cell.width * 0.3f)

    private fun move(cell: DialogCell, dx: Float) {
        val width = cell.width.toFloat()
        val limit = threshold(cell)
        val pulled = abs(dx)
        // entiny: past the threshold the row gets heavier, so the pull feels rubber-banded instead of free-running
        val eased = if (pulled <= limit) pulled else limit + (pulled - limit) * 0.5f
        val translation = (if (dx < 0f) -1f else 1f) * min(eased, width)
        cell.translationX = translation
        val nowArmed = abs(translation) >= limit
        if (nowArmed != armed) {
            armed = nowArmed
            animateArm(if (armed) 1f else 0f)
            if (armed) tick()
        }
        list.invalidate()
    }

    private fun tick() {
        try {
            list.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        } catch (_: Exception) {
        }
    }

    private fun animateArm(target: Float) {
        armAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(armProgress, target)
        animator.duration = if (target > 0f) 260 else 180
        animator.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
        animator.addUpdateListener {
            armProgress = it.animatedValue as Float
            list.invalidate()
        }
        armAnimator = animator
        animator.start()
    }

    private fun finish(cell: DialogCell, cancelled: Boolean) {
        var velocity = 0f
        tracker?.let {
            it.computeCurrentVelocity(1000)
            velocity = it.xVelocity
            it.recycle()
        }
        tracker = null
        swiping = false
        val translation = cell.translationX
        val direction = if (translation >= 0f) 1 else -1
        val flung = abs(velocity) > dp(900f) && (velocity > 0f) == (translation > 0f) && abs(translation) > dp(48f)
        if (!cancelled && (armed || flung)) {
            if (!armed) {
                armed = true
                animateArm(1f)
                tick()
            }
            complete(cell, direction)
        } else {
            snapBack(cell)
        }
    }

    private fun complete(cell: DialogCell, direction: Int) {
        val dialogId = activeDialogId
        val animator = ValueAnimator.ofFloat(cell.translationX, direction * cell.width.toFloat())
        animator.duration = 220
        animator.interpolator = CubicBezierInterpolator.EASE_IN
        animator.addUpdateListener {
            cell.translationX = it.animatedValue as Float
            list.invalidate()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (settleAnimator === animator) settleAnimator = null
                if (cancelled) return
                if (direction > 0) SpamFolderHelper.blockAndDelete(account, dialogId) else SpamFolderHelper.release(account, dialogId)
                AndroidUtilities.runOnUIThread({ clearActive(cell) }, 400)
            }
        })
        settleAnimator = animator
        animator.start()
    }

    private fun snapBack(cell: DialogCell) {
        animateArm(0f)
        val animator = ValueAnimator.ofFloat(cell.translationX, 0f)
        animator.duration = 280
        animator.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
        animator.addUpdateListener {
            cell.translationX = it.animatedValue as Float
            list.invalidate()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (settleAnimator === animator) settleAnimator = null
                clearActive(cell)
            }
        })
        settleAnimator = animator
        animator.start()
    }

    private fun clearActive(cell: DialogCell) {
        if (active !== cell) return
        active = null
        armed = false
        armProgress = 0f
        cell.swipeCanceled = false
        list.invalidate()
    }

    private fun abandon() {
        settleAnimator?.cancel()
        armAnimator?.cancel()
        settleAnimator = null
        swiping = false
        tracker?.recycle()
        tracker = null
        active = null
        armed = false
        armProgress = 0f
        list.invalidate()
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val cell = active ?: return
        val shift = cell.translationX
        if (shift == 0f || cell.parent !== parent) return
        val block = shift > 0f
        val top = cell.top + cell.translationY
        val bottom = cell.bottom + cell.translationY
        val left = if (block) cell.left.toFloat() else cell.right + shift
        val right = if (block) cell.left + shift else cell.right.toFloat()
        val revealWidth = right - left
        if (revealWidth <= 0f) return

        val progress = (abs(shift) / threshold(cell)).coerceIn(0f, 1f)
        val accent = if (block) Theme.getColor(Theme.key_dialogSwipeRemove) else Theme.getColor(Theme.key_chats_archiveBackground)
        val idle = Theme.getColor(Theme.key_windowBackgroundGray)
        // entiny: the strip warms up towards the action colour while pulling, then floods with it once armed
        val strip = ColorUtils.blendARGB(idle, accent, 0.45f * progress * progress)

        val inset = min(dp(40f).toFloat(), revealWidth / 2f)
        val cx = if (block) cell.left + inset else cell.right - inset
        val cy = (top + bottom) / 2f

        c.save()
        c.clipRect(left, top, right, bottom)
        paint.style = Paint.Style.FILL
        paint.color = strip
        c.drawRect(left, top, right, bottom, paint)
        if (armProgress > 0f) {
            val reach = hypot(max(abs(cx - left), abs(right - cx)), (bottom - top) / 2f)
            paint.color = accent
            c.drawCircle(cx, cy, reach * armProgress, paint)
        }

        val visibility = ((revealWidth - dp(12f)) / dp(36f)).coerceIn(0f, 1f)
        if (visibility > 0f) {
            val pop = 1f + 0.2f * sin(Math.PI * armProgress).toFloat()
            val scale = (0.55f + 0.45f * progress) * pop
            val half = dp(12f)
            val icon = if (block) blockIcon else restoreIcon
            if (icon != null) {
                icon.setBounds(-half, -half, half, half)
                icon.alpha = (255 * visibility).toInt()
                c.save()
                c.translate(cx, cy - dp(9f))
                c.scale(scale, scale)
                icon.draw(c)
                c.restore()
            }
            val labelVisibility = ((revealWidth - dp(56f)) / dp(24f)).coerceIn(0f, 1f)
            if (labelVisibility > 0f) {
                textPaint.alpha = (255 * labelVisibility).toInt()
                textPaint.textSize = dp(12f).toFloat()
                val label = LocaleController.getString(if (block) R.string.InuSpamBlock else R.string.InuSpamRestore)
                c.drawText(label, cx, cy + dp(18f), textPaint)
            }
        }
        c.restore()
    }

    companion object {
        @JvmStatic
        fun attach(list: RecyclerView, account: Int, probe: SpamFolderHelper.SelectionProbe?) {
            val controller = SpamSwipeController(list, account, probe)
            list.addItemDecoration(controller)
            list.addOnItemTouchListener(controller)
            list.addOnChildAttachStateChangeListener(controller.childListener)
        }
    }
}
