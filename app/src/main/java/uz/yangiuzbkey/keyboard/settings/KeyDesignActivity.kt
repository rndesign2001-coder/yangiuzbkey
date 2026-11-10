package uz.yangiuzbkey.keyboard.settings

import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import uz.yangiuzbkey.keyboard.R
import uz.yangiuzbkey.keyboard.keyboard.FieldVariant
import uz.yangiuzbkey.keyboard.keyboard.KeyStylePreview
import uz.yangiuzbkey.keyboard.keyboard.KeyboardView
import uz.yangiuzbkey.keyboard.keyboard.Languages
import uz.yangiuzbkey.keyboard.keyboard.LayoutBuilder
import uz.yangiuzbkey.keyboard.keyboard.ThemedFrame
import uz.yangiuzbkey.keyboard.theme.ResolvedStyle
import uz.yangiuzbkey.keyboard.theme.ThemeManager
import uz.yangiuzbkey.keyboard.util.Prefs

/** Key design picker: style gallery, shape and size, with a live keyboard preview. */
class KeyDesignActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private class StyleCell(val id: String, val card: MaterialCardView, val preview: KeyStylePreview)

    private lateinit var prefs: Prefs
    private lateinit var frame: ThemedFrame
    private lateinit var kv: KeyboardView
    private val cells = mutableListOf<StyleCell>()
    private val controls = mutableListOf<() -> Unit>()
    private var syncing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val cfg = resources.configuration
        val landscape = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE

        val root = vertical()
        frame = ThemedFrame(this)
        kv = KeyboardView(this).apply {
            heightPercent = 80
            setOnTouchListener { _, _ -> true }
        }
        frame.addView(kv)
        frame.setPadding(0, dp(6), 0, dp(6))

        val col = vertical(16)
        // In landscape a pinned preview would leave no room for the options, so it scrolls with them.
        if (landscape) col.addView(frame, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) })
        else root.addView(frame, LinearLayout.LayoutParams(MATCH, WRAP))

        col.addView(sectionTitle(getString(R.string.key_design_styles)))
        buildStyles(col, ((cfg.screenWidthDp - 32) / 108).coerceIn(2, 5))

        col.addView(sectionTitle(getString(R.string.theme_key_shape)))
        val shape = card()
        val shapeCol = vertical(16)
        slider(shapeCol, R.string.theme_key_radius, "key_radius", 8, 0f, 24f, 1f)
        slider(shapeCol, R.string.theme_key_gap, "key_gap", 3, 0f, 8f, 1f)
        slider(shapeCol, R.string.theme_font_size, "font_scale", 100, 70f, 140f, 5f, "%")
        switch(shapeCol, R.string.theme_bold, "key_bold")
        switch(shapeCol, R.string.theme_key_border, "key_border")
        shape.addView(shapeCol)
        col.addView(shape)

        col.addView(MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = getString(R.string.theme_reset)
            setOnClickListener { reset() }
        })

        root.addView(ScrollView(this).apply { addView(col) }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        setScreen(getString(R.string.key_design_title), root)
        refresh()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onStart() {
        super.onStart()
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
        refresh()
    }

    override fun onStop() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) = refresh()

    private fun label(id: String): Int = when (id) {
        "outline" -> R.string.theme_style_outline
        "flat" -> R.string.theme_style_flat
        "raised" -> R.string.theme_style_raised
        "gradient" -> R.string.theme_style_gradient
        "pill" -> R.string.theme_style_pill
        "glass" -> R.string.theme_style_glass
        "neon" -> R.string.theme_style_neon
        "underline" -> R.string.theme_style_underline
        else -> R.string.theme_style_filled
    }

    private fun buildStyles(col: LinearLayout, columns: Int) {
        ThemeManager.keyStyleIds.chunked(columns).forEach { chunk ->
            val row = LinearLayout(this)
            chunk.forEach { id ->
                val preview = KeyStylePreview(this)
                val name = text(getString(label(id)), 13f).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setPadding(0, dp(6), 0, 0)
                }
                val inner = vertical(8)
                inner.addView(preview, LinearLayout.LayoutParams(MATCH, dp(54)))
                inner.addView(name, LinearLayout.LayoutParams(MATCH, WRAP))
                val card = MaterialCardView(this).apply {
                    radius = dp(16).toFloat()
                    cardElevation = 0f
                    isClickable = true
                    isFocusable = true
                    contentDescription = getString(label(id))
                    addView(inner)
                    setOnClickListener { prefs.sp.edit().putString("key_style", id).apply() }
                }
                cells += StyleCell(id, card, preview)
                row.addView(card, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            repeat(columns - chunk.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
            }
            col.addView(row)
        }
    }

    private fun slider(col: LinearLayout, label: Int, key: String, def: Int, from: Float, to: Float, step: Float, unit: String = "") {
        val title = text("", 14f)
        val s = Slider(this).apply {
            valueFrom = from; valueTo = to; stepSize = step
            setLabelFormatter { "${it.toInt()}$unit" }
        }
        fun show(v: Int) { title.text = "${getString(label)}: $v$unit" }
        s.addOnChangeListener { _, v, fromUser ->
            show(v.toInt())
            if (fromUser) prefs.sp.edit().putInt(key, v.toInt()).apply()
        }
        controls += {
            val v = prefs.sp.getInt(key, def).coerceIn(from.toInt(), to.toInt())
            val snapped = from + Math.round((v - from) / step) * step
            if (s.value != snapped) s.value = snapped
            show(v)
        }
        col.addView(title)
        col.addView(s, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun switch(col: LinearLayout, label: Int, key: String): MaterialSwitch {
        val sw = MaterialSwitch(this).apply { text = getString(label) }
        sw.setOnCheckedChangeListener { _, checked ->
            if (!syncing) prefs.sp.edit().putBoolean(key, checked).apply()
        }
        controls += { if (sw.isChecked != prefs.sp.getBoolean(key, false)) sw.isChecked = prefs.sp.getBoolean(key, false) }
        col.addView(sw, LinearLayout.LayoutParams(MATCH, WRAP))
        return sw
    }

    private fun reset() {
        prefs.sp.edit()
            .remove("key_style").remove("key_radius").remove("key_gap")
            .remove("font_scale").remove("key_bold").remove("key_border")
            .apply()
    }

    private fun refresh() {
        if (!::kv.isInitialized) return
        val st = ThemeManager.resolve(this, prefs)
        frame.style = st
        kv.setStyle(st)
        kv.setKeyboard(LayoutBuilder.alpha(Languages.byId(prefs.currentLanguage), prefs, prefs.languages.size > 1, FieldVariant.NORMAL))
        val current = prefs.sp.getString("key_style", "filled")
        val selected = if (current in ThemeManager.keyStyleIds) current else "filled"
        val accent = frame.themeColor(androidx.appcompat.R.attr.colorPrimary)
        cells.forEach { c ->
            c.preview.style = ResolvedStyle(
                st.theme, ThemeManager.keyStyleOf(c.id), st.radiusDp, st.gapDp,
                st.fontScale, st.bold, st.border, null, 0f,
            )
            c.card.strokeColor = accent
            c.card.strokeWidth = if (c.id == selected) dp(3) else 0
        }
        syncing = true
        controls.forEach { it() }
        syncing = false
    }
}
