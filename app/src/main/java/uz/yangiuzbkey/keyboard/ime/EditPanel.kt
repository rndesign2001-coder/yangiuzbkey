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
import uz.yangiuzbkey.keyboard.keyboard.Icon
import uz.yangiuzbkey.keyboard.keyboard.Icons
import uz.yangiuzbkey.keyboard.theme.ResolvedStyle

/** Text-edit panel: cursor arrows, selection mode, copy/cut/paste, undo/redo. */
class EditPanel(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onEditAction(id: String)
        fun onEditClose()
    }

    private class Item(val id: String, val icon: Icon, val label: Int)

    var listener: Listener? = null
    /** While on, arrow keys extend the selection (Shift held). */
    var selecting = false
        set(v) { field = v; for (c in chips) c.invalidate() }

    private var style: ResolvedStyle? = null
    private val d = resources.displayMetrics.density
    private val list = LinearLayout(context).apply { orientation = VERTICAL }
    private val title = TextView(context)
    private val done = TextView(context)
    private val chips = ArrayList<Chip>()

    private val items = listOf(
        Item("select", Icon.SELECT, R.string.edit_select),
        Item("up", Icon.ARROW_UP, R.string.edit_up),
        Item("select_all", Icon.SELECT_ALL, R.string.edit_select_all),
        Item("delete", Icon.DELETE, R.string.edit_delete),
        Item("left", Icon.BACK, R.string.edit_left),
        Item("down", Icon.ARROW_DOWN, R.string.edit_down),
        Item("right", Icon.BACK, R.string.edit_right),
        Item("copy", Icon.COPY, R.string.edit_copy),
        Item("home", Icon.HOME, R.string.edit_home),
        Item("end", Icon.END, R.string.edit_end),
        Item("cut", Icon.CUT, R.string.edit_cut),
        Item("paste", Icon.PASTE, R.string.edit_paste),
        Item("undo", Icon.UNDO, R.string.edit_undo),
        Item("redo", Icon.REDO, R.string.edit_redo),
    )

    init {
        orientation = VERTICAL
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * d).toInt(), 0, (8 * d).toInt(), 0)
        }
        title.apply {
            setText(R.string.edit_title); setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            typeface = Typeface.DEFAULT_BOLD
        }
        done.apply {
            setText(R.string.toolbar_done); setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            val p = (14 * d).toInt(); setPadding(p, (6 * d).toInt(), p, (6 * d).toInt())
            isClickable = true
            setOnClickListener { selecting = false; listener?.onEditClose() }
        }
        header.addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(done)
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, (48 * d).toInt()))
        val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false; isFillViewport = true }
        list.setPadding((6 * d).toInt(), 0, (6 * d).toInt(), (8 * d).toInt())
        scroll.addView(list)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        val m = (3 * d).toInt()
        for (chunk in items.chunked(COLUMNS)) {
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (k in 0 until COLUMNS) {
                val lp = LayoutParams(0, (62 * d).toInt(), 1f).apply { setMargins(m, m, m, m) }
                val it = chunk.getOrNull(k)
                row.addView(if (it != null) Chip(context, it).also { c -> chips.add(c) } else View(context), lp)
            }
            list.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    fun open() { selecting = false }

    fun setStyle(s: ResolvedStyle) {
        style = s
        val t = s.theme
        title.setTextColor(t.stripText)
        done.setTextColor(t.accentText)
        done.background = GradientDrawable().apply { cornerRadius = 18 * d; setColor(t.accent) }
        if (s.background != null) title.setShadowLayer(3 * d, 0f, d / 2, 0x80000000.toInt())
        for (c in chips) c.invalidate()
    }

    private inner class Chip(context: Context, val item: Item) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val r = RectF()
        private var pressed = false
        private val repeat = object : Runnable {
            override fun run() { fire(); postDelayed(this, 60) }
        }

        private val repeats get() = item.id in REPEATING

        override fun onDraw(canvas: Canvas) {
            val t = style?.theme ?: return
            val w = width.toFloat(); val h = height.toFloat()
            r.set(0f, 0f, w, h)
            val on = item.id == "select" && selecting
            fill.color = when { on -> t.accent; pressed -> t.keyPressed; else -> t.keyBg }
            canvas.drawRoundRect(r, 12 * d, 12 * d, fill)
            val fg = if (on) t.accentText else t.keyText
            if (item.id == "right") {
                // mirror the "back" arrow
                canvas.save(); canvas.scale(-1f, 1f, w / 2, h * 0.36f)
                Icons.draw(canvas, item.icon, w / 2, h * 0.36f, 20 * d, fg)
                canvas.restore()
            } else Icons.draw(canvas, item.icon, w / 2, h * 0.36f, 20 * d, fg)
            text.color = fg; text.textSize = 10.5f * d
            val label = TextUtils.ellipsize(context.getString(item.label), android.text.TextPaint(text), w - 8 * d, TextUtils.TruncateAt.END).toString()
            canvas.drawText(label, w / 2, h * 0.78f, text)
        }

        private fun fire() {
            if (item.id == "select") selecting = !selecting
            else listener?.onEditAction(item.id)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressed = true; invalidate()
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (repeats) { fire(); postDelayed(repeat, 400) }
                }
                MotionEvent.ACTION_UP -> {
                    val was = pressed; pressed = false; invalidate(); removeCallbacks(repeat)
                    if (was && !repeats && e.x in 0f..width.toFloat() && e.y in 0f..height.toFloat()) { performClick(); fire() }
                }
                MotionEvent.ACTION_CANCEL -> { pressed = false; invalidate(); removeCallbacks(repeat) }
            }
            return true
        }
        override fun performClick(): Boolean { super.performClick(); return true }
    }

    companion object {
        private const val COLUMNS = 4
        private val REPEATING = setOf("left", "right", "up", "down", "delete")
    }
}
