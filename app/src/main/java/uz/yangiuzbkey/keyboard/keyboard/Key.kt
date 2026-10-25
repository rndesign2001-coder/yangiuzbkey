package uz.yangiuzbkey.keyboard.keyboard

import android.graphics.RectF
import java.util.Locale

/**
 * A single key. [code] > 0 is a Unicode code point, negative values are the
 * functional codes in the companion object. [output] overrides the committed
 * text (multi-character keys like "oʻ").
 */
class Key(
    val code: Int,
    val label: String,
    val output: String? = null,
    var popups: List<String> = emptyList(),
    var weight: Float = 1f,
    val functional: Boolean = false,
    val repeatable: Boolean = false,
    var hint: String? = null,
) {
    /** Visual rect in view coordinates (includes the gap). */
    val rect = RectF()

    val isChar get() = code > 0 || code == TEXT

    val text: String
        get() = output ?: if (code > 0) String(Character.toChars(code)) else label

    fun shifted(locale: Locale) = upper(text, locale)

    companion object {
        const val SHIFT = -1
        const val SYMBOLS = -2
        const val ALPHA = -3
        const val SYMBOLS2 = -4
        const val DELETE = -5
        const val LANG = -6
        const val ENTER = -7
        const val EMOJI = -8
        const val SETTINGS = -9
        const val TEXT = -10
        const val SPACE = 32

        /** Popup tokens that trigger actions instead of text. */
        const val POP_EMOJI = "{emoji}"
        const val POP_SETTINGS = "{settings}"
        const val POP_CLIPBOARD = "{clipboard}"

        fun upper(s: String, locale: Locale): String {
            if (s.isEmpty() || s.startsWith("{")) return s
            // "oʻ" -> "Oʻ" ; "ß" stays a single letter
            val first = s.codePointAt(0)
            val firstStr = String(Character.toChars(first))
            val up = if (firstStr == "ß") "ẞ" else firstStr.uppercase(locale)
            return up + s.substring(firstStr.length)
        }

        fun char(s: String, popups: List<String> = emptyList(), weight: Float = 1f): Key {
            val cp = s.codePointAt(0)
            return if (Character.charCount(cp) == s.length) Key(cp, s, null, popups, weight)
            else Key(TEXT, s, s, popups, weight)
        }

        fun func(code: Int, label: String = "", weight: Float = 1f, repeatable: Boolean = false, popups: List<String> = emptyList()) =
            Key(code, label, null, popups, weight, functional = true, repeatable = repeatable)
    }
}

enum class Mode { ALPHA, SYMBOLS, SYMBOLS2, NUMPAD, PHONE }

class KeyboardLayout(
    val rows: List<List<Key>>,
    val mode: Mode,
    /** Units across one row; keys are laid out proportionally to this. */
    val units: Float,
    /** Index of the optional number row (height is slightly smaller). */
    val numberRowIndex: Int = -1,
)
