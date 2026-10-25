package uz.yangiuzbkey.keyboard.keyboard

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * FrameLayout whose size follows its first child. The other children are
 * stretched over it and never make it grow, so a MATCH_PARENT overlay or panel
 * cannot blow the IME window up to full screen height.
 */
open class AnchorFrame @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    var anchored = true
        set(v) { field = v; requestLayout() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val anchor = if (childCount > 0) getChildAt(0) else null
        if (!anchored || anchor == null) { super.onMeasure(widthMeasureSpec, heightMeasureSpec); return }
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val inner = maxOf(0, width - paddingLeft - paddingRight)
        val hSpec = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        } else {
            MeasureSpec.makeMeasureSpec(maxOf(0, MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom), MeasureSpec.AT_MOST)
        }
        anchor.measure(MeasureSpec.makeMeasureSpec(inner, MeasureSpec.EXACTLY), hSpec)
        val h = anchor.measuredHeight
        for (i in 1 until childCount) {
            val c = getChildAt(i)
            if (c.visibility != GONE) {
                c.measure(MeasureSpec.makeMeasureSpec(inner, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
            }
        }
        setMeasuredDimension(width, h + paddingTop + paddingBottom)
    }
}
