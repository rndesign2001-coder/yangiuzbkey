package uz.yangiuzbkey.keyboard.settings

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

/** Small helpers shared by the settings screens; all UI is built in code. */
internal fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

internal fun View.themeColor(attr: Int): Int = MaterialColors.getColor(this, attr)

internal const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
internal const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

internal fun Context.vertical(padding: Int = 0): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    if (padding > 0) setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
}

internal fun Context.text(value: CharSequence, sizeSp: Float = 15f, bold: Boolean = false, attr: Int = com.google.android.material.R.attr.colorOnSurface): TextView =
    TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setTextColor(themeColor(attr))
    }

internal fun Context.sectionTitle(value: CharSequence): TextView =
    text(value, 14f, true, androidx.appcompat.R.attr.colorPrimary).apply {
        setPadding(dp(4), dp(20), dp(4), dp(8))
    }

internal fun Context.card(): MaterialCardView = MaterialCardView(this).apply {
    radius = dp(20).toFloat()
    cardElevation = 0f
    strokeWidth = 0
    setCardBackgroundColor(themeColor(com.google.android.material.R.attr.colorSurfaceContainer))
    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) }
}

/** Builds "toolbar + content" with edge-to-edge insets handled (targetSdk 35+ forces edge-to-edge). */
internal fun AppCompatActivity.setScreen(title: CharSequence, content: View, showBack: Boolean = true) {
    enableEdgeToEdge()
    val root = vertical()
    val toolbar = MaterialToolbar(this).apply { this.title = title }
    root.addView(toolbar, LinearLayout.LayoutParams(MATCH, WRAP))
    root.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))
    root.setBackgroundColor(root.themeColor(com.google.android.material.R.attr.colorSurface))
    setContentView(root)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(showBack)
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
        WindowInsetsCompat.CONSUMED
    }
}

internal fun Context.centered(view: View): LinearLayout = LinearLayout(this).apply {
    gravity = Gravity.CENTER
    addView(view)
}
