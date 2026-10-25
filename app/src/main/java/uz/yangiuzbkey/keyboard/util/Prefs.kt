package uz.yangiuzbkey.keyboard.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/** Typed access to every user setting. Keys match res/xml/preferences.xml. */
class Prefs(context: Context) {
    val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    val numberRow get() = sp.getBoolean("number_row", false)
    val hints get() = sp.getBoolean("hints", true)
    val toolbar get() = sp.getBoolean("toolbar", true)
    val emojiKey get() = sp.getBoolean("emoji_key", true)
    val langKey get() = sp.getBoolean("lang_key", true)
    val keyPopup get() = sp.getBoolean("key_popup", true)
    val heightPercent get() = sp.getInt("height", 100).coerceIn(70, 150)

    val autoConvert get() = sp.getBoolean("auto_convert", true)
    val apostrophe: String get() = sp.getString("apostrophe", "ʼ") ?: "ʼ"

    val autoCap get() = sp.getBoolean("auto_cap", true)
    val doubleSpace get() = sp.getBoolean("double_space", true)
    val spaceCursor get() = sp.getBoolean("space_cursor", true)
    val deleteSwipe get() = sp.getBoolean("delete_swipe", true)
    val longPressMs get() = sp.getInt("long_press", 300).coerceIn(150, 700).toLong()

    val suggestions get() = sp.getBoolean("suggestions", true)
    val autocorrect get() = sp.getBoolean("autocorrect", false)
    val learn get() = sp.getBoolean("learn", true)

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
    }
}
