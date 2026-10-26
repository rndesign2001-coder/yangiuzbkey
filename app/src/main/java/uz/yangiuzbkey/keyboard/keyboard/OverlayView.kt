package uz.yangiuzbkey.keyboard.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import uz.yangiuzbkey.keyboard.theme.ResolvedStyle

/**
 * Transparent layer above the whole keyboard window. Draws the magnified key
 * preview and the long-press alternatives popup so they can extend above the
 * key rows (over the suggestion strip).
 */
class OverlayView(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    var style: ResolvedStyle? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    // Preview
    private var previewRect: RectF? = null
    private var previewLabel = ""

    // Popup
    private var popupItems: List<String> = emptyList()
    private var popupLabels: List<String> = emptyList()
    private val cells = ArrayList<RectF>()
    private val popupFrame = RectF()
    var selected = -1
        private set

    init {
        isClickable = false
        isFocusable = false
    }

    val popupVisible get() = popupItems.isNotEmpty()

    fun showPreview(keyRect: RectF, label: String) {
        val w = maxOf(keyRect.width() * 1.15f, 44 * d)
        val h = keyRect.height() * 1.25f
        var left = keyRect.centerX() - w / 2
        left = left.coerceIn(0f, maxOf(0f, width - w))
        var top = keyRect.top - h - 4 * d
        if (top < 0) top = 0f
        previewRect = RectF(left, top, left + w, top + h)
        previewLabel = label
        invalidate()
    }

    fun hidePreview() {
        if (previewRect != null) { previewRect = null; invalidate() }
    }

    /** [items] are committed values; [labels] what is drawn (shifted case etc.). */
    fun showPopup(keyRect: RectF, items: List<String>, labels: List<String>, fingerX: Float) {
        hidePreview()
        popupItems = items; popupLabels = labels
        cells.clear()
        val perRow = minOf(6, items.size)
        val rowsN = (items.size + perRow - 1) / perRow
        val cw = maxOf(keyRect.width(), 38 * d)
        val ch = keyRect.height().coerceAtMost(56 * d)
        val pad = 4 * d
        val fw = cw * perRow + pad * 2
        val fh = ch * rowsN + pad * 2
        var left = keyRect.centerX() - cw / 2 - pad
        // Prefer extending to the side with more room so the popup stays on screen.
        if (left + fw > width) left = width - fw
        if (left < 0) left = 0f
        var top = keyRect.top - fh + keyRect.height() * 0.15f
        if (top < 0) top = 0f
        popupFrame.set(left, top, left + fw, top + fh)
        for (i in items.indices) {
            // Row 0 is the bottom row (closest to the finger).
            val r = i / perRow
            val col = i % perRow
            val x = left + pad + col * cw
            val y = top + pad + (rowsN - 1 - r) * ch
            cells += RectF(x, y, x + cw, y + ch)
        }
        selected = 0
        update(fingerX, keyRect.centerY())
        invalidate()
    }

    /** Moves the selection to the cell nearest to the finger. */
    fun update(x: Float, y: Float) {
        if (cells.isEmpty()) return
        var best = 0
        var bestD = Float.MAX_VALUE
        val cy = y.coerceIn(popupFrame.top, popupFrame.bottom - 1)
        for (i in cells.indices) {
            val c = cells[i]
            val dx = if (x < c.left) c.left - x else if (x > c.right) x - c.right else 0f
            val dy = if (cy < c.top) c.top - cy else if (cy > c.bottom) cy - c.bottom else 0f
            val dist = dx * dx + dy * dy * 4
            if (dist < bestD) { bestD = dist; best = i }
        }
        // Finger far outside: cancel selection.
        if (x < popupFrame.left - 60 * d || x > popupFrame.right + 60 * d) best = -1
        if (best != selected) { selected = best; invalidate() }
    }

    fun selectedItem(): String? = popupItems.getOrNull(selected)

    fun hidePopup() {
        if (popupItems.isNotEmpty()) { popupItems = emptyList(); popupLabels = emptyList(); cells.clear(); selected = -1; invalidate() }
    }

    fun hideAll() { hidePreview(); hidePopup(); clearTrail() }

    // Glide trail
    private val trail = ArrayList<Float>()
    private val trailPath = Path()
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    fun addTrail(x: Float, y: Float) {
        trail += x; trail += y
        while (trail.size > TRAIL_MAX * 2) { trail.removeAt(0); trail.removeAt(0) }
        invalidate()
    }

    fun clearTrail() { if (trail.isNotEmpty()) { trail.clear(); invalidate() } }

    override fun onDraw(canvas: Canvas) {
        val st = style ?: return
        val t = st.theme
        val r = 10 * d
        textPaint.typeface = if (st.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        if (trail.size >= 4) {
            trailPath.reset()
            trailPath.moveTo(trail[0], trail[1])
            var i = 2
            while (i < trail.size) { trailPath.lineTo(trail[i], trail[i + 1]); i += 2 }
            trailPaint.color = (t.accent and 0x00FFFFFF) or (0xB0 shl 24)
            trailPaint.strokeWidth = 5 * d
            canvas.drawPath(trailPath, trailPaint)
        }
        previewRect?.let { pr ->
            shadowPaint.color = 0x40000000
            canvas.drawRoundRect(pr.left, pr.top + 2 * d, pr.right, pr.bottom + 2 * d, r, r, shadowPaint)
            bgPaint.color = t.popupBg
            canvas.drawRoundRect(pr, r, r, bgPaint)
            textPaint.color = t.popupText
            textPaint.textSize = pr.height() * 0.48f * st.fontScale
            fitText(previewLabel, pr.width() * 0.85f)
            canvas.drawText(previewLabel, pr.centerX(), pr.centerY() - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
        }
        if (popupItems.isNotEmpty()) {
            shadowPaint.color = 0x48000000
            canvas.drawRoundRect(popupFrame.left, popupFrame.top + 3 * d, popupFrame.right, popupFrame.bottom + 3 * d, r, r, shadowPaint)
            bgPaint.color = t.popupBg
            canvas.drawRoundRect(popupFrame, r, r, bgPaint)
            for (i in cells.indices) {
                val c = cells[i]
                val sel = i == selected
                if (sel) {
                    bgPaint.color = t.popupSelected
                    canvas.drawRoundRect(c.left + 1 * d, c.top + 1 * d, c.right - 1 * d, c.bottom - 1 * d, 7 * d, 7 * d, bgPaint)
                }
                val color = if (sel) t.accentText else t.popupText
                val item = popupItems[i]
                val icon = when (item) {
                    Key.POP_EMOJI -> Icon.EMOJI
                    Key.POP_SETTINGS -> Icon.GEAR
                    Key.POP_CLIPBOARD -> Icon.CLIPBOARD
                    else -> null
                }
                if (icon != null) {
                    Icons.draw(canvas, icon, c.centerX(), c.centerY(), minOf(c.width(), c.height()) * 0.5f, color)
                } else {
                    val label = popupLabels.getOrElse(i) { item }
                    textPaint.color = color
                    textPaint.textSize = c.height() * (if (label.length > 2) 0.3f else 0.42f) * st.fontScale
                    fitText(label, c.width() * 0.9f)
                    canvas.drawText(label, c.centerX(), c.centerY() - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
                }
            }
        }
    }

    private fun fitText(s: String, maxW: Float) {
        val w = textPaint.measureText(s)
        if (w > maxW && w > 0) textPaint.textSize = textPaint.textSize * maxW / w
    }

    private companion object { const val TRAIL_MAX = 60 }
}
