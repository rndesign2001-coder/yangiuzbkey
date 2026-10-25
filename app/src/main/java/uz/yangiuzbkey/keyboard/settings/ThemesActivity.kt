package uz.yangiuzbkey.keyboard.settings

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import uz.yangiuzbkey.keyboard.R
import uz.yangiuzbkey.keyboard.keyboard.FieldVariant
import uz.yangiuzbkey.keyboard.keyboard.KeyboardView
import uz.yangiuzbkey.keyboard.keyboard.Languages
import uz.yangiuzbkey.keyboard.keyboard.LayoutBuilder
import uz.yangiuzbkey.keyboard.keyboard.ThemedFrame
import uz.yangiuzbkey.keyboard.theme.ThemeManager
import uz.yangiuzbkey.keyboard.util.Prefs
import java.io.FileOutputStream
import kotlin.math.max

/** Theme picker with a live keyboard preview pinned at the top. */
class ThemesActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var frame: ThemedFrame
    private lateinit var kv: KeyboardView
    private val swatches = mutableListOf<Pair<String, MaterialCardView>>()
    private lateinit var removeBg: MaterialButton
    private lateinit var bgSwitch: MaterialSwitch
    private val controls = mutableListOf<() -> Unit>()
    private var syncing = false

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importBackground(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = vertical()
        frame = ThemedFrame(this)
        kv = KeyboardView(this).apply {
            heightPercent = 80
            setOnTouchListener { _, _ -> true }
        }
        frame.addView(kv)
        frame.setPadding(0, dp(6), 0, dp(6))
        root.addView(frame, LinearLayout.LayoutParams(MATCH, WRAP))

        val col = vertical(16)
        col.addView(sectionTitle(getString(R.string.theme_presets)))
        buildPresets(col)

        col.addView(sectionTitle(getString(R.string.theme_custom_bg)))
        buildBackground(col)

        col.addView(sectionTitle(getString(R.string.theme_key_style)))
        buildKeyStyle(col)

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
        }, LinearLayout.LayoutParams(MATCH, WRAP))

        root.addView(ScrollView(this).apply { addView(col) }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        setScreen(getString(R.string.themes_title), root)
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

    private fun refresh() {
        val st = ThemeManager.resolve(this, prefs)
        frame.style = st
        kv.setStyle(st)
        val lang = Languages.byId(prefs.currentLanguage)
        kv.setKeyboard(LayoutBuilder.alpha(lang, prefs, prefs.languages.size > 1, FieldVariant.NORMAL))
        val current = prefs.sp.getString("theme", "auto")
        val accent = frame.themeColor(androidx.appcompat.R.attr.colorPrimary)
        for ((id, card) in swatches) {
            card.strokeColor = accent
            card.strokeWidth = if (id == current) dp(3) else 0
        }
        val hasFile = ThemeManager.backgroundFile(this).exists()
        removeBg.visibility = if (hasFile) View.VISIBLE else View.GONE
        bgSwitch.isEnabled = hasFile
        syncing = true
        controls.forEach { it() }
        syncing = false
    }

    private fun buildPresets(col: LinearLayout) {
        val ids = ThemeManager.ids()
        ids.chunked(3).forEach { chunk ->
            val row = LinearLayout(this)
            chunk.forEach { id -> row.addView(swatch(id), LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
            repeat(3 - chunk.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dp(4), 0, dp(4), 0) }) }
            col.addView(row)
        }
    }

    private fun swatch(id: String): MaterialCardView {
        val t = ThemeManager.theme(this, id)
        val card = MaterialCardView(this).apply {
            radius = dp(16).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(t.bg)
            isClickable = true
            setOnClickListener { prefs.sp.edit().putString("theme", id).apply() }
        }
        val inner = vertical().apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(10))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(t.bg, t.bg2))
        }
        val keys = LinearLayout(this).apply { gravity = Gravity.CENTER }
        repeat(3) { i ->
            keys.addView(View(this).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(4).toFloat()
                    setColor(if (i == 2) t.accent else t.keyBg)
                }
            }, LinearLayout.LayoutParams(dp(18), dp(20)).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        inner.addView(keys)
        inner.addView(text(ThemeManager.displayName(this, id), 12f, true).apply {
            setTextColor(if (t.dark) 0xFFFFFFFF.toInt() else 0xFF1B1B1F.toInt())
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(0, dp(8), 0, 0)
        })
        card.addView(inner)
        swatches += id to card
        return card
    }

    private fun buildBackground(col: LinearLayout) {
        val c = card()
        val inner = vertical(16)
        val buttons = LinearLayout(this)
        buttons.addView(MaterialButton(this).apply {
            text = getString(R.string.theme_pick_image)
            setOnClickListener { pickImage.launch("image/*") }
        }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(8) })
        removeBg = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.theme_bg_remove)
            setOnClickListener {
                ThemeManager.backgroundFile(this@ThemesActivity).delete()
                ThemeManager.invalidateBackground()
                prefs.sp.edit().putBoolean("bg_image", false).apply()
                refresh()
            }
        }
        buttons.addView(removeBg, LinearLayout.LayoutParams(0, WRAP, 1f))
        inner.addView(buttons)
        bgSwitch = switch(inner, R.string.theme_custom_bg, "bg_image")
        slider(inner, R.string.theme_bg_dim, "bg_dim", 30, 0f, 90f, 5f, "%")
        c.addView(inner)
        col.addView(c)
    }

    private fun buildKeyStyle(col: LinearLayout) {
        val ids = listOf("filled", "outline", "flat")
        val labels = listOf(R.string.theme_style_filled, R.string.theme_style_outline, R.string.theme_style_flat)
        val group = MaterialButtonToggleGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
        val buttonIds = ids.map { View.generateViewId() }
        ids.indices.forEach { i ->
            group.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = buttonIds[i]
                text = getString(labels[i])
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && !syncing) {
                val v = ids[buttonIds.indexOf(checkedId)]
                if (prefs.sp.getString("key_style", "filled") != v) prefs.sp.edit().putString("key_style", v).apply()
            }
        }
        controls += {
            val i = ids.indexOf(prefs.sp.getString("key_style", "filled")).coerceAtLeast(0)
            if (group.checkedButtonId != buttonIds[i]) group.check(buttonIds[i])
        }
        col.addView(group, LinearLayout.LayoutParams(MATCH, WRAP))
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
            .remove("theme").remove("key_style").remove("key_radius").remove("key_gap")
            .remove("font_scale").remove("key_bold").remove("key_border").remove("bg_dim").remove("bg_image")
            .apply()
    }

    private fun importBackground(uri: Uri) {
        val app = applicationContext
        Thread {
            val ok = try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1440) sample *= 2
                val bmp: Bitmap? = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
                if (bmp == null) false else {
                    val f = ThemeManager.backgroundFile(app)
                    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                    bmp.recycle()
                    true
                }
            } catch (_: Throwable) { false }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (ok) {
                    ThemeManager.invalidateBackground()
                    prefs.sp.edit().putBoolean("bg_image", true).apply()
                    refresh()
                } else {
                    Toast.makeText(this, R.string.theme_bg_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
