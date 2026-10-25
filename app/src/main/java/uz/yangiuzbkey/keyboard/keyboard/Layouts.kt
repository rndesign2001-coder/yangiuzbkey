package uz.yangiuzbkey.keyboard.keyboard

import java.util.Locale
import uz.yangiuzbkey.keyboard.util.Prefs

/** A keyboard language: letter rows (lowercase) + long-press accents. */
class Language(
    val id: String,
    val name: String,
    val short: String,
    val locale: Locale,
    val rows: List<List<String>>,
    val popups: Map<String, List<String>> = emptyMap(),
    val uzbek: Boolean = false,
) {
    val dictAsset get() = "dict/$id.txt"
}

/** Splits "abc" into code points, or "a b c" on spaces (for multi-char keys). */
internal fun tokens(s: String): List<String> {
    if (s.contains(' ')) return s.split(' ').filter { it.isNotEmpty() }
    val out = ArrayList<String>()
    var i = 0
    while (i < s.length) {
        val cp = s.codePointAt(i)
        val n = Character.charCount(cp)
        out += s.substring(i, i + n)
        i += n
    }
    return out
}

private fun pm(vararg pairs: Pair<String, String>): Map<String, List<String>> =
    pairs.associate { (k, v) -> k to tokens(v) }

object Languages {
    const val OKINA = "ʻ" // ʻ  (oʻ, gʻ)
    const val TUTUQ = "ʼ" // ʼ

    private val latinAccents = pm(
        "a" to "à á â ä æ ã å ā", "e" to "è é ê ë ē", "i" to "ì í î ï ī",
        "o" to "ò ó ô ö õ ø œ", "u" to "ù ú û ü ū", "c" to "ç ć č",
        "n" to "ñ ń", "s" to "ş ß ś š", "y" to "ý ÿ", "z" to "ž ź ż", "g" to "ğ",
    )

    val all: List<Language> = listOf(
        Language(
            "uz_new", "Oʻzbek (yangi)", "ÖZ", Locale("uz", "UZ"),
            listOf(tokens("qwertyuiopö"), tokens("asdfghjklğ$TUTUQ"), tokens("zxçvbnmş")),
            pm(
                "o" to "ö ó ò", "g" to "ğ", "s" to "ş", "ç" to "c", "ş" to "s",
                "ö" to "o", "ğ" to "g", "e" to "é ë", "i" to "í ı", "u" to "ú ü",
                TUTUQ to "$OKINA '", "a" to "á à", "n" to "ñ",
            ),
            uzbek = true,
        ),
        Language(
            "uz_latn", "Oʻzbek (lotin)", "UZ", Locale("uz", "UZ"),
            listOf(
                tokens("q w e r t y u i o p o$OKINA"),
                tokens("a s d f g h j k l g$OKINA $TUTUQ"),
                tokens("zxcvbnm"),
            ),
            pm(
                "o" to "o$OKINA ó ö", "g" to "g$OKINA", "s" to "sh ş", "c" to "ch ç",
                TUTUQ to "$OKINA '", "e" to "é", "u" to "ú ü", "i" to "í",
            ),
            uzbek = true,
        ),
        Language(
            "uz_cyrl", "Ўзбекча (кирилл)", "ЎЗ", Locale("uz", "UZ"),
            listOf(tokens("йцукенгшўзхҳ"), tokens("фқвапролджэғ"), tokens("ячсмитъбю")),
            pm("е" to "ё", "ъ" to "ь", "ш" to "щ", "и" to "ы", "ц" to "ts", "ў" to "у", "ғ" to "г", "қ" to "к", "ҳ" to "х"),
            uzbek = true,
        ),
        Language(
            "ru", "Русский", "RU", Locale("ru", "RU"),
            listOf(tokens("йцукенгшщзх"), tokens("фывапролджэ"), tokens("ячсмитьбю")),
            pm("е" to "ё", "ь" to "ъ", "х" to "ъ"),
        ),
        Language(
            "en", "English", "EN", Locale.US,
            listOf(tokens("qwertyuiop"), tokens("asdfghjkl"), tokens("zxcvbnm")),
            latinAccents,
        ),
        Language(
            "tr", "Türkçe", "TR", Locale("tr", "TR"),
            listOf(tokens("qwertyuıopğü"), tokens("asdfghjklşi"), tokens("zxcvbnmöç")),
            pm("a" to "â", "u" to "û", "ı" to "î", "o" to "ô", "e" to "é"),
        ),
        Language(
            "kk", "Қазақша", "KK", Locale("kk", "KZ"),
            listOf(tokens("әіңғүұқөһ"), tokens("йцукенгшщзх"), tokens("фывапролджэ"), tokens("ячсмитьбю")),
            pm("е" to "ё", "ь" to "ъ", "ү" to "у", "ұ" to "у", "і" to "и"),
        ),
        Language(
            "de", "Deutsch", "DE", Locale.GERMANY,
            listOf(tokens("qwertzuiopü"), tokens("asdfghjklöä"), tokens("yxcvbnm")),
            pm("a" to "ä à á â", "o" to "ö ò ó ô", "u" to "ü ù ú û", "s" to "ß", "e" to "é è ê ë"),
        ),
        Language(
            "fr", "Français", "FR", Locale.FRANCE,
            listOf(tokens("azertyuiop"), tokens("qsdfghjklm"), tokens("wxcvbn'")),
            pm(
                "a" to "à â æ ä", "e" to "é è ê ë", "i" to "î ï", "o" to "ô œ ö",
                "u" to "ù û ü", "c" to "ç", "y" to "ÿ", "'" to "’ \"",
            ),
        ),
        Language(
            "es", "Español", "ES", Locale("es", "ES"),
            listOf(tokens("qwertyuiop"), tokens("asdfghjklñ"), tokens("zxcvbnm")),
            pm("a" to "á à", "e" to "é è", "i" to "í ï", "o" to "ó ò", "u" to "ú ü", "n" to "ñ", "c" to "ç"),
        ),
    )

