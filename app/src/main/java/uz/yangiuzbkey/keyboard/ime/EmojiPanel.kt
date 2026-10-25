package uz.yangiuzbkey.keyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import uz.yangiuzbkey.keyboard.keyboard.Icon
import uz.yangiuzbkey.keyboard.keyboard.Icons
import uz.yangiuzbkey.keyboard.theme.ResolvedStyle
import uz.yangiuzbkey.keyboard.util.Prefs

/** Emoji picker: recents + categories, filtered to what the device can render. */
class EmojiPanel(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onEmoji(emoji: String)
        fun onEmojiDelete()
        fun onEmojiClose()
    }

    private class Category(val id: String, val icon: String, val items: List<String>)

    var listener: Listener? = null
    private val prefs = Prefs(context)
    private val d = resources.displayMetrics.density
    private val grid = GridView(context)
    private val tabs = TabBar(context)
    private var categories: List<Category> = emptyList()
    private var current = 0
    private var style: ResolvedStyle? = null
    private var items: List<String> = emptyList()

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv = (convertView as? TextView) ?: TextView(context).apply {
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26f)
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (46 * d).toInt())
                includeFontPadding = false
            }
            tv.text = items[position]
            return tv
        }
    }

    init {
        orientation = VERTICAL
        grid.numColumns = GridView.AUTO_FIT
        grid.columnWidth = (44 * d).toInt()
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.selector = android.graphics.drawable.ColorDrawable(0)
        grid.isVerticalScrollBarEnabled = false
        grid.setPadding((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), 0)
        grid.clipToPadding = false
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ ->
            val e = items.getOrNull(pos) ?: return@setOnItemClickListener
            addRecent(e); listener?.onEmoji(e)
        }
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(tabs, LayoutParams(LayoutParams.MATCH_PARENT, (44 * d).toInt()))
    }

    fun setStyle(s: ResolvedStyle) { style = s; tabs.invalidate(); adapter.notifyDataSetChanged() }

    /** Loads data (cached after the first call) and shows recents or the first category. */
    fun open() {
        if (categories.isEmpty()) categories = load(context)
        val recents = recents()
        select(if (recents.isNotEmpty()) 0 else 1)
    }

    private fun select(i: Int) {
        current = i.coerceIn(0, categories.size)
        items = if (current == 0) recents() else categories.getOrNull(current - 1)?.items ?: emptyList()
        adapter.notifyDataSetChanged()
        grid.setSelection(0)
        tabs.invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        // Grid cells inherit text colour from the theme each draw.
        val c = style?.theme?.keyText
        if (c != null) for (i in 0 until grid.childCount) (grid.getChildAt(i) as? TextView)?.setTextColor(c)
        super.dispatchDraw(canvas)
    }

    private fun recents(): List<String> =
        (prefs.sp.getString("emoji_recent", "") ?: "").split(' ').filter { it.isNotEmpty() }

    private fun addRecent(e: String) {
        val list = ArrayList(recents()); list.remove(e); list.add(0, e)
        prefs.sp.edit().putString("emoji_recent", list.take(32).joinToString(" ")).apply()
        if (current == 0) return // keep the grid stable while the user taps
    }

    /** Bottom bar: ABC · category tabs · delete (repeats while held). */
    private inner class TabBar(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val handler = Handler(Looper.getMainLooper())
        private var pressedIdx = -2
        private val repeat = object : Runnable {
            override fun run() { listener?.onEmojiDelete(); handler.postDelayed(this, 60) }
        }

        private fun slotCount() = categories.size + 1 + 2 // recents + cats + ABC + delete
        private fun slotRect(i: Int, out: RectF) {
            val w = width.toFloat() / slotCount(); out.set(i * w, 0f, (i + 1) * w, height.toFloat())
        }
        private val r = RectF()

        override fun onDraw(canvas: Canvas) {
            val st = style ?: return
            val t = st.theme
            fill.color = t.stripBg; if (st.background == null) canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
            val n = slotCount()
            for (i in 0 until n) {
                slotRect(i, r)
                val catIdx = i - 1 // slot 0 = ABC, last = delete
                val selected = catIdx == current
                if (selected || pressedIdx == i) {
                    fill.color = if (selected) t.keyFuncBg else t.keyPressed
                    canvas.drawRoundRect(r.left + 3 * d, r.top + 5 * d, r.right - 3 * d, r.bottom - 5 * d, 10 * d, 10 * d, fill)
                }
                when {
                    i == 0 -> {
                        paint.color = t.stripText; paint.textSize = 14 * d; paint.isFakeBoldText = true
                        canvas.drawText("ABC", r.centerX(), r.centerY() - (paint.descent() + paint.ascent()) / 2, paint)
                    }
                    i == n - 1 -> Icons.draw(canvas, Icon.DELETE, r.centerX(), r.centerY(), 22 * d, t.stripText)
                    else -> {
                        val label = if (catIdx == 0) "🕘" else categories[catIdx - 1].icon
                        paint.isFakeBoldText = false; paint.textSize = 18 * d
                        paint.alpha = if (selected) 255 else 170
                        canvas.drawText(label, r.centerX(), r.centerY() - (paint.descent() + paint.ascent()) / 2, paint)
                        paint.alpha = 255
                    }
                }
            }
        }

        private fun hit(x: Float): Int = (x / (width.toFloat() / slotCount())).toInt().coerceIn(0, slotCount() - 1)

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressedIdx = hit(e.x); invalidate()
                    if (pressedIdx == slotCount() - 1) {
                        listener?.onEmojiDelete(); handler.postDelayed(repeat, 400)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(repeat)
                    val i = pressedIdx; pressedIdx = -2; invalidate()
                    if (i == hit(e.x)) when (i) {
                        0 -> listener?.onEmojiClose()
                        slotCount() - 1 -> Unit
                        else -> select(i - 1)
                    }
                }
                MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(repeat); pressedIdx = -2; invalidate() }
            }
            return true
        }

        override fun onDetachedFromWindow() { handler.removeCallbacks(repeat); super.onDetachedFromWindow() }
    }

    companion object {
        private var cache: List<Category>? = null
        private val ICONS = mapOf(
            "smileys" to "😀", "people" to "👋", "nature" to "🐻", "food" to "🍔", "travel" to "🚗",
            "activities" to "⚽", "objects" to "💡", "symbols" to "❤", "flags" to "🏳",
        )

        @Synchronized
        private fun load(ctx: Context): List<Category> {
            cache?.let { return it }
            val paint = Paint()
            val out = ArrayList<Category>()
            var id: String? = null
            val cur = ArrayList<String>()
            fun flush() {
                val c = id ?: return
                if (cur.isNotEmpty()) out += Category(c, ICONS[c] ?: "•", ArrayList(cur))
                cur.clear()
            }
            try {
                ctx.assets.open("emoji.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
                    for (raw in lines) {
                        val line = raw.trim()
                        if (line.isEmpty()) continue
                        if (line.startsWith("@")) { flush(); id = line.substring(1); continue }
                        val parts = line.split(' ').filter { it.isNotEmpty() }
                        if (parts.size < 2) continue
                        val ver = parts[0].toFloatOrNull() ?: continue
                        for (e in parts.drop(1)) if (renderable(paint, e, ver)) cur += e
                    }
                }
            } catch (_: Exception) { }
            flush()
            cache = out
            return out
        }

        private fun renderable(paint: Paint, e: String, version: Float): Boolean =
            if (Build.VERSION.SDK_INT >= 23) paint.hasGlyph(e) else version <= 1.0f
    }
}
