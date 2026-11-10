package uz.yangiuzbkey.keyboard.keyboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import uz.yangiuzbkey.keyboard.theme.KeyStyle
import uz.yangiuzbkey.keyboard.theme.ResolvedStyle
import kotlin.math.min

/**
 * Draws the body of one key (fill, shadow, gloss, strokes) for every [KeyStyle].
 * Shared by the keyboard itself and the style previews in settings. UI thread only.
 */
object KeyPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val gloss = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glossMatrix = Matrix()

    /** Unit-height top-light / bottom-shade overlay, scaled per key via a local matrix. */
    private val glossShader = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(0x38FFFFFF, 0x00FFFFFF, 0x24000000),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )

    fun withAlpha(c: Int, a: Float): Int =
        Color.argb((Color.alpha(c) * a).toInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    fun darken(c: Int, f: Float = 0.82f): Int =
        Color.argb(Color.alpha(c), (Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())

    /**
     * @param rc key bounds; may be shifted in place (pressed "raised" keys sink), so the
     *           caller's label drawn afterwards follows the key.
     * @param alpha extra opacity factor (keys over a photo background are slightly see-through).
     */
    fun draw(
        canvas: Canvas, rc: RectF, st: ResolvedStyle,
        pressed: Boolean, enter: Boolean, func: Boolean, d: Float, alpha: Float,
    ) {
        val t = st.theme
        val ks = st.keyStyle
        val base = when {
            enter -> t.accent
            func -> t.keyFuncBg
            else -> t.keyBg
        }
        val half = min(rc.width(), rc.height()) / 2f
        val r = if (ks == KeyStyle.PILL) half else min(st.radiusDp * d, half)

        val color: Int? = when {
            enter -> if (pressed) darken(t.accent) else t.accent
            pressed -> t.keyPressed
            ks == KeyStyle.FLAT || ks == KeyStyle.UNDERLINE -> null
            ks == KeyStyle.OUTLINE -> withAlpha(base, 0.35f)
            ks == KeyStyle.GLASS -> withAlpha(base, 0.45f)
            ks == KeyStyle.NEON -> withAlpha(base, 0.25f)
            else -> base
        }

        if (ks == KeyStyle.RAISED) {
            val depth = 2.6f * d
            if (pressed) {
                rc.offset(0f, depth * 0.6f)
            } else {
                fill.color = withAlpha(darken(base, 0.62f), alpha)
                canvas.drawRoundRect(rc.left, rc.top + depth, rc.right, rc.bottom + depth, r, r, fill)
            }
        }

        if (color != null) {
            if (!pressed && (ks == KeyStyle.FILLED || ks == KeyStyle.GRADIENT || ks == KeyStyle.PILL)) {
                fill.color = withAlpha(t.keyShadow, alpha)
                canvas.drawRoundRect(rc.left, rc.top + 1.2f * d, rc.right, rc.bottom + 1.2f * d, r, r, fill)
            }
            fill.color = withAlpha(color, alpha)
            canvas.drawRoundRect(rc, r, r, fill)
            if (ks == KeyStyle.GRADIENT || ks == KeyStyle.GLASS || ks == KeyStyle.RAISED) {
                glossMatrix.setScale(1f, rc.height())
                glossMatrix.postTranslate(0f, rc.top)
                glossShader.setLocalMatrix(glossMatrix)
                gloss.shader = glossShader
                gloss.alpha = if (ks == KeyStyle.RAISED) 120 else 255
                canvas.drawRoundRect(rc, r, r, gloss)
            }
        }

        when {
            ks == KeyStyle.OUTLINE && !enter -> {
                stroke.strokeWidth = 1.2f * d
                stroke.color = withAlpha(t.keyText, 0.4f)
                canvas.drawRoundRect(rc, r, r, stroke)
            }
            ks == KeyStyle.GLASS -> {
                stroke.strokeWidth = 1f * d
                stroke.color = if (t.dark) 0x59FFFFFF else withAlpha(t.keyText, 0.18f)
                canvas.drawRoundRect(rc, r, r, stroke)
            }
            ks == KeyStyle.NEON && !enter -> {
                val c = if (func) t.keyFuncText else t.accent
                stroke.strokeWidth = 4f * d
                stroke.color = withAlpha(c, if (func) 0.10f else 0.22f)
                canvas.drawRoundRect(rc, r, r, stroke)
                stroke.strokeWidth = 1.4f * d
                stroke.color = withAlpha(c, if (func) 0.55f else 1f)
                canvas.drawRoundRect(rc, r, r, stroke)
            }
            ks == KeyStyle.UNDERLINE && !enter -> {
                val w = min(rc.width() * 0.4f, 28f * d)
                val h = 2f * d
                val cy = rc.bottom - 4f * d
                fill.color = if (func) withAlpha(t.keyFuncText, 0.45f) else t.accent
                canvas.drawRoundRect(rc.centerX() - w / 2, cy - h / 2, rc.centerX() + w / 2, cy + h / 2, h / 2, h / 2, fill)
            }
            st.border && color != null -> {
                stroke.strokeWidth = 1f * d
                stroke.color = t.border
                canvas.drawRoundRect(rc, r, r, stroke)
            }
        }
    }
}
