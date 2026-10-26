package uz.yangiuzbkey.keyboard.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/** Typed access to every user setting. Keys match res/xml/preferences.xml. */
class Prefs(context: Context) {
    val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    val numberRow get() = sp.getBoolean("number_row", false)
    val hints get() = sp.getBoolean("hints", false)
    val toolbar get() = sp.getBoolean("toolbar", true)
    val langKey get() = sp.getBoolean("lang_key", false)
    val keyPopup get() = sp.getBoolean("key_popup", true)
    val heightPercent get() = sp.getInt("height", 100).coerceIn(70, 150)
    val navSafe get() = sp.getBoolean("nav_safe", true)
    val bottomGap get() = sp.getInt("bottom_gap", 0).coerceIn(0, 48)

    /** Toolbar buttons in display order, as SuggestionStrip.Action names. */
    var toolbarItems: List<String>
        get() = (sp.getString("toolbar_items", null) ?: DEFAULT_TOOLBAR)
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }
        set(v) { sp.edit().putString("toolbar_items", v.joinToString(",")).apply() }

    var screenshots: Boolean
        get() = sp.getBoolean("clip_screenshots", false)
        set(v) { sp.edit().putBoolean("clip_screenshots", v).apply() }

    val autoConvert get() = sp.getBoolean("auto_convert", true)
    val apostrophe: String get() = sp.getString("apostrophe", "ʼ") ?: "ʼ"

    val autoCap get() = sp.getBoolean("auto_cap", true)
    val doubleSpace get() = sp.getBoolean("double_space", true)
    val spaceCursor get() = sp.getBoolean("space_cursor", false)
    val spaceLang get() = sp.getBoolean("space_lang", true)
    val deleteSwipe get() = sp.getBoolean("delete_swipe", true)
    val longPressMs get() = sp.getInt("long_press", 300).coerceIn(150, 700).toLong()

    val suggestions get() = sp.getBoolean("suggestions", true)
    val autocorrect get() = sp.getBoolean("autocorrect", false)
    val learn get() = sp.getBoolean("learn", true)
    val glide get() = sp.getBoolean("glide", true)
    val uzSpell get() = sp.getBoolean("uz_spell", true)

    var incognito: Boolean
        get() = sp.getBoolean("incognito", false)
        set(v) { sp.edit().putBoolean("incognito", v).apply() }

    /** "off", "left" or "right". */
    var oneHand: String
        get() = sp.getString("one_hand", "off") ?: "off"
        set(v) { sp.edit().putString("one_hand", v).apply() }
    val oneHandWidth get() = sp.getInt("one_hand_width", 82).coerceIn(70, 95)

    /** Text shortcuts, one "key\tvalue" pair per line. */
    var shortcuts: String
        get() = sp.getString("shortcuts", null) ?: DEFAULT_SHORTCUTS
        set(v) { sp.edit().putString("shortcuts", v).apply() }
    val shortcutMap: Map<String, String>
        get() = shortcuts.lines().mapNotNull { l ->
            val i = l.indexOf('\t'); if (i <= 0) null
            else l.substring(0, i).trim().lowercase().takeIf { it.isNotEmpty() }?.let { it to l.substring(i + 1).trim() }
        }.filter { it.second.isNotEmpty() }.toMap()

    val vibrate get() = sp.getBoolean("vibrate", true)
    val vibrateMs get() = sp.getInt("vibrate_strength", 15).coerceIn(5, 60).toLong()
    val sound get() = sp.getBoolean("sound", false)
    val soundVolume get() = sp.getInt("sound_volume", 30).coerceIn(5, 100) / 100f

    /** Ordered list of enabled language ids. */
    var languages: List<String>
        get() = (sp.getString("languages", null) ?: DEFAULT_LANGS)
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }
            .ifEmpty { DEFAULT_LANGS.split(',') }
        set(v) { sp.edit().putString("languages", v.joinToString(",")).apply() }

    var currentLanguage: String
        get() = sp.getString("current_language", null) ?: languages.first()
        set(v) { sp.edit().putString("current_language", v).apply() }

    fun toggleNumberRow() { sp.edit().putBoolean("number_row", !numberRow).apply() }

    companion object {
        const val DEFAULT_LANGS = "uz_new,uz_latn,ru,en"
        const val DEFAULT_SHORTCUTS = "assl\tAssalomu alaykum\nrhm\tRahmat\nxayr\tXayr, salomat bo'ling"
        const val DEFAULT_TOOLBAR = "SETTINGS,EMOJI,CLIPBOARD,NUMBERS,THEME,CONVERT,HIDE"
    }
}
