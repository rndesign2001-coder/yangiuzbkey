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

    init {
        orientation = VERTICAL
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, (44 * d).toInt()))
        scroll.isFillViewport = true
        scroll.addView(list)
        body.addView(scroll)
        body.addView(empty, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun setStyle(s: ResolvedStyle) { style = s; header.invalidate(); refresh() }

    fun refresh() {
        list.removeAllViews()
        val st = style
        val items = history.sorted()
        empty.visibility = if (items.isEmpty()) VISIBLE else GONE
        st?.let { empty.setTextColor(it.theme.hint) }
        val m = (6 * d).toInt()
        for (item in items) {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = 12 * d; setColor(st?.theme?.keyBg ?: 0xFFFFFFFF.toInt())
                }
                setPadding((12 * d).toInt(), (6 * d).toInt(), (2 * d).toInt(), (6 * d).toInt())
                isClickable = true
                setOnClickListener { listener?.onClipPaste(item.text) }
            }
            val tv = TextView(context).apply {
                text = item.text.replace('\n', ' ').trim(); maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f); st?.let { setTextColor(it.theme.keyText) }
            }
            row.addView(tv, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            row.addView(IconButton(context, Icon.PIN, item.pinned) { history.togglePin(item); refresh() },
                LayoutParams((40 * d).toInt(), (40 * d).toInt()))
            row.addView(IconButton(context, Icon.CLOSE, false) { history.remove(item); refresh() },
                LayoutParams((40 * d).toInt(), (40 * d).toInt()))
            list.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(m, m / 2, m, m / 2) })
        }
    }

    private inner class IconButton(context: Context, val icon: Icon, val active: Boolean, val onTap: () -> Unit) : View(context) {
        private var down = false
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val t = style?.theme ?: return
            if (down) { fill.color = t.keyPressed; canvas.drawCircle(width / 2f, height / 2f, width / 2.4f, fill) }
            Icons.draw(canvas, icon, width / 2f, height / 2f, 18 * d, if (active) t.accent else t.hint)
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

    /** Title · clear unpinned · back to letters. */
    private inner class Header(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private var pressed = 0 // 1 = trash, 2 = abc
        private val trash = RectF(); private val abc = RectF()

        override fun onDraw(canvas: Canvas) {
            val st = style ?: return
            val t = st.theme
            if (st.background == null) canvas.drawColor(t.stripBg)
            val h = height.toFloat(); val w = width.toFloat()
            abc.set(w - 64 * d, 0f, w, h); trash.set(abc.left - 52 * d, 0f, abc.left, h)
            paint.color = t.stripText; paint.textSize = 15 * d; paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(context.getString(R.string.tb_clipboard), 16 * d, h / 2 - (paint.descent() + paint.ascent()) / 2, paint)
            for ((i, r) in listOf(trash, abc).withIndex()) if (pressed == i + 1) {
                fill.color = t.keyPressed
                canvas.drawRoundRect(r.left + 4 * d, r.top + 6 * d, r.right - 4 * d, r.bottom - 6 * d, 10 * d, 10 * d, fill)
            }
            Icons.draw(canvas, Icon.TRASH, trash.centerX(), trash.centerY(), 20 * d, t.stripText)
            paint.textAlign = Paint.Align.CENTER; paint.textSize = 14 * d
            canvas.drawText(context.getString(R.string.abc), abc.centerX(), abc.centerY() - (paint.descent() + paint.ascent()) / 2, paint)
        }

        private fun hit(x: Float, y: Float) = when { trash.contains(x, y) -> 1; abc.contains(x, y) -> 2; else -> 0 }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { pressed = hit(e.x, e.y); invalidate() }
                MotionEvent.ACTION_UP -> {
                    val p = pressed; pressed = 0; invalidate()
                    if (p != 0 && p == hit(e.x, e.y)) {
                        performClick()
                        if (p == 1) { history.clearUnpinned(); refresh() } else listener?.onClipClose()
                    }
                }
                MotionEvent.ACTION_CANCEL -> { pressed = 0; invalidate() }
            }
            return true
        }
        override fun performClick(): Boolean { super.performClick(); return true }
    }
}