    fun byId(id: String): Language = all.firstOrNull { it.id == id } ?: all.first()
}

enum class FieldVariant { NORMAL, EMAIL, URL }

object LayoutBuilder {
    private val HINT_ROW2 = tokens("@#\$_&-+()")
    private val HINT_ROW3 = tokens("*\"':;!?")

    private val digitPopups = mapOf(
        "1" to tokens("¹ ½ ⅓ ¼ ⅛"), "2" to tokens("² ⅔"), "3" to tokens("³ ¾ ⅜"),
        "4" to tokens("⁴"), "5" to tokens("⅝"), "7" to tokens("⅞"), "0" to tokens("ⁿ ∅"),
    )
    private val symPopups = mapOf(
        "$" to tokens("€ £ ¥ ₽ ₸ ¢ ₺ ₩"), "-" to tokens("_ – — ·"), "(" to tokens("< { ["),
        ")" to tokens("> } ]"), "\"" to tokens("« » “ ” „"), "'" to tokens("ʼ ʻ ‘ ’ ‚"),
        "!" to tokens("¡"), "?" to tokens("¿"), "*" to tokens("★ †"), "%" to tokens("‰"),
        "&" to tokens("§"), "+" to tokens("±"), "/" to tokens("÷"), "=" to tokens("≠ ≈ ∞"),
        "." to tokens("… · •"), "°" to tokens("′ ″"), "<" to tokens("≤ « ‹"), ">" to tokens("≥ » ›"),
    )
    private val periodPopups = tokens("? ! , : ; … - ' \" @ / #")

    fun numberRow(units: Float): List<Key> =
        tokens("1234567890").map { Key.char(it, digitPopups[it] ?: emptyList(), units / 10f) }

    fun alpha(lang: Language, p: Prefs, multiLang: Boolean, variant: FieldVariant, forceNumberRow: Boolean = false): KeyboardLayout {
        val withNumbers = p.numberRow || forceNumberRow
        val letterRows = lang.rows
        val last = letterRows.last()
        var units = (letterRows.dropLast(1).maxOfOrNull { it.size } ?: 0).toFloat()
        units = maxOf(units, last.size + 2f, 10f)
        val rows = ArrayList<List<Key>>()
        var numIdx = -1
        if (withNumbers) {
            rows += numberRow(units); numIdx = 0
        }
        letterRows.forEachIndexed { ri, row ->
            val keys = row.mapIndexed { ci, t ->
                val out = if (t == Languages.TUTUQ) p.apostrophe else t
                val pops = ArrayList<String>()
                var hint: String? = null
                val fromEnd = letterRows.size - ri // 3 = first, 2 = middle, 1 = last
                when {
                    ri == 0 && !withNumbers && ci < 10 -> hint = ((ci + 1) % 10).toString()
                    fromEnd == 2 && ci < HINT_ROW2.size -> hint = HINT_ROW2[ci]
                    fromEnd == 1 && ci < HINT_ROW3.size -> hint = HINT_ROW3[ci]
                }
                hint?.let { pops += it }
                lang.popups[t]?.let { pops += it }
                Key.char(out, pops).also { if (p.hints) it.hint = hint }
            }
            if (ri == letterRows.lastIndex) {
                val side = maxOf(1f, (units - row.size) / 2f)
                rows += listOf(Key.func(Key.SHIFT, weight = side)) + keys +
                    Key.func(Key.DELETE, weight = side, repeatable = true)
            } else rows += keys
        }
        rows += bottomRow(units, true, lang, p, multiLang, variant)
        return KeyboardLayout(rows, Mode.ALPHA, units, numIdx)
    }

