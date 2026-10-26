package uz.yangiuzbkey.keyboard.ime

import android.content.Context
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import uz.yangiuzbkey.keyboard.keyboard.Language

/**
 * Word suggestions and autocorrect for one language at a time.
 *
 * The bundled dictionary is a frequency-ordered word list; words the user types
 * are learned locally (never leave the device) and ranked above it.
 */
class SuggestionEngine(private val context: Context) {

    class Result(val words: List<String>, val autocorrect: String?)

    private val io = Executors.newSingleThreadExecutor()

    @Volatile private var langId: String? = null
    @Volatile private var words: Array<String> = emptyArray()
    @Volatile private var rank: HashMap<String, Int> = HashMap()
    private val learned = LinkedHashMap<String, Int>()
    /** Learned word pairs: previous word -> (next word -> count). */
    private val bigrams = HashMap<String, HashMap<String, Int>>()
    private var bigramCount = 0
    private var locale: Locale = Locale.ROOT
    private var dirty = false

    fun load(lang: Language) {
        if (lang.id == langId) return
        flush()
        langId = lang.id
        locale = lang.locale
        words = emptyArray(); rank = HashMap()
        synchronized(learned) { learned.clear(); bigrams.clear(); bigramCount = 0 }
        val id = lang.id
        io.execute {
            val list = try {
                context.assets.open(lang.dictAsset).bufferedReader().useLines { s ->
                    s.map { it.trim() }.filter { it.isNotEmpty() }.toList()
                }
            } catch (_: Exception) { emptyList() }
            val r = HashMap<String, Int>(list.size * 2)
            list.forEachIndexed { i, w -> r.putIfAbsent(w.lowercase(locale), i) }
            val learnedFile = learnedFile(id)
            val l = LinkedHashMap<String, Int>()
            if (learnedFile.exists()) try {
                learnedFile.forEachLine { line ->
                    val tab = line.lastIndexOf('\t')
                    if (tab > 0) l[line.substring(0, tab)] = line.substring(tab + 1).toIntOrNull() ?: 1
                }
            } catch (_: Exception) {}
            val bg = HashMap<String, HashMap<String, Int>>()
            var bc = 0
            val bigramFile = bigramFile(id)
            if (bigramFile.exists()) try {
                bigramFile.forEachLine { line ->
                    val parts = line.split('\t')
                    if (parts.size == 3) {
                        bg.getOrPut(parts[0]) { HashMap() }[parts[1]] = parts[2].toIntOrNull() ?: 1
                        bc++
                    }
                }
            } catch (_: Exception) {}
            if (langId == id) {
                words = list.toTypedArray(); rank = r
                synchronized(learned) {
                    learned.clear(); learned.putAll(l)
                    bigrams.clear(); bigrams.putAll(bg); bigramCount = bc
                }
            }
        }
    }

    /** Drops the cache and reads the dictionary and learned words again (after an import). */
    // The file was rewritten by an import (which already merged what was saved); re-read it as is.
    fun reload(lang: Language) { dirty = false; langId = null; load(lang) }

    /**
     * Glide typing: [path] is the sequence of letter keys the finger crossed.
     * A candidate starts and ends on the path's ends and its letters appear
     * along the path in order.
     */
    fun glide(path: String, max: Int = 4): List<String> {
        if (path.length < 2) return emptyList()
        val p = path.lowercase(locale)
        val first = p[0]; val last = p[p.length - 1]
        val scored = ArrayList<Pair<String, Int>>()
        val seen = HashSet<String>()
        fun consider(w: String, base: Int) {
            if (w.length < 2 || w in seen) return
            val letters = w.filter { it.isLetter() && it != '\u02BB' }
            if (letters.isEmpty() || letters[0] != first || letters[letters.length - 1] != last) return
            var j = 0
            var prev = ' '
            for (c in letters) {
                if (c == prev && j > 0 && p[j - 1] == c) continue
                while (j < p.length && p[j] != c) j++
                if (j >= p.length) return
                j++; prev = c
            }
            seen += w
            val skipped = p.length - letters.length
            scored += w to base + skipped.coerceAtLeast(0) * 120 - letters.length * 40
        }
        synchronized(learned) {
            for ((w, n) in learned) if (n >= 1) consider(w, -minOf(n, 20) * 400)
        }
        val dict = words
        val n = minOf(dict.size, 60000)
        for (i in 0 until n) consider(dict[i].lowercase(locale), i / 4)
        return scored.sortedBy { it.second }.take(max).map { it.first }
    }

    private fun learnedFile(id: String) = File(context.filesDir, "learned_$id.txt")
    private fun bigramFile(id: String) = File(context.filesDir, "bigram_$id.txt")

    /** Remembers that [next] followed [prev], for next-word prediction. */
    fun learnPair(prev: String, next: String) {
        if (prev.isEmpty() || next.length < 2 || next.any { it.isDigit() } || prev.any { it.isDigit() }) return
        val a = prev.lowercase(locale); val b = next.lowercase(locale)
        synchronized(learned) {
            val m = bigrams.getOrPut(a) { HashMap() }
            val old = m[b]
            if (old == null) {
                if (bigramCount >= MAX_BIGRAMS) trimBigrams()
                bigramCount++
            }
            m[b] = (old ?: 0) + 1
        }
        dirty = true
    }

