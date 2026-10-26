package uz.yangiuzbkey.keyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import uz.yangiuzbkey.keyboard.keyboard.Icon
import uz.yangiuzbkey.keyboard.keyboard.Icons
import uz.yangiuzbkey.keyboard.theme.ResolvedStyle
import uz.yangiuzbkey.keyboard.util.Transliterator

/**
 * The bar above the keys. It shows word suggestions while typing and a
 * toolbar (settings, emoji, clipboard, number row, theme, script converter,
 * hide) when there is nothing to suggest.
 */
class SuggestionStrip(context: Context) : View(context) {

    enum class Action(val icon: Icon, val label: String) {
        SETTINGS(Icon.GEAR, "Sozlamalar"), EMOJI(Icon.EMOJI, "Emoji"), CLIPBOARD(Icon.CLIPBOARD, "Bufer"),
        NUMBERS(Icon.NUMBERS, "Raqamlar qatori"), THEME(Icon.PALETTE, "Mavzu"), CONVERT(Icon.CONVERT, "Alifbo"),
        HIDE(Icon.CHEVRON_DOWN, "Yashirish"), UNDO(Icon.UNDO, "Bekor qilish"), REDO(Icon.REDO, "Qaytarish"),
        SELECT_ALL(Icon.SELECT_ALL, "Hammasini belgilash"), COPY(Icon.COPY, "Nusxa olish"), CUT(Icon.CUT, "Kesish"),
        PASTE(Icon.PASTE, "Qo'yish"), CURSOR_LEFT(Icon.BACK, "Kursor chapga"), CURSOR_RIGHT(Icon.NEXT, "Kursor o'ngga"),
        LANGUAGE(Icon.GLOBE, "Til"), KEYBOARD_PICK(Icon.KEYBOARD, "Klaviatura tanlash"),
        MIC(Icon.MIC, "Ovozli yozish"), EDIT_PANEL(Icon.EDIT, "Matn tahriri"), ONE_HAND(Icon.ONE_HAND, "Bir qo'l rejimi"),
        INCOGNITO(Icon.INCOGNITO, "Inkognito"),
        BACK(Icon.BACK, ""),
    }

    interface Listener {
        fun onSuggestion(word: String)
        fun onToolbar(action: Action)
        fun onConvert(target: String)
        /** Long-press on the toolbar: open the edit mode. */
        fun onToolbarEdit()
    }

    companion object {
        /** Every action a user may place on the toolbar, in pool order. */
        val EDITABLE: List<Action> = Action.values().filter { it != Action.BACK }

        fun parse(names: List<String>): List<Action> =
            names.mapNotNull { n -> runCatching { Action.valueOf(n) }.getOrNull() }
                .filter { it != Action.BACK }.distinct()
    }

    private enum class Mode { TOOLBAR, SUGGEST, CONVERT }

    private class Cell(val rect: RectF, val icon: Icon? = null, val text: String? = null,
                       val bold: Boolean = false, val active: Boolean = false, val onTap: () -> Unit)

    var listener: Listener? = null
    var style: ResolvedStyle? = null
        set(v) { field = v; invalidate() }
    var numberRowOn = false
        set(v) { if (field != v) { field = v; rebuild() } }
    var toolbarEnabled = true
        set(v) { if (field != v) { field = v; rebuild() } }
    /** Toolbar buttons drawn highlighted (incognito, one-hand on). */
    var activeActions: Set<Action> = emptySet()
        set(v) { if (field != v) { field = v; rebuild() } }
    /** Toolbar buttons in display order. */
    var items: List<Action> = parse(uz.yangiuzbkey.keyboard.util.Prefs.DEFAULT_TOOLBAR.split(','))
        set(v) { if (field != v) { field = v; scroll = 0f; rebuild() } }