    private fun bottomRow(units: Float, alpha: Boolean, lang: Language, p: Prefs, multiLang: Boolean, variant: FieldVariant): List<Key> {
        val left = ArrayList<Key>()
        left += if (alpha) Key.func(Key.SYMBOLS, "?123", 1.5f) else Key.func(Key.ALPHA, "ABC", 1.5f)
        val commaPops = ArrayList<String>().apply {
            add(Key.POP_SETTINGS); add(Key.POP_EMOJI); add(Key.POP_CLIPBOARD)
        }
        left += when (variant) {
            FieldVariant.EMAIL -> Key.char("@", tokens("_ - +"))
            FieldVariant.URL -> Key.char("/", tokens(": _ -"))
            else -> Key.char(",", commaPops)
        }
        if (alpha && multiLang && p.langKey) left += Key.func(Key.LANG)
        val right = ArrayList<Key>()
        right += if (variant == FieldVariant.URL) Key.char(".", tokens(".com .uz .ru .org .net"))
        else Key.char(".", periodPopups)
        right += Key.func(Key.ENTER, weight = 1.5f)
        val used = left.sumOf { it.weight.toDouble() } + right.sumOf { it.weight.toDouble() }
        val spaceLabel = if (alpha && multiLang && p.spaceLang) "‹  ${lang.name}  ›" else lang.name
        val space = Key(Key.SPACE, spaceLabel, " ", weight = maxOf(3f, units - used.toFloat()))
        return left + space + right
    }

    private fun symRow(s: String, units: Float = 10f) =
        tokens(s).map { Key.char(it, symPopups[it] ?: digitPopups[it] ?: emptyList()) }

    fun symbols(lang: Language, p: Prefs, page2: Boolean, variant: FieldVariant): KeyboardLayout {
        val rows = ArrayList<List<Key>>()
        if (!page2) {
            rows += symRow("1234567890")
            rows += symRow("@#\$_&-+()/")
            rows += listOf(Key.func(Key.SYMBOLS2, "=\\<", 1.5f)) + symRow("*\"':;!?") +
                Key.func(Key.DELETE, weight = 1.5f, repeatable = true)
        } else {
            rows += symRow("~ ` | • √ π ÷ × ¶ ∆")
            rows += symRow("£ € ¥ ^ ° = { } \\ %")
            rows += listOf(Key.func(Key.SYMBOLS, "?123", 1.5f)) + symRow("© ™ ✓ [ ] < >") +
                Key.func(Key.DELETE, weight = 1.5f, repeatable = true)
            rows[2][1].popups = tokens("®")
        }
        rows += bottomRow(10f, false, lang, p, false, variant)
        return KeyboardLayout(rows, if (page2) Mode.SYMBOLS2 else Mode.SYMBOLS, 10f)
    }

    fun numpad(phone: Boolean): KeyboardLayout {
        val rows = ArrayList<List<Key>>()
        if (phone) {
            rows += symRow("1 2 3 (")
            rows += symRow("4 5 6 )")
            rows += symRow("7 8 9") + Key.func(Key.DELETE, repeatable = true)
            rows += listOf(Key.char("*", tokens("+ ,")), Key.char("0", tokens("+")), Key.char("#", tokens("; N")), Key.func(Key.ENTER))
        } else {
            rows += symRow("1 2 3 -")
            rows += symRow("4 5 6 ,")
            rows += symRow("7 8 9") + Key.func(Key.DELETE, repeatable = true)
            rows += listOf(Key.char("+", tokens("± *")), Key.char("0"), Key.char(".", tokens("/ %")), Key.func(Key.ENTER))
        }
        return KeyboardLayout(rows, if (phone) Mode.PHONE else Mode.NUMPAD, 4f)
    }
}
