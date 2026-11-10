package uz.yangiuzbkey.keyboard.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import uz.yangiuzbkey.keyboard.theme.ResolvedStyle

/** Miniature "three keys" sample of one key style, used by the key design picker. */
class KeyStylePreview(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rc = RectF()
    private var shaderH = -1
    private var shaderFor: ResolvedStyle? = null

    var style: ResolvedStyle? = null
        set(v) { field = v; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val st = style ?: return
        val t = st.theme
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (shaderH != height || shaderFor !== st) {
            bg.shader = LinearGradient(0f, 0f, 0f, h, t.bg, t.bg2, Shader.TileMode.CLAMP)
            shaderH = height; shaderFor = st
        }
        canvas.drawRoundRect(0f, 0f, w, h, 12f * d, 12f * d, bg)

        val padH = 8f * d
        val gap = 5f * d
        val kh = (h - 20f * d).coerceAtLeast(8f * d)
        val top = (h - kh) / 2f
        // Two letter keys and a wider accent (enter) key.
        val unit = (w - padH * 2 - gap * 2) / 3.4f
        val widths = floatArrayOf(unit, unit, unit * 1.4f)
        label.typeface = if (st.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        label.textSize = kh * 0.44f
        var x = padH
        for (i in 0..2) {
            rc.set(x, top, x + widths[i], top + kh)
            val enter = i == 2
            KeyPainter.draw(canvas, rc, st, false, enter, false, d, 1f)
            if (enter) {
                Icons.draw(canvas, Icon.ENTER, rc.centerX(), rc.centerY(), kh * 0.5f, t.accentText)
            } else {
                label.color = t.keyText
                canvas.drawText(if (i == 0) "A" else "b", rc.centerX(), rc.centerY() - (label.descent() + label.ascent()) / 2, label)
            }
            x += widths[i] + gap
        }
    }
}
