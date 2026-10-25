package uz.yangiuzbkey.keyboard.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import uz.yangiuzbkey.keyboard.theme.KeyStyle
import uz.yangiuzbkey.keyboard.theme.ResolvedStyle
import java.util.Locale
import kotlin.math.abs

/**
 * Custom-drawn key grid with multi-touch, key previews, long-press popups,
 * space-bar cursor control and swipe-to-delete-words.
 */
class KeyboardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    enum class ShiftState { OFF, ON, LOCKED }

    interface Listener {
        fun onKey(key: Key, text: String)
        fun onFunctional(code: Int)
        fun onPopupAction(token: String)
        fun onCursorMove(steps: Int)
        /** Space bar swiped sideways while language swipe is on: -1 left, +1 right. */
        fun onSpaceSwipe(dir: Int)
        fun onDeleteSwipe(words: Int)
        fun onDeleteSwipeEnd(apply: Boolean)
        fun onPress(key: Key)
        /** Return true when the long press was handled (e.g. space → IME picker). */
        fun onLongPressFunctional(code: Int): Boolean
    }

    var listener: Listener? = null
    var overlay: OverlayView? = null

    private val d = resources.displayMetrics.density
    private var layout: KeyboardLayout? = null
    private var style: ResolvedStyle? = null

    var locale: Locale = Locale.getDefault()
    var shift = ShiftState.OFF
        set(v) { if (field != v) { field = v; invalidate() } }
    private var enterIcon: Icon? = Icon.ENTER
    private var enterLabel: String? = null

    // Behaviour settings, set by the service from Prefs.
    var heightPercent = 100
        set(v) { field = v; requestLayout() }
    var longPressMs = 300L
    var keyPopup = true
    var spaceCursor = true
    var spaceLang = false
    var deleteSwipe = true
    /** Keeps the keyboard from changing height between alpha / symbols pages. */
    var minContentHeight = 0
        set(v) { if (field != v) { field = v; requestLayout() } }

    private val padTop = 4 * d
    private val padBottom = 4 * d
    private val padSide = 2 * d

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val tmp = RectF()

    fun setKeyboard(l: KeyboardLayout) {
        cancelAll()
        layout = l
        requestLayout()
        computeRects()
        invalidate()
    }

    val keyboard: KeyboardLayout? get() = layout

    fun setStyle(s: ResolvedStyle) {
        style = s
        overlay?.style = s
        invalidate()
    }

    fun setEnter(icon: Icon?, label: String?) {
        enterIcon = icon; enterLabel = label; invalidate()
    }

    // ---------------------------------------------------------------- sizing

    private fun baseKeyHeight(): Float {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return (if (land) 40f else 54f) * d * heightPercent / 100f
    }

    private fun rowHeight(i: Int, l: KeyboardLayout): Float =
        if (i == l.numberRowIndex) baseKeyHeight() * 0.82f else baseKeyHeight()

    fun naturalHeight(l: KeyboardLayout): Int =
        (l.rows.indices.sumOf { rowHeight(it, l).toDouble() } + padTop + padBottom).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val l = layout
        val h = if (l == null) minContentHeight else maxOf(naturalHeight(l), minContentHeight)
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        computeRects()
    }

    private fun computeRects() {
        val l = layout ?: return
        if (width == 0 || height == 0) return
        val sum = l.rows.indices.sumOf { rowHeight(it, l).toDouble() }.toFloat()
        val avail = height - padTop - padBottom
        val scale = if (sum > 0f && avail > sum) avail / sum else 1f
        val unitW = (width - padSide * 2) / l.units
        var y = padTop
        l.rows.forEachIndexed { i, row ->
            val h = rowHeight(i, l) * scale
            val rowW = row.sumOf { it.weight.toDouble() }.toFloat()
            var x = padSide + maxOf(0f, (l.units - rowW) / 2f) * unitW
            for (k in row) {
                val kw = k.weight * unitW
                k.rect.set(x, y, x + kw, y + h)
                x += kw
            }
            y += h
        }
    }

    private fun findKey(x: Float, y: Float): Key? {
        val l = layout ?: return null
        if (l.rows.isEmpty()) return null
        var row = l.rows.last()
        for (r in l.rows) {
            if (r.isNotEmpty() && y < r[0].rect.bottom) { row = r; break }
        }
        var best: Key? = null
        var bestD = Float.MAX_VALUE
        for (k in row) {
            if (x >= k.rect.left && x < k.rect.right) return k
            val dist = minOf(abs(x - k.rect.left), abs(x - k.rect.right))
            if (dist < bestD) { bestD = dist; best = k }
        }
        return best
    }

    // ---------------------------------------------------------------- drawing

    private fun withAlpha(c: Int, a: Float): Int =
        Color.argb((Color.alpha(c) * a).toInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    private fun darken(c: Int): Int {
        val f = 0.82f
        return Color.argb(Color.alpha(c), (Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())
    }

    private fun displayLabel(k: Key): String =
        if (k.isChar && k.code != Key.SPACE && shift != ShiftState.OFF) Key.upper(k.label, locale) else k.label

    private fun outText(k: Key): String =
        if (shift != ShiftState.OFF && k.code != Key.SPACE) Key.upper(k.text, locale) else k.text

    override fun onDraw(canvas: Canvas) {
        val l = layout ?: return
        val st = style ?: return
        val t = st.theme
        val photo = st.background != null
        val a = if (photo) 0.86f else 1f
        val r = st.radiusDp * d
        val gh = st.gapDp * d / 2f
        val gv = st.gapDp * d / 2f + 2.5f * d
        val tf = if (st.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        labelPaint.typeface = tf
        val baseH = baseKeyHeight()

        for (row in l.rows) for (k in row) {
            tmp.set(k.rect.left + gh, k.rect.top + gv, k.rect.right - gh, k.rect.bottom - gv)
            if (tmp.width() <= 0f || tmp.height() <= 0f) continue
            val pressed = k in pressed
            val enter = k.code == Key.ENTER
            val func = k.functional
            val fill: Int? = when {
                enter -> if (pressed) darken(t.accent) else t.accent
                pressed -> t.keyPressed
                st.keyStyle == KeyStyle.FLAT -> null
                st.keyStyle == KeyStyle.OUTLINE -> withAlpha(if (func) t.keyFuncBg else t.keyBg, 0.35f)
                func -> t.keyFuncBg
                else -> t.keyBg
            }
            if (fill != null) {
                if (st.keyStyle == KeyStyle.FILLED && !pressed) {
                    keyPaint.color = withAlpha(t.keyShadow, a)
                    canvas.drawRoundRect(tmp.left, tmp.top + 1.2f * d, tmp.right, tmp.bottom + 1.2f * d, r, r, keyPaint)
                }
                keyPaint.color = withAlpha(fill, a)
                canvas.drawRoundRect(tmp, r, r, keyPaint)
            }
            if (st.keyStyle == KeyStyle.OUTLINE && !enter) {
                strokePaint.strokeWidth = 1.2f * d
                strokePaint.color = withAlpha(t.keyText, 0.4f)
                canvas.drawRoundRect(tmp, r, r, strokePaint)
            } else if (st.border && fill != null) {
                strokePaint.strokeWidth = 1f * d
                strokePaint.color = t.border
                canvas.drawRoundRect(tmp, r, r, strokePaint)
            }
            drawContent(canvas, k, t.let { it }, st, baseH)
        }
    }

    private fun drawContent(canvas: Canvas, k: Key, t: uz.yangiuzbkey.keyboard.theme.KeyboardTheme, st: ResolvedStyle, baseH: Float) {
        val cx = tmp.centerX(); val cy = tmp.centerY()
        val kh = minOf(tmp.height(), baseH)
        val iconSize = kh * 0.46f
        val textColor = if (k.functional) t.keyFuncText else t.keyText
        when (k.code) {
            Key.SHIFT -> {
                val icon = when (shift) { ShiftState.OFF -> Icon.SHIFT; ShiftState.ON -> Icon.SHIFT_ON; ShiftState.LOCKED -> Icon.CAPS }
                Icons.draw(canvas, icon, cx, cy, iconSize, if (shift == ShiftState.OFF) textColor else t.accent)
            }
            Key.DELETE -> Icons.draw(canvas, Icon.DELETE, cx, cy, iconSize, textColor)
            Key.LANG -> Icons.draw(canvas, Icon.GLOBE, cx, cy, iconSize * 0.92f, textColor)
            Key.EMOJI -> Icons.draw(canvas, Icon.EMOJI, cx, cy, iconSize * 0.92f, textColor)
            Key.SETTINGS -> Icons.draw(canvas, Icon.GEAR, cx, cy, iconSize, textColor)
            Key.ENTER -> {
                val lbl = enterLabel
                val icon = enterIcon
                if (icon != null || lbl.isNullOrEmpty()) {
                    Icons.draw(canvas, icon ?: Icon.ENTER, cx, cy, iconSize, t.accentText)
                } else {
                    labelPaint.color = t.accentText
                    labelPaint.textSize = kh * 0.27f * st.fontScale
                    fit(lbl, tmp.width() * 0.86f)
                    canvas.drawText(lbl, cx, cy - (labelPaint.descent() + labelPaint.ascent()) / 2, labelPaint)
                }
            }
            Key.SPACE -> {
                labelPaint.color = t.hint
                labelPaint.textSize = kh * 0.26f * st.fontScale
                val s = k.label
                fit(s, tmp.width() * 0.8f)
                canvas.drawText(s, cx, cy - (labelPaint.descent() + labelPaint.ascent()) / 2, labelPaint)
            }
            else -> {
                val s = displayLabel(k)
                if (s.isEmpty()) return
                labelPaint.color = textColor
                val cps = s.codePointCount(0, s.length)
                val f = when {
                    k.functional -> 0.28f
                    cps <= 1 -> 0.43f
                    cps == 2 -> 0.36f
                    else -> 0.28f
                }
                labelPaint.textSize = kh * f * st.fontScale
                fit(s, tmp.width() * 0.82f)
                canvas.drawText(s, cx, cy - (labelPaint.descent() + labelPaint.ascent()) / 2, labelPaint)
                val h = k.hint
                if (h != null && !k.functional) {
                    hintPaint.color = t.hint
                    hintPaint.typeface = labelPaint.typeface
                    hintPaint.textSize = kh * 0.21f * st.fontScale
                    canvas.drawText(h, tmp.right - 4 * d, tmp.top + hintPaint.textSize + 2 * d, hintPaint)
                }
            }
        }
    }

    private fun fit(s: String, maxW: Float) {
        val w = labelPaint.measureText(s)
        if (w > maxW && w > 0f) labelPaint.textSize *= maxW / w
    }

    // ---------------------------------------------------------------- touch

    private enum class PMode { NORMAL, POPUP, SPACE, DELSWIPE, DONE }

    private class Ptr(val id: Int, var key: Key, var downX: Float, val downY: Float) {
        var mode = PMode.NORMAL
        var lastStep = 0
        var words = 0
        var repeated = false
    }

    private val ptrs = ArrayList<Ptr>()
    private val pressed = HashSet<Key>()
    private val handler = Handler(Looper.getMainLooper())

    private var lpPtr: Ptr? = null
    private val longPressRunnable = Runnable { lpPtr?.let { onLongPress(it) } }
    private var repPtr: Ptr? = null
    private val repeatRunnable = object : Runnable {
        override fun run() {
            val p = repPtr ?: return
            if (p.mode != PMode.NORMAL) return
            p.repeated = true
            listener?.onFunctional(Key.DELETE)
            handler.postDelayed(this, 50)
        }
    }

    private val locSelf = IntArray(2)
    private val locOverlay = IntArray(2)

    private fun overlayOffset(): Pair<Float, Float> {
        val o = overlay ?: return 0f to 0f
        getLocationInWindow(locSelf)
        o.getLocationInWindow(locOverlay)
        return (locSelf[0] - locOverlay[0]).toFloat() to (locSelf[1] - locOverlay[1]).toFloat()
    }

    private fun overlayRect(k: Key): RectF {
        val (ox, oy) = overlayOffset()
        val st = style
        val gh = (st?.gapDp ?: 0f) * d / 2f
        val gv = (st?.gapDp ?: 0f) * d / 2f + 2.5f * d
        return RectF(k.rect.left + gh + ox, k.rect.top + gv + oy, k.rect.right - gh + ox, k.rect.bottom - gv + oy)
    }

    private fun showPreview(k: Key) {
        if (!keyPopup || !k.isChar || k.code == Key.SPACE) { overlay?.hidePreview(); return }
        overlay?.showPreview(overlayRect(k), displayLabel(k))
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (layout == null) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                down(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val p = ptrs.firstOrNull { it.id == e.getPointerId(i) } ?: continue
                    move(p, e.getX(i), e.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                ptrs.firstOrNull { it.id == id }?.let { up(it, true) }
            }
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun down(id: Int, x: Float, y: Float) {
        // Fast typing: a new finger commits the keys still held by other fingers.
        for (p in ptrs.toList()) if (p.mode == PMode.NORMAL && p.key.isChar) up(p, true)
        val k = findKey(x, y) ?: return
        val p = Ptr(id, k, x, y)
        ptrs += p
        pressed += k
        listener?.onPress(k)
        when (k.code) {
            Key.SHIFT -> {
                listener?.onFunctional(Key.SHIFT)
                p.mode = PMode.DONE
            }
            Key.DELETE -> {
                if (!deleteSwipe) { listener?.onFunctional(Key.DELETE); p.repeated = true }
                repPtr = p
                handler.removeCallbacks(repeatRunnable)
                handler.postDelayed(repeatRunnable, 400)
            }
            else -> {
                showPreview(k)
                scheduleLongPress(p)
            }
        }
        invalidate()
    }

    private fun scheduleLongPress(p: Ptr) {
        handler.removeCallbacks(longPressRunnable)
        lpPtr = p
        handler.postDelayed(longPressRunnable, longPressMs)
    }

    private fun cancelLongPress(p: Ptr? = null) {
        if (p == null || lpPtr === p) {
            handler.removeCallbacks(longPressRunnable)
            lpPtr = null
        }
    }

    private fun onLongPress(p: Ptr) {
        lpPtr = null
        if (p.mode != PMode.NORMAL || p !in ptrs) return
        val k = p.key
        if (k.isChar && k.code != Key.SPACE && k.popups.isNotEmpty()) {
            val o = overlay ?: return
            val upper = shift != ShiftState.OFF
            val items = if (upper) k.popups.map { Key.upper(it, locale) } else k.popups
            val (ox, _) = overlayOffset()
            o.showPopup(overlayRect(k), items, items, p.downX + ox)
            p.mode = PMode.POPUP
            listener?.onPress(k)
        } else if (listener?.onLongPressFunctional(k.code) == true) {
            overlay?.hidePreview()
            p.mode = PMode.DONE
            pressed.remove(k)
            invalidate()
        }
    }

    private fun move(p: Ptr, x: Float, y: Float) {
        when (p.mode) {
            PMode.POPUP -> {
                val (ox, oy) = overlayOffset()
                overlay?.update(x + ox, y + oy)
            }
            PMode.SPACE -> {
                val steps = ((x - p.downX) / (11 * d)).toInt()
                if (steps != p.lastStep) {
                    listener?.onCursorMove(steps - p.lastStep)
                    p.lastStep = steps
                }
            }
            PMode.DELSWIPE -> {
                val w = ((p.downX - x) / (28 * d) + 0.6f).toInt().coerceAtLeast(0)
                if (w != p.words) { p.words = w; listener?.onDeleteSwipe(w) }
            }
            PMode.NORMAL -> {
                val k = p.key
                when {
                    k.code == Key.SPACE -> if (spaceLang) {
                        if (abs(x - p.downX) > 36 * d) {
                            cancelLongPress(p)
                            p.mode = PMode.DONE
                            pressed.remove(k)
                            invalidate()
                            listener?.onSpaceSwipe(if (x > p.downX) 1 else -1)
                        }
                    } else if (spaceCursor && abs(x - p.downX) > 16 * d) {
                        cancelLongPress(p)
                        p.mode = PMode.SPACE
                        p.downX = x
                        p.lastStep = 0
                    }
                    k.code == Key.DELETE -> if (deleteSwipe && !p.repeated && p.downX - x > 18 * d) {
                        handler.removeCallbacks(repeatRunnable)
                        repPtr = null
                        p.mode = PMode.DELSWIPE
                        p.downX += 18 * d
                        p.words = 0
                        move(p, x, y)
                    }
                    k.isChar -> {
                        val nk = findKey(x, y)
                        if (nk != null && nk !== k && nk.isChar && nk.code != Key.SPACE) {
                            pressed.remove(k)
                            pressed += nk
                            p.key = nk
                            showPreview(nk)
                            scheduleLongPress(p)
                            invalidate()
                        }
                    }
                }
            }
            PMode.DONE -> {}
        }
    }

    private fun up(p: Ptr, commit: Boolean) {
        ptrs.remove(p)
        cancelLongPress(p)
        if (repPtr === p) { handler.removeCallbacks(repeatRunnable); repPtr = null }
        val k = p.key
        if (ptrs.none { it.key === k }) pressed.remove(k)
        overlay?.hidePreview()
        when (p.mode) {
            PMode.NORMAL -> if (commit) when {
                k.code == Key.DELETE -> if (!p.repeated) listener?.onFunctional(Key.DELETE)
                k.isChar -> listener?.onKey(k, outText(k))
                else -> listener?.onFunctional(k.code)
            }
            PMode.POPUP -> {
                val s = overlay?.selectedItem()
                overlay?.hidePopup()
                if (commit && s != null) {
                    if (s.startsWith("{") && s.endsWith("}")) listener?.onPopupAction(s) else listener?.onKey(k, s)
                }
            }
            PMode.DELSWIPE -> listener?.onDeleteSwipeEnd(commit && p.words > 0)
            PMode.SPACE, PMode.DONE -> {}
        }
        invalidate()
    }

    fun cancelAll() {
        for (p in ptrs.toList()) up(p, false)
        ptrs.clear()
        pressed.clear()
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(repeatRunnable)
        lpPtr = null; repPtr = null
        overlay?.hideAll()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAll()
    }
}
