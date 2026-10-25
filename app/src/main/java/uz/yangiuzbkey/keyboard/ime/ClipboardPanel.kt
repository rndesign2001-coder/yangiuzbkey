package uz.yangiuzbkey.keyboard.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import uz.yangiuzbkey.keyboard.R
import uz.yangiuzbkey.keyboard.keyboard.Icon
import uz.yangiuzbkey.keyboard.keyboard.Icons
import uz.yangiuzbkey.keyboard.theme.ResolvedStyle
import java.io.File

/** Clipboard history kept on the device only (never synced or uploaded). */
class ClipboardHistory(private val context: Context) {
    class Item(val text: String, var pinned: Boolean, val time: Long)

    private val file get() = File(context.filesDir, "clipboard.json")
    val items = ArrayList<Item>()

    init { load() }

    private fun load() {
        items.clear()
        try {
            if (!file.exists()) return
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items += Item(o.getString("t"), o.optBoolean("p"), o.optLong("ts"))
            }
        } catch (_: Exception) { }
    }

    private fun save() {
        try {
            val arr = JSONArray()
            for (it in items) arr.put(JSONObject().put("t", it.text).put("p", it.pinned).put("ts", it.time))
            file.writeText(arr.toString())
        } catch (_: Exception) { }
    }

    fun add(text: String) {
        if (text.isBlank() || text.length > 5000) return
        val existing = items.firstOrNull { it.text == text }
        items.remove(existing)
        items.add(0, Item(text, existing?.pinned ?: false, System.currentTimeMillis()))
        var unpinned = 0
        val it = items.iterator()
        while (it.hasNext()) { val x = it.next(); if (!x.pinned && ++unpinned > MAX) it.remove() }
        save()
    }

    fun togglePin(item: Item) { item.pinned = !item.pinned; save() }
    fun remove(item: Item) { items.remove(item); save() }
    fun clearUnpinned() { items.removeAll { !it.pinned }; save() }

    /** Pinned clips first, then newest. */
    fun sorted(): List<Item> = items.sortedWith(compareByDescending<Item> { it.pinned }.thenByDescending { it.time })

    /** Reads the current primary clip, skipping clips the source app marked sensitive. */
    fun capture(cm: ClipboardManager) {
        try {
            val clip: ClipData = cm.primaryClip ?: return
            if (Build.VERSION.SDK_INT >= 24) {
                val extras = clip.description?.extras
                if (extras != null && extras.getBoolean(EXTRA_IS_SENSITIVE)) return
            }
            if (clip.itemCount == 0) return
            val t = clip.getItemAt(0).coerceToText(context)?.toString() ?: return
            add(t)
        } catch (_: Exception) { }
    }

    companion object {
        const val MAX = 20
        /** Same value as ClipDescription.EXTRA_IS_SENSITIVE (API 33), usable on older SDKs. */
        private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    }
}

class ClipboardPanel(context: Context, private val history: ClipboardHistory) : LinearLayout(context) {

    interface Listener {
        fun onClipPaste(text: String)
        fun onClipClose()
    }

    var listener: Listener? = null
    private var style: ResolvedStyle? = null
    private val d = resources.displayMetrics.density
    private val header = Header(context)
    private val scroll = ScrollView(context)
    private val list = LinearLayout(context).apply { orientation = VERTICAL }
    private val empty = TextView(context).apply {
        gravity = Gravity.CENTER; setText(R.string.clipboard_empty)
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
        val p = (24 * d).toInt(); setPadding(p, p, p, p)
    }
    private val body = FrameLayout(context)

    /** Edit mode shows pin / delete badges on every card. */
    private var editing = false
    private var showAllRecent = false

