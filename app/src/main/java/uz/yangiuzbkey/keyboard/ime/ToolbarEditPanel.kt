package uz.yangiuzbkey.keyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import uz.yangiuzbkey.keyboard.R
import uz.yangiuzbkey.keyboard.keyboard.Icons
import uz.yangiuzbkey.keyboard.theme.ResolvedStyle

/**
 * Samsung-style toolbar editor: active buttons carry a "−" badge and ◀ ▶ arrows,
 * the "Mavjud tugmalar" pool below carries "+" badges.
 */
class ToolbarEditPanel(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onToolbarEditDone(items: List<SuggestionStrip.Action>)
    }

    var listener: Listener? = null
    private var style: ResolvedStyle? = null
    private val d = resources.displayMetrics.density
    private val active = ArrayList<SuggestionStrip.Action>()
    private val list = LinearLayout(context).apply { orientation = VERTICAL }
    private val title = TextView(context)
    private val done = TextView(context)

    init {
        orientation = VERTICAL
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * d).toInt(), 0, (8 * d).toInt(), 0)
        }
        title.apply {
            setText(R.string.toolbar_edit_title); setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            typeface = Typeface.DEFAULT_BOLD
        }
        done.apply {
            setText(R.string.toolbar_done); setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            val p = (14 * d).toInt(); setPadding(p, (6 * d).toInt(), p, (6 * d).toInt())
            isClickable = true
            setOnClickListener { listener?.onToolbarEditDone(ArrayList(active)) }
        }
        header.addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(done)
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, (48 * d).toInt()))
        val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false; isFillViewport = true }
        list.setPadding((6 * d).toInt(), 0, (6 * d).toInt(), (8 * d).toInt())
        scroll.addView(list)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun open(items: List<SuggestionStrip.Action>) {
        active.clear(); active.addAll(items)
        refresh()
    }

    fun setStyle(s: ResolvedStyle) {
        style = s
        val t = s.theme
        title.setTextColor(t.stripText)
        done.setTextColor(t.accentText)
        done.background = GradientDrawable().apply { cornerRadius = 18 * d; setColor(t.accent) }
        if (s.background != null) title.setShadowLayer(3 * d, 0f, d / 2, 0x80000000.toInt())
        refresh()
    }

    private fun refresh() {
        list.removeAllViews()
        addLabel(context.getString(R.string.toolbar_edit_hint), false)
        addGrid(active, true)
        addLabel(context.getString(R.string.toolbar_available), true)
        val pool = SuggestionStrip.EDITABLE.filter { it !in active }
        if (pool.isEmpty()) addLabel(context.getString(R.string.toolbar_none), false)
        else addGrid(pool, false)
    }

    private fun addLabel(text: String, bold: Boolean) {
        val t = style?.theme
        list.addView(TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, if (bold) 14f else 12f)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            t?.let { setTextColor(if (bold) it.stripText else it.hint) }
            if (style?.background != null) setShadowLayer(3 * d, 0f, d / 2, 0x80000000.toInt())
            setPadding((8 * d).toInt(), (if (bold) 12 else 2) * d.toInt(), (8 * d).toInt(), (4 * d).toInt())
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun addGrid(items: List<SuggestionStrip.Action>, isActive: Boolean) {
        val m = (3 * d).toInt()
        for (chunk in items.chunked(COLUMNS)) {
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (k in 0 until COLUMNS) {
                val lp = LayoutParams(0, (78 * d).toInt(), 1f).apply { setMargins(m, m, m, m) }
                val a = chunk.getOrNull(k)
                row.addView(if (a != null) Chip(context, a, isActive) else View(context), lp)
            }
            list.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    private fun apply(a: SuggestionStrip.Action, zone: Int) {
        val i = active.indexOf(a)
        when (zone) {
            Z_BADGE -> if (i >= 0) active.removeAt(i) else active.add(a)
            Z_LEFT -> if (i > 0) { active.removeAt(i); active.add(i - 1, a) }
            Z_RIGHT -> if (i >= 0 && i < active.size - 1) { active.removeAt(i); active.add(i + 1, a) }
            else -> return
        }
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        refresh()
    }

    private inner class Chip(context: Context, val action: SuggestionStrip.Action, val isActive: Boolean) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val r = RectF()
        private val badge = RectF(); private val left = RectF(); private val right = RectF()
        private var pressed = 0

        override fun onDraw(canvas: Canvas) {
            val t = style?.theme ?: return
            val w = width.toFloat(); val h = height.toFloat()
            r.set(0f, 0f, w, h)
            fill.color = if (pressed == Z_BODY) t.keyPressed else t.keyBg
            canvas.drawRoundRect(r, 12 * d, 12 * d, fill)
            val idx = active.indexOf(action)
            Icons.draw(canvas, action.icon, w / 2, h * 0.36f, 22 * d, t.keyText)
            text.color = t.keyText; text.textSize = 10.5f * d
            val label = TextUtils.ellipsize(action.label, android.text.TextPaint(text), w - 8 * d, TextUtils.TruncateAt.END).toString()
            canvas.drawText(label, w / 2, h * 0.72f, text)
            // "−" (remove) or "+" (add) badge, top-right
            val br = 10 * d
            badge.set(w - 2 * br - 2 * d, 2 * d, w - 2 * d, 2 * br + 2 * d)
            fill.color = if (isActive) 0xFFE53935.toInt() else 0xFF43A047.toInt()
            canvas.drawCircle(badge.centerX(), badge.centerY(), br, fill)
            fill.color = 0xFFFFFFFF.toInt(); fill.strokeWidth = 2.2f * d; fill.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(badge.centerX() - br / 2, badge.centerY(), badge.centerX() + br / 2, badge.centerY(), fill)
            if (!isActive) canvas.drawLine(badge.centerX(), badge.centerY() - br / 2, badge.centerX(), badge.centerY() + br / 2, fill)
            if (isActive) {
                val az = 26 * d
                left.set(0f, h - az, az, h); right.set(w - az, h - az, w, h)
                text.textSize = 13 * d
                text.color = if (idx > 0) t.accent else t.border
                canvas.drawText("◀", left.centerX(), left.centerY() + 4 * d, text)
                text.color = if (idx < active.size - 1) t.accent else t.border
                canvas.drawText("▶", right.centerX(), right.centerY() + 4 * d, text)
            } else { left.setEmpty(); right.setEmpty() }
            // number in the toolbar order, top-left
            if (isActive) {
                text.textSize = 10 * d; text.color = t.hint
                canvas.drawText((idx + 1).toString(), 10 * d, 14 * d, text)
            }
        }

        private fun zone(x: Float, y: Float): Int {
            val slop = 6 * d
            return when {
                x >= badge.left - slop && y <= badge.bottom + slop -> Z_BADGE
                !left.isEmpty && x <= left.right + slop && y >= left.top - slop -> Z_LEFT
                !right.isEmpty && x >= right.left - slop && y >= right.top - slop -> Z_RIGHT
                x in 0f..width.toFloat() && y in 0f..height.toFloat() -> Z_BODY
                else -> 0
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { pressed = zone(e.x, e.y); invalidate() }
                MotionEvent.ACTION_UP -> {
                    val p = pressed; pressed = 0; invalidate()
                    if (p != 0 && p == zone(e.x, e.y)) {
                        performClick()
                        // tapping a pool chip adds it; tapping an active chip body does nothing
                        post { this@ToolbarEditPanel.apply(action, if (!isActive && p == Z_BODY) Z_BADGE else p) }
                    }
                }
                MotionEvent.ACTION_CANCEL -> { pressed = 0; invalidate() }
            }
            return true
        }
        override fun performClick(): Boolean { super.performClick(); return true }
    }

    companion object {
        private const val COLUMNS = 4
        private const val Z_BODY = 1
        private const val Z_BADGE = 2
        private const val Z_LEFT = 3
        private const val Z_RIGHT = 4
    }
}
