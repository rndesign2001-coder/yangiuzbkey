package uz.yangiuzbkey.keyboard.settings

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import uz.yangiuzbkey.keyboard.BuildConfig
import uz.yangiuzbkey.keyboard.R
import uz.yangiuzbkey.keyboard.ime.SuggestionEngine
import uz.yangiuzbkey.keyboard.util.Prefs

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = FrameLayout(this).apply { id = R.id.settings_container }
        setScreen(getString(R.string.settings_title), container)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.settings_container, SettingsFragment()).commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            findPreference<Preference>("version")?.summary = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            val ctx = requireContext()
            when (preference.key) {
                "open_themes" -> startActivity(Intent(ctx, ThemesActivity::class.java))
                "open_languages" -> startActivity(Intent(ctx, LanguagesActivity::class.java))
                "clear_learned" -> MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.pref_clear_learned)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        SuggestionEngine(ctx.applicationContext).clearLearned()
                        // Tells a running keyboard to drop its in-memory copy too.
                        Prefs(ctx).sp.edit().putLong("learned_reset", System.currentTimeMillis()).apply()
                        Toast.makeText(ctx, R.string.pref_clear_learned_done, Toast.LENGTH_SHORT).show()
                    }
                    .show()
                "privacy" -> MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.pref_privacy)
                    .setMessage(R.string.privacy_note)
                    .setPositiveButton(R.string.ok, null)
                    .show()
                else -> return super.onPreferenceTreeClick(preference)
            }
            return true
        }
    }
}