    /** Drops pairs seen only once (the long tail) when the table is full. */
    private fun trimBigrams() {
        val it = bigrams.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val before = e.value.size
            e.value.entries.removeAll { x -> x.value <= 1 }
            bigramCount -= before - e.value.size
            if (e.value.isEmpty()) it.remove()
        }
        if (bigramCount >= MAX_BIGRAMS) { bigrams.clear(); bigramCount = 0 }
    }

    /** Words the user usually types after [prev], most frequent first. */
    fun predict(prev: String, max: Int = 3): List<String> {
        val p = prev.lowercase(locale)
        val hits = synchronized(learned) {
            bigrams[p]?.entries?.sortedByDescending { it.value }?.take(max)?.map { it.key }
        } ?: emptyList()
        val capital = prev.firstOrNull()?.isUpperCase() == true && prev.length > 1 && prev.drop(1).any { it.isUpperCase() }
        return hits.map { if (capital) it.uppercase(locale) else it }
    }

    fun isKnown(word: String): Boolean {
        val w = word.lowercase(locale)
        return rank.containsKey(w) || synchronized(learned) { (learned[w] ?: 0) >= 2 }
    }

    /** Remembers a word the user committed. Short or numeric strings are ignored. */
    fun learn(word: String) {
        if (word.length < 2 || word.any { it.isDigit() }) return
        val w = word.lowercase(locale)
        synchronized(learned) {
            learned[w] = (learned[w] ?: 0) + 1
            if (learned.size > 5000) learned.remove(learned.keys.first())
        }
        dirty = true
    }

    fun forget(word: String) {
        synchronized(learned) { learned.remove(word.lowercase(locale)) }
        dirty = true
    }

    fun flush() {
        val id = langId ?: return
        if (!dirty) return
        dirty = false
        val snapshot = synchronized(learned) { learned.entries.map { it.key to it.value } }
        val pairs = synchronized(learned) {
            val sb = StringBuilder()
            for ((a, m) in bigrams) for ((b, c) in m) sb.append(a).append('\t').append(b).append('\t').append(c).append('\n')
            sb.toString()
        }
        io.execute {
            try {
                learnedFile(id).writeText(snapshot.joinToString("\n") { "${it.first}\t${it.second}" })
                bigramFile(id).writeText(pairs)
            } catch (_: Exception) {}
        }
    }

    fun clearLearned() {
        synchronized(learned) { learned.clear(); bigrams.clear(); bigramCount = 0 }
        dirty = false
        io.execute {
            context.filesDir.listFiles()?.filter { it.name.startsWith("learned_") || it.name.startsWith("bigram_") }?.forEach { it.delete() }
        }
    }

    /**
     * Suggestions for the word being typed. [typed] keeps the user's case,
     * which is reapplied to every candidate.
     */
    fun suggest(typed: String, max: Int = 3, allowCorrection: Boolean = true): Result {
        if (typed.isEmpty()) return Result(emptyList(), null)
        val low = typed.lowercase(locale)
        val dict = words
        val out = LinkedHashSet<String>()

        // 1. Learned words with this prefix (most used first).
        val learnedHits = synchronized(learned) {
            learned.entries.filter { it.key.startsWith(low) && it.key != low && it.value >= 1 }
                .sortedByDescending { it.value }.take(max).map { it.key }
        }
        // 2. Dictionary completions in frequency order.
        val completions = ArrayList<String>()
        for (w in dict) {
            if (completions.size >= max * 2) break
            if (w.length > low.length && w.regionMatches(0, low, 0, low.length, ignoreCase = true)) completions += w.lowercase(locale)
        }

        // 3. Corrections when the typed word is unknown.
        var correction: String? = null
        val known = isKnown(low)
        if (allowCorrection && !known && low.length >= 2 && dict.isNotEmpty()) {
            val limit = if (low.length >= 6) 2 else 1
            var best: String? = null; var bestScore = Int.MAX_VALUE
            val candidates = ArrayList<Pair<String, Int>>()
            val n = minOf(dict.size, 25000)
            for (i in 0 until n) {
                val w = dict[i]
                if (kotlin.math.abs(w.length - low.length) > limit) continue
                val lw = w.lowercase(locale)
                // Most typos keep the first letter; allow a change there only at distance 1.
                val d = distance(low, lw, limit)
                if (d > limit) continue
                val score = d * 100000 + i + (if (lw[0] != low[0]) 50000 else 0)
                candidates += lw to score
                if (score < bestScore) { bestScore = score; best = lw }
            }
            candidates.sortBy { it.second }
            correction = best
            candidates.take(max).forEach { out += it.first }
        }

        val result = ArrayList<String>()
        if (correction != null) result += correction
        for (w in learnedHits + completions + out) if (w !in result && w != low) result += w
        if (known && result.size > max - 1) result.subList(max - 1, result.size).clear()
        return Result(result.take(max).map { applyCase(typed, it) }, correction?.let { applyCase(typed, it) })
    }

    /** Predictions after a space: frequent words that commonly start a phrase. */
    fun nextWords(max: Int = 3): List<String> {
        val dict = words
        return dict.asSequence().filter { it.length > 1 }.take(max).toList()
    }

    private companion object { const val MAX_BIGRAMS = 20000 }

    private fun applyCase(typed: String, w: String): String {
        if (typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() }) return w.uppercase(locale)
        if (typed[0].isUpperCase()) return w.replaceFirstChar { it.titlecase(locale) }
        return w
    }

    /** Damerau–Levenshtein (optimal string alignment) with an early exit above [limit]. */
    private fun distance(a: String, b: String, limit: Int): Int {
        val n = a.length; val m = b.length
        var prev2 = IntArray(m + 1)
        var prev = IntArray(m + 1) { it }
        var cur = IntArray(m + 1)
        for (i in 1..n) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > limit) return limit + 1
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[m]
    }
}
