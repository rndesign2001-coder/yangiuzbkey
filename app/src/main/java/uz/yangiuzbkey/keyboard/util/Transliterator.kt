package uz.yangiuzbkey.keyboard.util

/**
 * Converts Uzbek text between the three scripts in use:
 *  - "cyrl": Cyrillic (1940–1993)
 *  - "latn": Latin 1995 (oʻ, gʻ, sh, ch)
 *  - "new":  the new Latin alphabet (ō, ḡ, ş, ç)
 * Latin 1995 is used as the pivot.
 */
object Transliterator {
    const val CYRL = "cyrl"
    const val LATN = "latn"
    const val NEW = "new"

    private const val OKINA = 'ʻ'
    private const val TUTUQ = 'ʼ'
    private val okinaLike = charArrayOf('\'', '`', '’', '‘', 'ʻ', 'ʼ', '´')

    private const val CYR_VOWELS = "аеёиоуэюяўАЕЁИОУЭЮЯЎ"

    private val cyrMap = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ё' to "yo", 'ж' to "j",
        'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f",
        'х' to "x", 'ч' to "ch", 'ш' to "sh", 'щ' to "sh", 'ъ' to "$TUTUQ", 'ы' to "i",
        'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya", 'ў' to "o$OKINA", 'қ' to "q",
        'ғ' to "g$OKINA", 'ҳ' to "h",
    )

    fun isCyrillic(c: Char) = c in 'Ѐ'..'ӿ'

    fun detect(text: String): String {
        var cyr = 0; var lat = 0; var nw = 0
        for (c in text) {
            when {
                isCyrillic(c) -> cyr++
                c in "ōŌḡḠşŞçÇ" -> { nw++; lat++ }
                c in 'a'..'z' || c in 'A'..'Z' -> lat++
            }
        }
        return when {
            cyr > lat -> CYRL
            nw > 0 -> NEW
            else -> LATN
        }
    }

    /** Converts [text] to the [target] script, detecting the source script. */
    fun convert(text: String, target: String): String {
        val latin = when (detect(text)) {
            CYRL -> cyrToLat(text)
            NEW -> newToLat(text)
            else -> normalizeLatin(text)
        }
        return when (target) {
            CYRL -> latToCyr(latin)
            NEW -> latToNew(latin)
            else -> latin
        }
    }

    // ---------- Cyrillic -> Latin ----------

    fun cyrToLat(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (i in s.indices) {
            val c = s[i]
            val lower = c.lowercaseChar()
            if (!isCyrillic(c)) { sb.append(c); continue }
            val prev = if (i > 0) s[i - 1] else ' '
            val next = if (i + 1 < s.length) s[i + 1] else ' '
            val wordStart = !prev.isLetter() && prev != TUTUQ
            val rep: String = when (lower) {
                'е' -> if (wordStart || prev in CYR_VOWELS || prev.lowercaseChar() == 'ъ' || prev.lowercaseChar() == 'ь') "ye" else "e"
                'ц' -> if (!wordStart && prev in CYR_VOWELS) "ts" else "s"
                else -> cyrMap[lower] ?: c.toString()
            }
            if (c.isUpperCase() && rep.isNotEmpty()) {
                val allCaps = next.isUpperCase() || (prev.isUpperCase() && !next.isLowerCase() && next.isLetter())
                sb.append(capitalize(rep, allCaps))
            } else sb.append(rep)
        }
        return sb.toString()
    }

    private fun capitalize(rep: String, allCaps: Boolean): String {
        if (rep.length == 1) return rep.uppercase()
        // keep okina untouched
        return if (allCaps) rep.uppercase() else rep[0].uppercaseChar() + rep.substring(1)
    }

    // ---------- Latin 1995 normalisation ----------

    /** Unifies the many apostrophes people type after o/g into ʻ and the tutuq into ʼ. */
    fun normalizeLatin(s: String): String {
        val sb = StringBuilder(s.length)
        for (i in s.indices) {
            val c = s[i]
            if (c in okinaLike && i > 0) {
                val p = s[i - 1]
                val n = if (i + 1 < s.length) s[i + 1] else ' '
                if ((p == 'o' || p == 'O' || p == 'g' || p == 'G') && (n.isLetter() || !n.isLetterOrDigit())) {
                    // o' at end of quoted word is rare; treat as okina when followed by letter or word end
                    if (n.isLetter() || i == s.lastIndex || n.isWhitespace() || n in ".,!?;:)") { sb.append(OKINA); continue }
                }
                if (p.isLetter() && n.isLetter()) { sb.append(TUTUQ); continue }
            }
            sb.append(c)
        }
        return sb.toString()
    }

    // ---------- Latin <-> New ----------

    fun latToNew(input: String): String {
        val s = normalizeLatin(input)
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val n = if (i + 1 < s.length) s[i + 1] else '\u0000'
            when {
                (c == 'o' || c == 'O') && n == OKINA -> { sb.append(if (c == 'o') 'ō' else 'Ō'); i += 2 }
                (c == 'g' || c == 'G') && n == OKINA -> { sb.append(if (c == 'g') 'ḡ' else 'Ḡ'); i += 2 }
                (c == 's' || c == 'S') && (n == 'h' || n == 'H') -> { sb.append(if (c == 's') 'ş' else 'Ş'); i += 2 }
                (c == 'c' || c == 'C') && (n == 'h' || n == 'H') -> { sb.append(if (c == 'c') 'ç' else 'Ç'); i += 2 }
                c == TUTUQ && i > 0 && (s[i - 1] == 's' || s[i - 1] == 'S' || s[i - 1] == 'c' || s[i - 1] == 'C') && (n == 'h' || n == 'H') -> i++ // isʼhoq -> ishoq
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString()
    }

    fun newToLat(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (i in s.indices) {
            val c = s[i]
            val next = if (i + 1 < s.length) s[i + 1] else ' '
            val prev = if (i > 0) s[i - 1] else ' '
            val caps = next.isUpperCase() || (prev.isUpperCase() && !next.isLowerCase())
            when (c) {
                'ō' -> sb.append("o").append(OKINA)
                'Ō' -> sb.append("O").append(OKINA)
                'ḡ' -> sb.append("g").append(OKINA)
                'Ḡ' -> sb.append("G").append(OKINA)
                'ş' -> sb.append("sh")
                'Ş' -> sb.append(if (caps) "SH" else "Sh")
                'ç' -> sb.append("ch")
                'Ç' -> sb.append(if (caps) "CH" else "Ch")
                'h', 'H' -> {
                    // keep s+h / c+h distinct from ş / ç
                    if (prev == 's' || prev == 'S' || prev == 'c' || prev == 'C') sb.append(TUTUQ)
                    sb.append(c)
                }
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    // ---------- Latin -> Cyrillic ----------

    fun latToCyr(input: String): String {
        val s = normalizeLatin(input)
        val sb = StringBuilder(s.length)
        var i = 0
        fun at(k: Int) = if (k < s.length) s[k] else '\u0000'
        while (i < s.length) {
            val c = s[i]
            val lc = c.lowercaseChar()
            val n = at(i + 1)
            val ln = n.lowercaseChar()
            val up = c.isUpperCase()
            val prev = if (i > 0) s[i - 1] else ' '
            val wordStart = !prev.isLetter() && prev != TUTUQ
            fun put(cyr: Char, len: Int) {
                sb.append(if (up) cyr.uppercaseChar() else cyr); i += len
            }
            if (c !in 'a'..'z' && c !in 'A'..'Z' && c != TUTUQ) { sb.append(c); i++; continue }
            when {
                lc == 's' && ln == 'h' -> put('ш', 2)
                lc == 'c' && ln == 'h' -> put('ч', 2)
                lc == 'o' && n == OKINA -> put('ў', 2)
                lc == 'g' && n == OKINA -> put('ғ', 2)
                lc == 'y' && ln == 'o' && at(i + 2) != OKINA -> put('ё', 2)
                lc == 'y' && ln == 'u' -> put('ю', 2)
                lc == 'y' && ln == 'a' -> put('я', 2)
                lc == 'y' && ln == 'e' -> put('е', 2)
                c == TUTUQ -> {
                    val p = prev.lowercaseChar()
                    if ((p == 's' || p == 'c') && ln == 'h') i++ else { sb.append(if (prev.isUpperCase() && n.isUpperCase()) 'Ъ' else 'ъ'); i++ }
                }
                lc == 'e' -> put(if (wordStart) 'э' else 'е', 1)
                else -> {
                    val m = when (lc) {
                        'a' -> 'а'; 'b' -> 'б'; 'c' -> 'с'; 'd' -> 'д'; 'f' -> 'ф'; 'g' -> 'г'; 'h' -> 'ҳ'
                        'i' -> 'и'; 'j' -> 'ж'; 'k' -> 'к'; 'l' -> 'л'; 'm' -> 'м'; 'n' -> 'н'; 'o' -> 'о'
                        'p' -> 'п'; 'q' -> 'қ'; 'r' -> 'р'; 's' -> 'с'; 't' -> 'т'; 'u' -> 'у'; 'v' -> 'в'
                        'w' -> 'в'; 'x' -> 'х'; 'y' -> 'й'; 'z' -> 'з'
                        else -> c
                    }
                    put(m, 1)
                }
            }
        }
        return sb.toString()
    }
}