    private val d = resources.displayMetrics.density
    private var mode = Mode.TOOLBAR
    private var words: List<String> = emptyList()
    private var autocorrect: String? = null
    private var literal: String? = null
    private val cells = ArrayList<Cell>()
    private var pressed: Cell? = null
    // Horizontal scroll of the toolbar when its buttons don't all fit.
    private var scroll = 0f
    private var contentW = 0f
    private var downX = 0f
    private var lastX = 0f
    private var dragging = false
    private var longFired = false
    private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private val longPress = Runnable {
        if (mode == Mode.TOOLBAR && !dragging) {
            longFired = true; pressed = null; invalidate()
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            listener?.onToolbarEdit()
        }
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val divider = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun luminance(c: Int): Float =
        (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f

    private val convertTargets = listOf(
        Transliterator.NEW to "→ Yangi", Transliterator.LATN to "→ Lotin", Transliterator.CYRL to "→ Кирилл",
    )

    /** Shows suggestions; [literal] is the typed word, shown in quotes when an autocorrect is pending. */
    fun setSuggestions(list: List<String>, correction: String?, literal: String?) {
        words = list; autocorrect = correction; this.literal = literal
        mode = if (list.isEmpty()) Mode.TOOLBAR else Mode.SUGGEST
        rebuild()
    }

    fun clear() { if (mode != Mode.CONVERT) setSuggestions(emptyList(), null, null) }

    fun showConvert() { mode = Mode.CONVERT; rebuild() }
    fun showToolbar() { mode = Mode.TOOLBAR; words = emptyList(); rebuild() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (44 * d).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = rebuild()

    private fun rebuild() {
        cells.clear(); pressed = null
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        when (mode) {
            Mode.SUGGEST -> {
                // Middle slot holds the best match, like every major keyboard.
                val ordered = ArrayList<String?>()
                val list = words
                ordered += list.getOrNull(1); ordered += list.getOrNull(0); ordered += list.getOrNull(2)
                if (autocorrect != null && literal != null) ordered[0] = "«$literal»"
                val cw = w / 3f
                ordered.forEachIndexed { i, s ->
                    if (s == null) return@forEachIndexed
                    val r = RectF(i * cw, 0f, (i + 1) * cw, h)
                    val word = if (s.startsWith("«") && s.endsWith("»")) literal!! else s
                    cells += Cell(r, text = s, bold = i == 1 && autocorrect != null) { listener?.onSuggestion(word) }
                }
            }
            Mode.TOOLBAR -> {
                if (!toolbarEnabled) { invalidate(); return }
                val list = items
                if (list.isEmpty()) { contentW = 0f; invalidate(); return }
                // Equal widths when they fit; otherwise fixed-width buttons that scroll sideways.
                val cw = maxOf(w / list.size, 48 * d)
                contentW = cw * list.size
                scroll = scroll.coerceIn(0f, maxOf(0f, contentW - w))
                list.forEachIndexed { i, a ->
                    cells += Cell(RectF(i * cw, 0f, (i + 1) * cw, h), icon = a.icon,
                        active = (a == Action.NUMBERS && numberRowOn) || a in activeActions) { listener?.onToolbar(a) }
                }
            }
            Mode.CONVERT -> {
                val back = 52 * d
                cells += Cell(RectF(0f, 0f, back, h), icon = Icon.BACK) { showToolbar() }
                val cw = (w - back) / convertTargets.size
                convertTargets.forEachIndexed { i, (target, label) ->
                    cells += Cell(RectF(back + i * cw, 0f, back + (i + 1) * cw, h), text = label) { listener?.onConvert(target) }
                }
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val st = style ?: return
        val t = st.theme
        val onImage = st.background != null
        if (!onImage) canvas.drawColor(t.stripBg)
        divider.color = t.border
        val pad = 6 * d
        // Image backgrounds can match the icon colour, so each item gets a soft
        // backing pill in the opposite tone plus a faint shadow.
        val lightInk = luminance(t.stripText) > 0.5f
        scrimPaint.color = if (lightInk) 0x66000000 else 0x99FFFFFF.toInt()
        val shadow = if (lightInk) 0x99000000.toInt() else 0x66FFFFFF
        if (onImage) textPaint.setShadowLayer(3 * d, 0f, d / 2, shadow) else textPaint.clearShadowLayer()
        val scrolled = mode == Mode.TOOLBAR && contentW > width
        canvas.save()
        if (scrolled) canvas.translate(-scroll, 0f)
        for ((i, c) in cells.withIndex()) {
            val r = c.rect
            if (onImage && c !== pressed && !c.active) {
                if (c.icon != null) {
                    canvas.drawCircle(r.centerX(), r.centerY(), 17 * d, scrimPaint)
                } else if (c.text != null) {
                    canvas.drawRoundRect(r.left + pad / 2, r.top + pad, r.right - pad / 2, r.bottom - pad, 10 * d, 10 * d, scrimPaint)
                }
            }
            if (c === pressed || c.active) {
                fillPaint.color = if (c.active && c !== pressed) (t.accent and 0x00FFFFFF) or 0x40000000 else t.keyPressed
                canvas.drawRoundRect(r.left + pad / 2, r.top + pad, r.right - pad / 2, r.bottom - pad, 10 * d, 10 * d, fillPaint)
            }
            val color = if (c.active) t.accent else t.stripText
            if (c.icon != null) {
                Icons.draw(canvas, c.icon, r.centerX(), r.centerY(), 22 * d, color)
            } else if (c.text != null) {
                textPaint.color = color
                textPaint.textSize = 16 * d * st.fontScale.coerceIn(0.85f, 1.2f)
                textPaint.typeface = if (c.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                val shown = TextUtils.ellipsize(c.text, android.text.TextPaint(textPaint), r.width() - 12 * d, TextUtils.TruncateAt.END).toString()
                val y = r.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
                canvas.drawText(shown, r.centerX(), y, textPaint)
            }
            if (mode == Mode.SUGGEST && !onImage && i < cells.lastIndex) {
                canvas.drawRect(r.right - d / 2, r.top + 12 * d, r.right + d / 2, r.bottom - 12 * d, divider)
            }
        }
        canvas.restore()
        if (scrolled) {
            // Thin indicator so people know more buttons are hidden.
            val w = width.toFloat()
            val bw = w * w / contentW
            val bx = (w - bw) * (scroll / (contentW - w))
            fillPaint.color = (t.stripText and 0x00FFFFFF) or 0x55000000
            canvas.drawRoundRect(bx, height - 3 * d, bx + bw, height - d, d, d, fillPaint)
        }
    }

    private fun hit(x: Float, y: Float): Cell? {
        val cx = if (mode == Mode.TOOLBAR && contentW > width) x + scroll else x
        return cells.firstOrNull { it.rect.contains(cx, y) }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; lastX = e.x; dragging = false; longFired = false
                pressed = hit(e.x, e.y); invalidate()
                if (mode == Mode.TOOLBAR && toolbarEnabled) postDelayed(longPress, 500)
                return pressed != null || mode == Mode.TOOLBAR
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == Mode.TOOLBAR && contentW > width && (dragging || kotlin.math.abs(e.x - downX) > slop)) {
                    if (!dragging) { dragging = true; removeCallbacks(longPress); pressed = null }
                    scroll = (scroll - (e.x - lastX)).coerceIn(0f, contentW - width)
                    invalidate()
                } else if (kotlin.math.abs(e.x - downX) > slop) {
                    removeCallbacks(longPress)
                }
                lastX = e.x
                val p = pressed
                if (p != null && hit(e.x, e.y) !== p) { pressed = null; invalidate() }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                val p = pressed
                pressed = null; invalidate()
                if (!longFired && !dragging && p != null && hit(e.x, e.y) === p) { performClick(); p.onTap() }
            }
            MotionEvent.ACTION_CANCEL -> { removeCallbacks(longPress); pressed = null; invalidate() }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
