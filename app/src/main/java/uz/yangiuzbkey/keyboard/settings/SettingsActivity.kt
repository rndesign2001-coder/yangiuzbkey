package uz.yangiuzbkey.keyboard.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import uz.yangiuzbkey.keyboard.BuildConfig
import uz.yangiuzbkey.keyboard.R
import uz.yangiuzbkey.keyboard.ime.SuggestionEngine
import uz.yangiuzbkey.keyboard.util.Prefs
import java.io.File

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
        private val exportDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) exportTo(uri)
        }
        private val importDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importFrom(uri)
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            findPreference<Preference>("version")?.summary = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            val ctx = requireContext()
            when (preference.key) {
                "open_themes" -> startActivity(Intent(ctx, ThemesActivity::class.java))
                "open_languages" -> startActivity(Intent(ctx, LanguagesActivity::class.java))
                "toolbar_reset" -> {
                    Prefs(ctx).sp.edit().remove("toolbar_items").apply()
                    Toast.makeText(ctx, R.string.toolbar_reset_done, Toast.LENGTH_SHORT).show()
                }
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
                "shortcuts_edit" -> editShortcuts()
                "dict_export" -> exportDoc.launch("yangiuzbkey-lugat.txt")
                "dict_import" -> importDoc.launch(arrayOf("text/*", "application/octet-stream"))
                "privacy" -> MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.pref_privacy)
                    .setMessage(R.string.privacy_note)
                    .setPositiveButton(R.string.ok, null)
                    .show()
                else -> return super.onPreferenceTreeClick(preference)
            }
            return true
        }

        private fun editShortcuts() {
            val ctx = requireContext()
            val prefs = Prefs(ctx)
            val d = resources.displayMetrics.density
            val input = EditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 6; maxLines = 14
                hint = getString(R.string.shortcuts_hint)
                setText(prefs.shortcuts.lines().filter { '\t' in it }.joinToString("\n") { it.replaceFirst("\t", " = ") })
            }
            val box = FrameLayout(ctx).apply {
                val p = (20 * d).toInt(); setPadding(p, (8 * d).toInt(), p, 0); addView(input)
            }
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.pref_shortcuts)
                .setMessage(R.string.shortcuts_hint)
                .setView(box)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    prefs.shortcuts = input.text.lines().mapNotNull { l ->
                        val i = l.indexOf('=')
                        if (i <= 0) null else {
                            val k = l.substring(0, i).trim(); val v = l.substring(i + 1).trim()
                            if (k.isEmpty() || v.isEmpty() || k.any { it.isWhitespace() }) null else "$k\t$v"
                        }
                    }.joinToString("\n")
                    Toast.makeText(ctx, R.string.shortcuts_saved, Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        /** One file for every language: "[id]" headers followed by "word<TAB>count" lines. */
        private fun exportTo(uri: Uri) {
            val ctx = requireContext()
            try {
                var n = 0
                val sb = StringBuilder("# Yangi Uzb Key lugʻati\n")
                ctx.filesDir.listFiles()?.filter { it.name.startsWith("learned_") && it.name.endsWith(".txt") }?.sortedBy { it.name }?.forEach { f ->
                    val lines = f.readLines().filter { '\t' in it }
                    if (lines.isEmpty()) return@forEach
                    sb.append('[').append(f.name.removePrefix("learned_").removeSuffix(".txt")).append("]\n")
                    lines.forEach { sb.append(it).append('\n') }
                    n += lines.size
                }
                ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(sb.toString().toByteArray()) }
                Toast.makeText(ctx, getString(R.string.dict_exported, n), Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(ctx, R.string.dict_error, Toast.LENGTH_SHORT).show()
            }
        }

        private fun importFrom(uri: Uri) {
            val ctx = requireContext()
            try {
                val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                val byLang = LinkedHashMap<String, LinkedHashMap<String, Int>>()
                var cur: String? = null
                for (raw in text.lines()) {
                    val line = raw.trimEnd('\r')
                    if (line.startsWith("[") && line.endsWith("]")) {
                        cur = line.substring(1, line.length - 1).filter { it.isLetterOrDigit() || it == '_' || it == '-' }
                        continue
                    }
                    val id = cur ?: continue
                    val tab = line.lastIndexOf('\t')
                    if (tab <= 0) continue
                    val w = line.substring(0, tab).trim()
                    if (w.isEmpty() || w.length > 40) continue
                    val c = line.substring(tab + 1).trim().toIntOrNull() ?: 1
                    val m = byLang.getOrPut(id) { LinkedHashMap() }
                    m[w] = maxOf(m[w] ?: 0, c)
                }
                var n = 0
                for ((id, words) in byLang) {
                    if (id.isEmpty()) continue
                    val f = File(ctx.filesDir, "learned_$id.txt")
                    val merged = LinkedHashMap<String, Int>()
                    if (f.exists()) f.forEachLine { l ->
                        val t = l.lastIndexOf('\t')
                        if (t > 0) merged[l.substring(0, t)] = l.substring(t + 1).toIntOrNull() ?: 1
                    }
                    for ((w, c) in words) merged[w] = maxOf(merged[w] ?: 0, c)
                    f.writeText(merged.entries.joinToString("\n") { "${it.key}\t${it.value}" })
                    n += words.size
                }
                // Tells a running keyboard to re-read its learned words.
                Prefs(ctx).sp.edit().putLong("learned_reload", System.currentTimeMillis()).apply()
                Toast.makeText(ctx, getString(R.string.dict_imported, n), Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(ctx, R.string.dict_error, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
