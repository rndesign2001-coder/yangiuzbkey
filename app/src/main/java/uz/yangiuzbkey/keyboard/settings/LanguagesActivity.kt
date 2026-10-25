package uz.yangiuzbkey.keyboard.settings

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import uz.yangiuzbkey.keyboard.R
import uz.yangiuzbkey.keyboard.keyboard.Languages
import uz.yangiuzbkey.keyboard.util.Prefs

/** Enable keyboard languages and set the order the 🌐 key cycles through. */
class LanguagesActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout
    private var enabled = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        enabled = prefs.languages.filter { id -> Languages.all.any { it.id == id } }.toMutableList()

        val col = vertical(16)
        col.addView(text(getString(R.string.languages_hint), 14f, attr = com.google.android.material.R.attr.colorOnSurfaceVariant).apply {
            setPadding(dp(4), 0, dp(4), dp(12))
        })
        list = vertical()
        col.addView(list)
        render()
        setScreen(getString(R.string.languages_title), ScrollView(this).apply { addView(col) })
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun render() {
        list.removeAllViews()
        val ordered = enabled.mapNotNull { id -> Languages.all.firstOrNull { it.id == id } } +
            Languages.all.filter { it.id !in enabled }
        ordered.forEach { lang ->
            val on = lang.id in enabled
            val pos = enabled.indexOf(lang.id)
            val card = card().apply { layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(8) } }
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(4), dp(4), dp(4))
            }
            val check = MaterialCheckBox(this).apply {
                text = "${lang.name}  ·  ${lang.short}"
                textSize = 16f
                isChecked = on
                setOnCheckedChangeListener { b, checked ->
                    if (checked) enabled += lang.id
                    else if (enabled.size <= 1) {
                        b.isChecked = true
                        Toast.makeText(this@LanguagesActivity, R.string.languages_need_one, Toast.LENGTH_SHORT).show()
                        return@setOnCheckedChangeListener
                    } else enabled -= lang.id
                    save()
                    list.post { render() }
                }
            }
            row.addView(check, LinearLayout.LayoutParams(0, WRAP, 1f))
            if (on) {
                row.addView(arrow("↑", R.string.move_up, pos > 0) { move(pos, pos - 1) })
                row.addView(arrow("↓", R.string.move_down, pos < enabled.size - 1) { move(pos, pos + 1) })
            }
            card.addView(row)
            list.addView(card)
        }
    }

    private fun arrow(symbol: String, desc: Int, active: Boolean, onClick: () -> Unit) =
        MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = symbol
            textSize = 18f
            contentDescription = getString(desc)
            isEnabled = active
            minWidth = dp(48)
            minimumWidth = dp(48)
            setOnClickListener { onClick() }
        }

    private fun move(from: Int, to: Int) {
        enabled.add(to, enabled.removeAt(from))
        save()
        render()
    }

    private fun save() {
        prefs.languages = enabled.toList()
        if (prefs.currentLanguage !in enabled) prefs.currentLanguage = enabled.first()
    }
}