    init {
        orientation = VERTICAL
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, (48 * d).toInt()))
        scroll.isFillViewport = true
        scroll.isVerticalScrollBarEnabled = false
        list.setPadding((8 * d).toInt(), 0, (8 * d).toInt(), (8 * d).toInt())
        scroll.addView(list)
        body.addView(scroll)
        body.addView(empty, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun setStyle(s: ResolvedStyle) { style = s; header.invalidate(); refresh() }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView === this && visibility != VISIBLE && editing) { editing = false; header.invalidate() }
    }

    fun refresh() {
        list.removeAllViews()
        val st = style
        val all = history.sorted()
        val recent = all.filter { !it.pinned }
        val pinned = all.filter { it.pinned }
        empty.visibility = if (all.isEmpty()) VISIBLE else GONE
        st?.let { empty.setTextColor(it.theme.hint) }
        if (recent.isNotEmpty()) {
            val more = recent.size > COLLAPSED
            val toggle = if (!more) 0 else if (showAllRecent) R.string.clip_show_less else R.string.clip_show_more
            addSection(R.string.clip_recent, toggle) { showAllRecent = !showAllRecent; refresh() }
            addGrid(if (showAllRecent || !more) recent else recent.take(COLLAPSED))
        }
        if (pinned.isNotEmpty()) {
            addSection(R.string.clip_pinned, 0, null)
            addGrid(pinned)
        }
    }

    private fun addSection(title: Int, toggle: Int, onToggle: (() -> Unit)?) {
        val t = style?.theme
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * d).toInt(), (10 * d).toInt(), (4 * d).toInt(), (6 * d).toInt())
        }
        row.addView(TextView(context).apply {
            setText(title); setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f); typeface = Typeface.DEFAULT_BOLD
            t?.let { setTextColor(it.stripText) }
            if (style?.background != null) setShadowLayer(3 * d, 0f, d / 2, 0x80000000.toInt())
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        if (toggle != 0) row.addView(TextView(context).apply {
            setText(toggle); setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f); typeface = Typeface.DEFAULT_BOLD
            t?.let { setTextColor(it.accent) }
            val p = (8 * d).toInt(); setPadding(p, p / 2, p, p / 2)
            isClickable = true; setOnClickListener { onToggle?.invoke() }
        })
        list.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun addGrid(items: List<ClipboardHistory.Item>) {
        val m = (4 * d).toInt()
        for (chunk in items.chunked(COLUMNS)) {
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (k in 0 until COLUMNS) {
                val lp = LayoutParams(0, (88 * d).toInt(), 1f).apply { setMargins(m, m, m, m) }
                val item = chunk.getOrNull(k)
                row.addView(if (item != null) card(item) else View(context), lp)
            }
            list.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    private fun card(item: ClipboardHistory.Item): View {
        val t = style?.theme
        val frame = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = 14 * d; setColor(t?.keyBg ?: 0xFF2B2B2B.toInt())
                if (item.pinned && t != null) setStroke((1.5f * d).toInt(), t.accent)
            }
            isClickable = true
            setOnClickListener { if (editing) { history.togglePin(item); refresh() } else listener?.onClipPaste(item.text) }
            setOnLongClickListener { editing = !editing; header.invalidate(); refresh(); true }
        }
        frame.addView(TextView(context).apply {
            text = item.text.trim(); maxLines = 4; ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f); setLineSpacing(0f, 1.05f)
            t?.let { setTextColor(it.keyText) }
            val p = (10 * d).toInt(); setPadding(p, p, if (editing) (26 * d).toInt() else p, p)
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        if (editing) {
            val sz = (30 * d).toInt()
            frame.addView(IconButton(context, Icon.CLOSE, false) { history.remove(item); refresh() },
                FrameLayout.LayoutParams(sz, sz, Gravity.TOP or Gravity.END))
            frame.addView(IconButton(context, Icon.PIN, item.pinned) { history.togglePin(item); refresh() },
                FrameLayout.LayoutParams(sz, sz, Gravity.BOTTOM or Gravity.END))
        }
        return frame
    }

    private inner class IconButton(context: Context, val icon: Icon, val active: Boolean, val onTap: () -> Unit) : View(context) {
        private var down = false
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val t = style?.theme ?: return
            fill.color = if (down) t.keyPressed else t.border
            canvas.drawCircle(width / 2f, height / 2f, width / 2.6f, fill)
            Icons.draw(canvas, icon, width / 2f, height / 2f, 14 * d, if (active) t.accent else t.keyText)
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { down = true; invalidate() }
                MotionEvent.ACTION_UP -> { down = false; invalidate(); if (e.x in 0f..width.toFloat() && e.y in 0f..height.toFloat()) { performClick(); onTap() } }
                MotionEvent.ACTION_CANCEL -> { down = false; invalidate() }
            }
            return true
        }
        override fun performClick(): Boolean { super.performClick(); return true }
    }

    /** Back to letters | Bufer ............ pin (edit mode) · clear unpinned. */
    private inner class Header(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private var pressed = 0 // 1 = back, 2 = pin, 3 = trash
        private val back = RectF(); private val pin = RectF(); private val trash = RectF()

        override fun onDraw(canvas: Canvas) {
            val st = style ?: return
            val t = st.theme
            if (st.background == null) canvas.drawColor(t.stripBg)
            val h = height.toFloat(); val w = width.toFloat()
            back.set(0f, 0f, 56 * d, h)
            trash.set(w - 52 * d, 0f, w - 4 * d, h); pin.set(trash.left - 48 * d, 0f, trash.left, h)
            val rects = listOf(back, pin, trash)
            for ((i, r) in rects.withIndex()) {
                val on = pressed == i + 1 || (i == 1 && editing)
                if (on || st.background != null) {
                    fill.color = if (on) t.keyPressed else 0x55000000
                    canvas.drawCircle(r.centerX(), r.centerY(), 18 * d, fill)
                }
            }
            Icons.draw(canvas, Icon.KEYBOARD, back.centerX(), back.centerY(), 22 * d, t.stripText)
            paint.color = t.border; paint.strokeWidth = d
            canvas.drawLine(back.right, h * 0.28f, back.right, h * 0.72f, paint)
            paint.color = t.stripText; paint.textSize = 17 * d; paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(context.getString(R.string.tb_clipboard), back.right + 14 * d, h / 2 - (paint.descent() + paint.ascent()) / 2, paint)
            Icons.draw(canvas, Icon.PIN, pin.centerX(), pin.centerY(), 20 * d, if (editing) t.accent else t.stripText)
            Icons.draw(canvas, Icon.TRASH, trash.centerX(), trash.centerY(), 20 * d, t.stripText)
        }

        private fun hit(x: Float, y: Float) = when {
            back.contains(x, y) -> 1; pin.contains(x, y) -> 2; trash.contains(x, y) -> 3; else -> 0
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { pressed = hit(e.x, e.y); invalidate() }
                MotionEvent.ACTION_UP -> {
                    val p = pressed; pressed = 0; invalidate()
                    if (p != 0 && p == hit(e.x, e.y)) {
                        performClick()
                        when (p) {
                            1 -> { editing = false; listener?.onClipClose() }
                            2 -> { editing = !editing; refresh() }
                            else -> { history.clearUnpinned(); refresh() }
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> { pressed = 0; invalidate() }
            }
            return true
        }
        override fun performClick(): Boolean { super.performClick(); return true }
    }

    companion object {
        private const val COLUMNS = 3
        private const val COLLAPSED = 6
    }
}
