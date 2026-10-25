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

    enum class Action { SETTINGS, EMOJI, CLIPBOARD, NUMBERS, THEME, CONVERT, HIDE, BACK }

    interface Listener {
        fun onSuggestion(word: String)
        fun onToolbar(action: Action)
        fun onConvert(target: String)
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

    private val d = resources.displayMetrics.density
    private var mode = Mode.TOOLBAR
    private var words: List<String> = emptyList()
    private var autocorrect: String? = null
    private var literal: String? = null
    private val cells = ArrayList<Cell>()
    private var pressed: Cell? = null

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val divider = Paint(Paint.ANTI_ALIAS_FLAG)

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
                val items = listOf(
                    Icon.GEAR to Action.SETTINGS, Icon.EMOJI to Action.EMOJI, Icon.CLIPBOARD to Action.CLIPBOARD,
                    Icon.NUMBERS to Action.NUMBERS, Icon.PALETTE to Action.THEME, Icon.CONVERT to Action.CONVERT,
                    Icon.CHEVRON_DOWN to Action.HIDE,
                )
                val cw = w / items.size
                items.forEachIndexed { i, (icon, a) ->
                    cells += Cell(RectF(i * cw, 0f, (i + 1) * cw, h), icon = icon,
                        active = a == Action.NUMBERS && numberRowOn) { listener?.onToolbar(a) }
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
        if (st.background == null) canvas.drawColor(t.stripBg)
        divider.color = t.border
        val pad = 6 * d
        for ((i, c) in cells.withIndex()) {
            val r = c.rect
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
            if (mode == Mode.SUGGEST && i < cells.lastIndex) {
                canvas.drawRect(r.right - d / 2, r.top + 12 * d, r.right + d / 2, r.bottom - 12 * d, divider)
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = cells.firstOrNull { it.rect.contains(e.x, e.y) }; invalidate()
                return pressed != null
            }
            MotionEvent.ACTION_MOVE -> {
                val p = pressed
                if (p != null && !p.rect.contains(e.x, e.y)) { pressed = null; invalidate() }
            }
            MotionEvent.ACTION_UP -> {
                val p = pressed
                pressed = null; invalidate()
                if (p != null && p.rect.contains(e.x, e.y)) { performClick(); p.onTap() }
            }
            MotionEvent.ACTION_CANCEL -> { pressed = null; invalidate() }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
