package uz.yangiuzbkey.keyboard.keyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Vector icons drawn straight onto a canvas, designed on a 24×24 grid.
 * Strokes scale with the icon so they stay crisp on every density.
 */
enum class Icon { SHIFT, SHIFT_ON, CAPS, DELETE, ENTER, SEARCH, SEND, DONE, NEXT, GLOBE, EMOJI, GEAR, CLIPBOARD, PALETTE, CHEVRON_DOWN, CONVERT, CLOSE, TRASH, NUMBERS, BACK, KEYBOARD, PIN, UNDO, REDO, COPY, PASTE, CUT, SELECT_ALL, IMAGE, PLUS, MIC, EDIT, ONE_HAND, INCOGNITO, ARROW_UP, ARROW_DOWN, HOME, END, SELECT }

object Icons {
    private val path = Path()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tooth = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.SQUARE; strokeWidth = 3.2f }
    private val oval = RectF()

    /** Draws [icon] centred at (cx, cy) with the given [size] in pixels. */
    fun draw(c: Canvas, icon: Icon, cx: Float, cy: Float, size: Float, color: Int) {
        val s = size / 24f
        c.save()
        c.translate(cx - size / 2f, cy - size / 2f)
        c.scale(s, s)
        stroke.color = color; stroke.strokeWidth = 2f
        fill.color = color; tooth.color = color
        path.reset()
        when (icon) {
            Icon.SHIFT, Icon.SHIFT_ON, Icon.CAPS -> {
                path.moveTo(12f, 3.5f); path.lineTo(20.5f, 12.5f); path.lineTo(15.5f, 12.5f)
                path.lineTo(15.5f, if (icon == Icon.CAPS) 17f else 20f); path.lineTo(8.5f, if (icon == Icon.CAPS) 17f else 20f)
                path.lineTo(8.5f, 12.5f); path.lineTo(3.5f, 12.5f); path.close()
                c.drawPath(path, if (icon == Icon.SHIFT) stroke else fill)
                if (icon != Icon.SHIFT) c.drawPath(path, stroke)
                if (icon == Icon.CAPS) c.drawLine(8.5f, 20.5f, 15.5f, 20.5f, stroke)
            }
            Icon.DELETE -> {
                path.moveTo(8f, 5f); path.lineTo(20f, 5f); path.quadTo(21.5f, 5f, 21.5f, 6.5f)
                path.lineTo(21.5f, 17.5f); path.quadTo(21.5f, 19f, 20f, 19f); path.lineTo(8f, 19f)
                path.lineTo(2.5f, 12f); path.close()
                c.drawPath(path, stroke)
                c.drawLine(11f, 9f, 17f, 15f, stroke); c.drawLine(17f, 9f, 11f, 15f, stroke)
            }
            Icon.ENTER -> {
                path.moveTo(19.5f, 5f); path.lineTo(19.5f, 13f); path.lineTo(5f, 13f)
                c.drawPath(path, stroke)
                path.reset(); path.moveTo(9.5f, 8.5f); path.lineTo(5f, 13f); path.lineTo(9.5f, 17.5f)
                c.drawPath(path, stroke)
            }
            Icon.SEARCH -> {
                c.drawCircle(10.5f, 10.5f, 6.5f, stroke); c.drawLine(15.5f, 15.5f, 20.5f, 20.5f, stroke)
            }
            Icon.SEND -> {
                path.moveTo(3f, 20.5f); path.lineTo(21.5f, 12f); path.lineTo(3f, 3.5f); path.lineTo(5.5f, 12f); path.close()
                c.drawPath(path, stroke); c.drawLine(5.5f, 12f, 13f, 12f, stroke)
            }
            Icon.DONE -> {
                path.moveTo(4.5f, 12.5f); path.lineTo(9.5f, 17.5f); path.lineTo(19.5f, 6.5f); c.drawPath(path, stroke)
            }
            Icon.NEXT -> {
                c.drawLine(4f, 12f, 19f, 12f, stroke)
                path.moveTo(13f, 6f); path.lineTo(19f, 12f); path.lineTo(13f, 18f); c.drawPath(path, stroke)
            }
            Icon.BACK -> {
                c.drawLine(5f, 12f, 20f, 12f, stroke)
                path.moveTo(11f, 6f); path.lineTo(5f, 12f); path.lineTo(11f, 18f); c.drawPath(path, stroke)
            }
            Icon.GLOBE -> {
                stroke.strokeWidth = 1.8f
                c.drawCircle(12f, 12f, 9f, stroke)
                oval.set(8f, 3f, 16f, 21f); c.drawOval(oval, stroke)
                c.drawLine(3f, 12f, 21f, 12f, stroke)
                c.drawLine(4.5f, 7.5f, 19.5f, 7.5f, stroke); c.drawLine(4.5f, 16.5f, 19.5f, 16.5f, stroke)
            }
            Icon.EMOJI -> {
                c.drawCircle(12f, 12f, 9f, stroke)
                c.drawCircle(9f, 10f, 1.2f, fill); c.drawCircle(15f, 10f, 1.2f, fill)
                oval.set(7.5f, 9f, 16.5f, 17f); c.drawArc(oval, 25f, 130f, false, stroke)
            }
            Icon.GEAR -> {
                stroke.strokeWidth = 1.8f
                for (i in 0 until 8) {
                    c.save(); c.rotate(i * 45f, 12f, 12f)
                    c.drawLine(12f, 2.5f, 12f, 5.5f, tooth)
                    c.restore()
                }
                c.drawCircle(12f, 12f, 6.5f, stroke); c.drawCircle(12f, 12f, 2.5f, stroke)
            }
            Icon.CLIPBOARD -> {
                path.moveTo(8f, 4.5f); path.lineTo(6f, 4.5f); path.quadTo(4.5f, 4.5f, 4.5f, 6f); path.lineTo(4.5f, 20f)
                path.quadTo(4.5f, 21.5f, 6f, 21.5f); path.lineTo(18f, 21.5f); path.quadTo(19.5f, 21.5f, 19.5f, 20f)
                path.lineTo(19.5f, 6f); path.quadTo(19.5f, 4.5f, 18f, 4.5f); path.lineTo(16f, 4.5f)
                c.drawPath(path, stroke)
                oval.set(8f, 2.5f, 16f, 6.5f); c.drawRoundRect(oval, 1.5f, 1.5f, stroke)
                c.drawLine(8.5f, 11.5f, 15.5f, 11.5f, stroke); c.drawLine(8.5f, 15.5f, 13.5f, 15.5f, stroke)
            }
            Icon.PALETTE -> {
                path.moveTo(12f, 3f); path.cubicTo(6.5f, 3f, 3f, 7f, 3f, 12f); path.cubicTo(3f, 17f, 7f, 21f, 11.5f, 21f)
                path.cubicTo(13f, 21f, 13.5f, 19.8f, 12.8f, 18.8f); path.cubicTo(12f, 17.6f, 12.8f, 16f, 14.3f, 16f)
                path.lineTo(16.5f, 16f); path.cubicTo(19f, 16f, 21f, 14f, 21f, 11.5f); path.cubicTo(21f, 6.8f, 17f, 3f, 12f, 3f); path.close()
                c.drawPath(path, stroke)
                c.drawCircle(7.5f, 11.5f, 1.3f, fill); c.drawCircle(10f, 7.5f, 1.3f, fill)
                c.drawCircle(14.5f, 7.5f, 1.3f, fill); c.drawCircle(17f, 11f, 1.3f, fill)
            }
            Icon.CHEVRON_DOWN -> {
                path.moveTo(6f, 9f); path.lineTo(12f, 15f); path.lineTo(18f, 9f); c.drawPath(path, stroke)
            }
            Icon.CONVERT -> {
                path.moveTo(4f, 8f); path.lineTo(19f, 8f); path.moveTo(15f, 4f); path.lineTo(19f, 8f); path.lineTo(15f, 12f)
                path.moveTo(20f, 16f); path.lineTo(5f, 16f); path.moveTo(9f, 12f); path.lineTo(5f, 16f); path.lineTo(9f, 20f)
                c.drawPath(path, stroke)
            }
            Icon.CLOSE -> {
                c.drawLine(6f, 6f, 18f, 18f, stroke); c.drawLine(18f, 6f, 6f, 18f, stroke)
            }
            Icon.TRASH -> {
                c.drawLine(4f, 6.5f, 20f, 6.5f, stroke)
                path.moveTo(9f, 6.5f); path.lineTo(9f, 4f); path.lineTo(15f, 4f); path.lineTo(15f, 6.5f)
                path.moveTo(6f, 6.5f); path.lineTo(7f, 20.5f); path.lineTo(17f, 20.5f); path.lineTo(18f, 6.5f)
                c.drawPath(path, stroke)
                c.drawLine(10f, 10.5f, 10f, 16.5f, stroke); c.drawLine(14f, 10.5f, 14f, 16.5f, stroke)
            }
            Icon.NUMBERS -> {
                stroke.strokeWidth = 1.8f
                oval.set(2.5f, 5f, 21.5f, 19f); c.drawRoundRect(oval, 2.5f, 2.5f, stroke)
                for (i in 0 until 4) c.drawCircle(6.5f + i * 3.67f, 10f, 1.1f, fill)
                c.drawLine(7f, 15f, 17f, 15f, stroke)
            }
            Icon.KEYBOARD -> {
                stroke.strokeWidth = 1.8f
                oval.set(2.5f, 5f, 21.5f, 19f); c.drawRoundRect(oval, 2.5f, 2.5f, stroke)
                for (r in 0 until 2) for (i in 0 until 5) c.drawCircle(5.8f + i * 3.1f, 9f + r * 3f, 0.95f, fill)
                c.drawLine(8f, 15.8f, 16f, 15.8f, stroke)
            }
            Icon.PIN -> {
                path.moveTo(9f, 3.5f); path.lineTo(15f, 3.5f); path.lineTo(14f, 10f); path.lineTo(17.5f, 14f)
                path.lineTo(6.5f, 14f); path.lineTo(10f, 10f); path.close()
                c.drawPath(path, stroke); c.drawLine(12f, 14f, 12f, 21f, stroke)
            }
            Icon.UNDO, Icon.REDO -> {
                if (icon == Icon.REDO) { c.scale(-1f, 1f, 12f, 12f) }
                path.moveTo(9f, 5f); path.lineTo(4.5f, 9.5f); path.lineTo(9f, 14f); c.drawPath(path, stroke)
                path.reset(); path.moveTo(4.5f, 9.5f); path.lineTo(14.5f, 9.5f)
                path.cubicTo(17.8f, 9.5f, 20f, 11.8f, 20f, 14.75f); path.cubicTo(20f, 17.7f, 17.8f, 20f, 14.5f, 20f)
                path.lineTo(10f, 20f); c.drawPath(path, stroke)
            }
            Icon.COPY -> {
                oval.set(8.5f, 8.5f, 20.5f, 20.5f); c.drawRoundRect(oval, 2f, 2f, stroke)
                path.moveTo(5.5f, 15.5f); path.lineTo(5f, 15.5f); path.quadTo(3.5f, 15.5f, 3.5f, 14f); path.lineTo(3.5f, 5f)
                path.quadTo(3.5f, 3.5f, 5f, 3.5f); path.lineTo(14f, 3.5f); path.quadTo(15.5f, 3.5f, 15.5f, 5f); path.lineTo(15.5f, 5.5f)
                c.drawPath(path, stroke)
            }
            Icon.PASTE -> {
                path.moveTo(8f, 4.5f); path.lineTo(6f, 4.5f); path.quadTo(4.5f, 4.5f, 4.5f, 6f); path.lineTo(4.5f, 20f)
                path.quadTo(4.5f, 21.5f, 6f, 21.5f); path.lineTo(18f, 21.5f); path.quadTo(19.5f, 21.5f, 19.5f, 20f)
                path.lineTo(19.5f, 6f); path.quadTo(19.5f, 4.5f, 18f, 4.5f); path.lineTo(16f, 4.5f)
                c.drawPath(path, stroke)
                oval.set(8f, 2.5f, 16f, 6.5f); c.drawRoundRect(oval, 1.5f, 1.5f, stroke)
                path.reset(); path.moveTo(12f, 9.5f); path.lineTo(12f, 17.5f); path.moveTo(8.5f, 14f); path.lineTo(12f, 17.5f); path.lineTo(15.5f, 14f)
                c.drawPath(path, stroke)
            }
            Icon.CUT -> {
                c.drawCircle(7f, 17.5f, 3f, stroke); c.drawCircle(17f, 17.5f, 3f, stroke)
                c.drawLine(9f, 15.3f, 18f, 3.5f, stroke); c.drawLine(15f, 15.3f, 6f, 3.5f, stroke)
            }
            Icon.SELECT_ALL -> {
                stroke.strokeWidth = 1.8f
                stroke.pathEffect = android.graphics.DashPathEffect(floatArrayOf(2.5f, 2.5f), 0f)
                oval.set(3.5f, 3.5f, 20.5f, 20.5f); c.drawRoundRect(oval, 2f, 2f, stroke)
                stroke.pathEffect = null
                oval.set(8f, 8f, 16f, 16f); c.drawRect(oval, fill)
            }
            Icon.IMAGE -> {
                oval.set(3.5f, 4.5f, 20.5f, 19.5f); c.drawRoundRect(oval, 2.5f, 2.5f, stroke)
                c.drawCircle(9f, 9.5f, 1.6f, fill)
                path.moveTo(4f, 17f); path.lineTo(9.5f, 12.5f); path.lineTo(13f, 15.5f); path.lineTo(15.5f, 13.5f); path.lineTo(20f, 17.5f)
                c.drawPath(path, stroke)
            }
            Icon.PLUS -> {
                c.drawLine(12f, 5f, 12f, 19f, stroke); c.drawLine(5f, 12f, 19f, 12f, stroke)
            }
            Icon.MIC -> {
                oval.set(9f, 3f, 15f, 15f); c.drawRoundRect(oval, 3f, 3f, stroke)
                oval.set(5.5f, 6f, 18.5f, 18f); c.drawArc(oval, 0f, 180f, false, stroke)
                c.drawLine(12f, 18f, 12f, 21f, stroke); c.drawLine(9f, 21f, 15f, 21f, stroke)
            }
            Icon.EDIT -> {
                path.moveTo(4f, 20f); path.lineTo(4.5f, 16f); path.lineTo(15.5f, 5f); path.lineTo(19f, 8.5f)
                path.lineTo(8f, 19.5f); path.close(); c.drawPath(path, stroke)
                c.drawLine(13f, 7.5f, 16.5f, 11f, stroke)
            }
            Icon.ONE_HAND -> {
                oval.set(6f, 2.5f, 18f, 21.5f); c.drawRoundRect(oval, 2.5f, 2.5f, stroke)
                oval.set(8.5f, 12f, 15.5f, 18f); c.drawRect(oval, fill)
            }
            Icon.INCOGNITO -> {
                path.moveTo(3f, 11f); path.lineTo(21f, 11f); path.moveTo(6f, 11f); path.lineTo(8f, 4.5f)
                path.lineTo(16f, 4.5f); path.lineTo(18f, 11f); c.drawPath(path, stroke)
                c.drawCircle(7.5f, 16.5f, 3f, stroke); c.drawCircle(16.5f, 16.5f, 3f, stroke)
                c.drawLine(10.5f, 16f, 13.5f, 16f, stroke)
            }
            Icon.ARROW_UP, Icon.ARROW_DOWN -> {
                if (icon == Icon.ARROW_DOWN) c.scale(1f, -1f, 12f, 12f)
                c.drawLine(12f, 5f, 12f, 20f, stroke)
                path.moveTo(6f, 11f); path.lineTo(12f, 5f); path.lineTo(18f, 11f); c.drawPath(path, stroke)
            }
            Icon.HOME, Icon.END -> {
                if (icon == Icon.END) c.scale(-1f, 1f, 12f, 12f)
                c.drawLine(4.5f, 5f, 4.5f, 19f, stroke); c.drawLine(8f, 12f, 20f, 12f, stroke)
                path.moveTo(13f, 7f); path.lineTo(8f, 12f); path.lineTo(13f, 17f); c.drawPath(path, stroke)
            }
            Icon.SELECT -> {
                c.drawLine(12f, 4f, 12f, 20f, stroke)
                c.drawLine(9f, 4f, 15f, 4f, stroke); c.drawLine(9f, 20f, 15f, 20f, stroke)
                stroke.strokeWidth = 1.6f
                c.drawLine(3f, 12f, 8f, 12f, stroke); c.drawLine(16f, 12f, 21f, 12f, stroke)
            }
        }
        c.restore()
    }
}
