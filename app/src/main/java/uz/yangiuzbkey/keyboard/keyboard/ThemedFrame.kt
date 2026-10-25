package uz.yangiuzbkey.keyboard.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import uz.yangiuzbkey.keyboard.theme.ResolvedStyle

/** Container that paints the keyboard background (gradient or user photo). */
class ThemedFrame @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : AnchorFrame(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dimPaint = Paint()
    private val matrix = Matrix()
    private var gradientKey = 0L

    var style: ResolvedStyle? = null
        set(v) { field = v; gradientKey = 0L; invalidate() }

    init { setWillNotDraw(false); anchored = false }

    override fun onDraw(canvas: Canvas) {
        val st = style ?: return
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val bmp = st.background
        if (bmp != null && !bmp.isRecycled) {
            val scale = maxOf(w / bmp.width, h / bmp.height)
            matrix.reset()
            matrix.setScale(scale, scale)
            matrix.postTranslate((w - bmp.width * scale) / 2f, (h - bmp.height * scale) / 2f)
            paint.shader = null
            canvas.drawBitmap(bmp, matrix, paint)
            dimPaint.color = (((st.dim.coerceIn(0f, 1f)) * 255).toInt() shl 24)
            canvas.drawRect(0f, 0f, w, h, dimPaint)
        } else {
            val t = st.theme
            val key = (t.bg.toLong() shl 32) xor t.bg2.toLong() xor (height.toLong() * 31)
            if (key != gradientKey || paint.shader == null) {
                paint.shader = LinearGradient(0f, 0f, 0f, h, t.bg, t.bg2, Shader.TileMode.CLAMP)
                gradientKey = key
            }
            canvas.drawRect(0f, 0f, w, h, paint)
        }
    }
}
