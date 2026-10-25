package uz.yangiuzbkey.keyboard.settings

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import uz.yangiuzbkey.keyboard.R

/** Onboarding: enable the IME, select it, try it, and jump to the other screens. */
class MainActivity : AppCompatActivity() {

    private class Step(val n: String, val card: MaterialCardView, val badge: TextView, val button: MaterialButton, val done: TextView)

    private lateinit var step1: Step
    private lateinit var step2: Step
    private lateinit var tryBox: TextInputLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = vertical(16)

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.app_logo)
            contentDescription = getString(R.string.app_name)
            elevation = dp(6).toFloat()
        }
        col.addView(centered(logo).apply { setPadding(0, dp(8), 0, dp(12)) })
        logo.layoutParams = LinearLayout.LayoutParams(dp(96), dp(96))
        col.addView(text(getString(R.string.welcome_title), 24f, true).apply { gravity = Gravity.CENTER })
        col.addView(text(getString(R.string.welcome_text), 15f, attr = com.google.android.material.R.attr.colorOnSurfaceVariant).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(20))
        })

        step1 = step(col, "1", R.string.step1_title, R.string.step1_text, R.string.step1_button) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        step2 = step(col, "2", R.string.step2_title, R.string.step2_text, R.string.step2_button) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }

        tryBox = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.try_hint)
            addView(TextInputEditText(context).apply { minLines = 2 })
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(4); bottomMargin = dp(16) }
        }
        col.addView(tryBox)

        val buttons = LinearLayout(this)
        fun nav(label: Int, target: Class<*>) {
            buttons.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = getString(label)
                maxLines = 1
                setOnClickListener { startActivity(Intent(this@MainActivity, target)) }
            }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        }
        nav(R.string.open_themes, ThemesActivity::class.java)
        nav(R.string.open_languages, LanguagesActivity::class.java)
        nav(R.string.open_settings, SettingsActivity::class.java)
        col.addView(buttons)

        col.addView(text("🔒  " + getString(R.string.privacy_note), 13f, attr = com.google.android.material.R.attr.colorOnSurfaceVariant).apply {
            setPadding(dp(8), dp(24), dp(8), dp(8))
            gravity = Gravity.CENTER
        })
        col.addView(text(getString(R.string.pref_developer) + ": " + getString(R.string.developer_name), 13f, true,
            com.google.android.material.R.attr.colorOnSurfaceVariant).apply {
            setPadding(dp(8), dp(4), dp(8), dp(16))
            gravity = Gravity.CENTER
        })

        setScreen(getString(R.string.app_name), ScrollView(this).apply { addView(col) }, showBack = false)
    }

    private fun step(parent: LinearLayout, n: String, title: Int, body: Int, action: Int, onClick: () -> Unit): Step {
        val card = card()
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val badge = text(n, 16f, true).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(14) }
        }
        val texts = vertical()
        texts.addView(text(getString(title), 16f, true))
        texts.addView(text(getString(body), 14f, attr = com.google.android.material.R.attr.colorOnSurfaceVariant))
        val done = text(getString(R.string.step_done), 14f, true, androidx.appcompat.R.attr.colorPrimary)
        texts.addView(done)
        val button = MaterialButton(this).apply {
            text = getString(action)
            setOnClickListener { onClick() }
        }
        row.addView(badge)
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(button, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(8) })
        card.addView(row)
        parent.addView(card)
        return Step(n, card, badge, button, done)
    }

    private fun isEnabled(): Boolean {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }

    private fun isSelected(): Boolean {
        val id = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
        return id.substringBefore('/') == packageName
    }

    private fun refresh() {
        val enabled = isEnabled()
        val selected = enabled && isSelected()
        render(step1, enabled, active = !enabled)
        render(step2, selected, active = enabled && !selected)
    }

    private fun render(s: Step, done: Boolean, active: Boolean) {
        val primary = s.card.themeColor(androidx.appcompat.R.attr.colorPrimary)
        val onPrimary = s.card.themeColor(com.google.android.material.R.attr.colorOnPrimary)
        val muted = s.card.themeColor(com.google.android.material.R.attr.colorSurfaceVariant)
        val onMuted = s.card.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        s.badge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (done || active) primary else muted)
        }
        s.badge.setTextColor(if (done || active) onPrimary else onMuted)
        s.badge.text = if (done) "✓" else s.n
        s.done.visibility = if (done) TextView.VISIBLE else TextView.GONE
        s.button.visibility = if (done) TextView.GONE else TextView.VISIBLE
        s.button.isEnabled = active
        s.card.strokeWidth = if (active) dp(2) else 0
        s.card.strokeColor = primary
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The IME picker is a dialog: it does not pause us, only steals focus.
        if (hasFocus) refresh()
    }
}
