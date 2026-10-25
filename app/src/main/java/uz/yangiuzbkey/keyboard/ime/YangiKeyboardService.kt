package uz.yangiuzbkey.keyboard.ime

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import uz.yangiuzbkey.keyboard.R
import uz.yangiuzbkey.keyboard.keyboard.FieldVariant
import uz.yangiuzbkey.keyboard.keyboard.Icon
import uz.yangiuzbkey.keyboard.keyboard.Key
import uz.yangiuzbkey.keyboard.keyboard.KeyboardView
import uz.yangiuzbkey.keyboard.keyboard.Language
import uz.yangiuzbkey.keyboard.keyboard.Languages
import uz.yangiuzbkey.keyboard.keyboard.LayoutBuilder
import uz.yangiuzbkey.keyboard.keyboard.Mode
import uz.yangiuzbkey.keyboard.keyboard.OverlayView
import uz.yangiuzbkey.keyboard.keyboard.ThemedFrame
import uz.yangiuzbkey.keyboard.settings.SettingsActivity
import uz.yangiuzbkey.keyboard.settings.ThemesActivity
import uz.yangiuzbkey.keyboard.theme.ThemeManager
import uz.yangiuzbkey.keyboard.util.Prefs
import uz.yangiuzbkey.keyboard.util.Transliterator

/**
 * The input method. Keeps the word being typed as composing text so that
 * suggestions, autocorrect and new-alphabet conversion can rewrite it.
 */
class YangiKeyboardService : InputMethodService(), KeyboardView.Listener, SuggestionStrip.Listener,
    EmojiPanel.Listener, ClipboardPanel.Listener, SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var engine: SuggestionEngine
    private lateinit var clipHistory: ClipboardHistory
    private var clipboard: ClipboardManager? = null

    private var root: ThemedFrame? = null
    private lateinit var strip: SuggestionStrip
    private lateinit var kv: KeyboardView
    private lateinit var emoji: EmojiPanel
    private lateinit var clipPanel: ClipboardPanel
    private lateinit var overlay: OverlayView

    private var lang: Language = Languages.all.first()
    private var mode = Mode.ALPHA
    private var variant = FieldVariant.NORMAL
    private var password = false
    private var noSuggest = false
    private var rawInput = false

    private val composing = StringBuilder()
    private var lastShiftTap = 0L
    private var oneShotShift = false
    private var lastSpaceTime = 0L
    /** Set after an autocorrect: (corrected text incl. separator, original word). */
    private var revert: Pair<String, String>? = null
    private var expectedSelEnd = -1
    private var layoutDirty = true
    private var styleDirty = true
    private var swipeWords = 0

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { clipboard?.let { clipHistory.capture(it) } }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        engine = SuggestionEngine(this)
        clipHistory = ClipboardHistory(this)
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        try { clipboard?.addPrimaryClipChangedListener(clipListener) } catch (_: Exception) {}
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
        lang = Languages.byId(prefs.currentLanguage)
        engine.load(lang)
    }

    override fun onDestroy() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        try { clipboard?.removePrimaryClipChangedListener(clipListener) } catch (_: Exception) {}
        engine.flush()
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        layoutDirty = true; styleDirty = true
        if (key == "languages" || key == "current_language") {
            lang = Languages.byId(prefs.currentLanguage); engine.load(lang)
        }
        if (key == "learned_reset") engine.clearLearned()
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View {
        val frame = ThemedFrame(this)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        strip = SuggestionStrip(this).also { it.listener = this }
        val body = FrameLayout(this)
        overlay = OverlayView(this)
        kv = KeyboardView(this).also { it.listener = this; it.overlay = overlay }
        emoji = EmojiPanel(this).also { it.listener = this; it.visibility = View.GONE }
        clipPanel = ClipboardPanel(this, clipHistory).also { it.listener = this; it.visibility = View.GONE }
        val mp = ViewGroup.LayoutParams.MATCH_PARENT
        body.addView(kv, FrameLayout.LayoutParams(mp, ViewGroup.LayoutParams.WRAP_CONTENT))
        body.addView(emoji, FrameLayout.LayoutParams(mp, mp))
        body.addView(clipPanel, FrameLayout.LayoutParams(mp, mp))
        column.addView(strip, LinearLayout.LayoutParams(mp, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(body, LinearLayout.LayoutParams(mp, ViewGroup.LayoutParams.WRAP_CONTENT))
        frame.addView(column, FrameLayout.LayoutParams(mp, ViewGroup.LayoutParams.WRAP_CONTENT))
        frame.addView(overlay, FrameLayout.LayoutParams(mp, mp))
        if (Build.VERSION.SDK_INT >= 20) {
            frame.setOnApplyWindowInsetsListener { v, insets ->
                @Suppress("DEPRECATION")
                v.setPadding(0, 0, 0, insets.systemWindowInsetBottom); insets
            }
        }
        root = frame
        layoutDirty = true; styleDirty = true
        return frame
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        clipboard?.let { clipHistory.capture(it) }
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val vari = info.inputType and InputType.TYPE_MASK_VARIATION
        rawInput = info.inputType == InputType.TYPE_NULL
        password = cls == InputType.TYPE_CLASS_TEXT && (vari == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            vari == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || vari == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) ||
            cls == InputType.TYPE_CLASS_NUMBER && vari == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        variant = when {
            cls == InputType.TYPE_CLASS_TEXT && (vari == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                vari == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) -> FieldVariant.EMAIL
            cls == InputType.TYPE_CLASS_TEXT && vari == InputType.TYPE_TEXT_VARIATION_URI -> FieldVariant.URL
            else -> FieldVariant.NORMAL
        }
        noSuggest = rawInput || password || variant != FieldVariant.NORMAL ||
            info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0 || cls != InputType.TYPE_CLASS_TEXT
        mode = when (cls) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME -> Mode.NUMPAD
            InputType.TYPE_CLASS_PHONE -> Mode.PHONE
            else -> Mode.ALPHA
        }
        lang = Languages.byId(prefs.currentLanguage)
        engine.load(lang)
        composing.setLength(0); revert = null; oneShotShift = false
        closePanels()
        applySettings(force = true)
        updateEnter(info)
        updateShift()
        showIdleSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        finishComposing()
        if (::kv.isInitialized) kv.cancelAll()
        engine.flush()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candStart: Int, candEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candStart, candEnd)
        // Cursor moved by the user (tap, selection): drop our composing state.
        if (composing.isNotEmpty() && (candEnd == -1 || newSelEnd != candEnd || newSelStart != newSelEnd)) {
            composing.setLength(0)
            currentInputConnection?.finishComposingText()
            revert = null
            showIdleSuggestions()
        } else if (composing.isEmpty() && newSelEnd != expectedSelEnd) {
            revert = null
        }
        expectedSelEnd = -1
        if (composing.isEmpty()) updateShift()
    }

    // ---------------------------------------------------------------- setup

    private fun applySettings(force: Boolean = false) {
        if (!::kv.isInitialized) return
        if (styleDirty || force) {
            val st = ThemeManager.resolve(this, prefs)
            root?.style = st; kv.setStyle(st); strip.style = st
            emoji.setStyle(st); clipPanel.setStyle(st)
            strip.invalidate(); root?.invalidate()
            styleDirty = false
        }
        kv.heightPercent = prefs.heightPercent
        kv.longPressMs = prefs.longPressMs
        kv.keyPopup = prefs.keyPopup
        kv.spaceCursor = prefs.spaceCursor
        kv.deleteSwipe = prefs.deleteSwipe
        kv.locale = lang.locale
        strip.numberRowOn = prefs.numberRow
        strip.toolbarEnabled = prefs.toolbar
        val alpha = LayoutBuilder.alpha(lang, prefs, prefs.languages.size > 1, variant, password)
        val sym = LayoutBuilder.symbols(lang, prefs, false, variant)
        kv.minContentHeight = if (mode == Mode.ALPHA || mode == Mode.SYMBOLS || mode == Mode.SYMBOLS2)
            maxOf(kv.naturalHeight(alpha), kv.naturalHeight(sym)) else 0
        kv.setKeyboard(when (mode) {
            Mode.ALPHA -> alpha
            Mode.SYMBOLS -> sym
            Mode.SYMBOLS2 -> LayoutBuilder.symbols(lang, prefs, true, variant)
            Mode.NUMPAD -> LayoutBuilder.numpad(false)
            Mode.PHONE -> LayoutBuilder.numpad(true)
        })
        layoutDirty = false
    }

    private fun updateEnter(info: EditorInfo? = currentInputEditorInfo) {
        if (!::kv.isInitialized) return
        val opts = info?.imeOptions ?: 0
        val icon = if (opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) Icon.ENTER else when (opts and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> Icon.SEARCH
            EditorInfo.IME_ACTION_SEND -> Icon.SEND
            EditorInfo.IME_ACTION_DONE -> Icon.DONE
            EditorInfo.IME_ACTION_NEXT -> Icon.NEXT
            EditorInfo.IME_ACTION_GO -> Icon.NEXT
            EditorInfo.IME_ACTION_PREVIOUS -> Icon.BACK
            else -> Icon.ENTER
        }
        kv.setEnter(icon, null)
    }

    private fun updateShift() {
        if (!::kv.isInitialized || mode != Mode.ALPHA) return
        if (kv.shift == KeyboardView.ShiftState.LOCKED) return
        val info = currentInputEditorInfo ?: return
        val auto = prefs.autoCap && !password && variant == FieldVariant.NORMAL && composing.isEmpty() &&
            info.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            (currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0) != 0
        if (auto) { kv.shift = KeyboardView.ShiftState.ON; oneShotShift = true }
        else if (oneShotShift) { kv.shift = KeyboardView.ShiftState.OFF; oneShotShift = false }
    }

    // ---------------------------------------------------------------- keyboard listener

    override fun onPress(key: Key) {
        if (prefs.vibrate) vibrate()
        if (prefs.sound) {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val fx = when (key.code) {
                Key.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                Key.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                Key.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            am?.playSoundEffect(fx, prefs.soundVolume)
        }
    }

    private fun vibrate() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(prefs.vibrateMs, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") v.vibrate(prefs.vibrateMs)
        } catch (_: Exception) {}
    }

    override fun onKey(key: Key, text: String) {
        if (key.code == Key.SPACE) { onSpace(); return }
        typeText(text)
    }

    private fun typeText(text: String) {
        val ic = currentInputConnection ?: return
        val isWordChar = text.isNotEmpty() && text.all { it.isLetterOrDigit() || it == Languages.OKINA[0] || it == Languages.TUTUQ[0] || it == '\'' || it == '-' }
        revert = null
        if (rawInput || noSuggest && mode != Mode.ALPHA) {
            ic.commitText(text, 1)
        } else if (isWordChar && mode == Mode.ALPHA || isWordChar && composing.isNotEmpty()) {
            composing.append(text)
            if (lang.id == "uz_new" && prefs.autoConvert) liveConvert()
            ic.setComposingText(composing, 1)
            refreshSuggestions()
        } else {
            commitSeparator(text)
        }
        if (oneShotShift && kv.shift == KeyboardView.ShiftState.ON) { kv.shift = KeyboardView.ShiftState.OFF; oneShotShift = false }
        if (!isWordChar) updateShift()
    }

    /** sh→ş, ch→ç, oʻ→ō, gʻ→ḡ while typing in the new alphabet. */
    private fun liveConvert() {
        val s = composing
        if (s.length < 2) return
        val a = s[s.length - 2]; val b = s[s.length - 1]
        val rep: String? = when {
            b.lowercaseChar() == 'h' && a.lowercaseChar() == 's' -> if (a.isUpperCase()) "Ş" else "ş"
            b.lowercaseChar() == 'h' && a.lowercaseChar() == 'c' -> if (a.isUpperCase()) "Ç" else "ç"
            (b == 'ʻ' || b == '`' || b == '‘') && a.lowercaseChar() == 'o' -> if (a.isUpperCase()) "Ō" else "ō"
            (b == 'ʻ' || b == '`' || b == '‘') && a.lowercaseChar() == 'g' -> if (a.isUpperCase()) "Ḡ" else "ḡ"
            else -> null
        }
        if (rep != null) { s.setLength(s.length - 2); s.append(rep) }
    }

    /** Converts "oʼ"/"o'" style apostrophes only when that yields a dictionary word. */
    private fun finalConvert(word: String): String {
        if (lang.id != "uz_new" || !prefs.autoConvert) return word
        if (!word.any { it == 'ʼ' || it == '\'' }) return word
        val conv = word.replace(Regex("([oOgG])['ʼ]")) { m ->
            when (m.groupValues[1]) { "o" -> "ō"; "O" -> "Ō"; "g" -> "ḡ"; else -> "Ḡ" }
        }
        return if (conv != word && engine.isKnown(conv.lowercase(lang.locale))) conv else word
    }

    private fun commitSeparator(sep: String) {
        val ic = currentInputConnection ?: return
        if (composing.isEmpty()) { ic.commitText(sep, 1); showIdleSuggestions(); return }
        var word = finalConvert(composing.toString())
        val original = word
        if (!noSuggest && prefs.autocorrect && sep.isNotEmpty() && sep.first().let { it == ' ' || it in ".,!?;:" }) {
            val r = engine.suggest(word, 3, true)
            val corr = r.autocorrect
            if (corr != null && !corr.equals(word, true)) word = corr
        }
        ic.commitText(word + sep, 1)
        if (word != original) revert = (word + sep) to original
        if (!password && prefs.learn && !noSuggest) engine.learn(word)
        composing.setLength(0)
        showIdleSuggestions()
    }

    private fun finishComposing() {
        if (composing.isEmpty()) return
        val ic = currentInputConnection
        val w = finalConvert(composing.toString())
        if (w != composing.toString()) ic?.setComposingText(w, 1)
        ic?.finishComposingText()
        if (!password && prefs.learn && !noSuggest) engine.learn(w)
        composing.setLength(0)
    }

    private fun onSpace() {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        if (prefs.doubleSpace && composing.isEmpty() && now - lastSpaceTime < 450 && !rawInput && mode == Mode.ALPHA) {
            val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                ic.deleteSurroundingText(1, 0); ic.commitText(". ", 1)
                lastSpaceTime = 0; revert = null; updateShift(); showIdleSuggestions(); return
            }
        }
        lastSpaceTime = now
        commitSeparator(" ")
        updateShift()
    }

    override fun onFunctional(code: Int) {
        val ic = currentInputConnection
        when (code) {
            Key.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                kv.shift = when {
                    kv.shift == KeyboardView.ShiftState.LOCKED -> KeyboardView.ShiftState.OFF
                    kv.shift == KeyboardView.ShiftState.ON && now - lastShiftTap < 300 -> KeyboardView.ShiftState.LOCKED
                    kv.shift == KeyboardView.ShiftState.ON -> KeyboardView.ShiftState.OFF
                    else -> KeyboardView.ShiftState.ON
                }
                oneShotShift = kv.shift == KeyboardView.ShiftState.ON
                lastShiftTap = now
            }
            Key.DELETE -> onDelete()
            Key.ENTER -> onEnter()
            Key.SYMBOLS -> { finishComposing(); setMode(Mode.SYMBOLS) }
            Key.SYMBOLS2 -> setMode(Mode.SYMBOLS2)
            Key.ALPHA -> setMode(Mode.ALPHA)
            Key.LANG -> nextLanguage()
            Key.EMOJI -> openEmoji()
            Key.SETTINGS -> launch(SettingsActivity::class.java)
            Key.SPACE -> onSpace()
            else -> ic?.let { }
        }
    }

    private fun setMode(m: Mode) {
        mode = m
        applySettings()
        if (m == Mode.ALPHA) { kv.shift = KeyboardView.ShiftState.OFF; oneShotShift = false; updateShift() }
    }

    private fun onDelete() {
        val ic = currentInputConnection ?: return
        revert?.let { (corrected, original) ->
            revert = null
            val before = ic.getTextBeforeCursor(corrected.length, 0)?.toString()
            if (before == corrected) {
                ic.deleteSurroundingText(corrected.length, 0)
                composing.setLength(0); composing.append(original)
                ic.setComposingText(composing, 1)
                if (prefs.learn && !password) engine.learn(original)
                refreshSuggestions(); return
            }
        }
        if (composing.isNotEmpty()) {
            val cp = Character.codePointBefore(composing, composing.length)
            composing.setLength(composing.length - Character.charCount(cp))
            if (composing.isEmpty()) { ic.commitText("", 1); showIdleSuggestions() }
            else { ic.setComposingText(composing, 1); refreshSuggestions() }
        } else {
            val sel = ic.getSelectedText(0)
            if (!sel.isNullOrEmpty()) ic.commitText("", 1)
            else {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                if (!noSuggest && mode == Mode.ALPHA) recomposePrevious()
            }
        }
        updateShift()
    }

    /** After deleting back into a word, make it editable (suggestions) again. */
    private fun recomposePrevious() {
        val ic = currentInputConnection ?: return
        if (Build.VERSION.SDK_INT < 24) return
        val before = ic.getTextBeforeCursor(40, 0)?.toString() ?: return
        val after = ic.getTextAfterCursor(1, 0)?.toString() ?: ""
        if (after.isNotEmpty() && after[0].isLetterOrDigit()) return
        var i = before.length
        while (i > 0 && (before[i - 1].isLetterOrDigit() || before[i - 1] == 'ʻ' || before[i - 1] == 'ʼ')) i--
        val word = before.substring(i)
        if (word.isEmpty() || word.length == before.length && before.length >= 40) return
        val selEnd = currentInputEditorInfo?.initialSelEnd ?: return
        val ex = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0) ?: return
        val end = ex.startOffset + ex.selectionEnd
        if (end < word.length || selEnd < 0) return
        ic.setComposingRegion(end - word.length, end)
        composing.setLength(0); composing.append(word)
        refreshSuggestions()
    }

    private fun onEnter() {
        val ic = currentInputConnection ?: return
        finishComposing()
        val info = currentInputEditorInfo
        val opts = info?.imeOptions ?: 0
        val action = opts and EditorInfo.IME_MASK_ACTION
        if (opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0 && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else if (info != null && info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0 && action == EditorInfo.IME_ACTION_UNSPECIFIED &&
            info.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        } else ic.commitText("\n", 1)
        revert = null
        updateShift(); showIdleSuggestions()
    }

    private fun nextLanguage() {
        finishComposing()
        val list = prefs.languages
        if (list.isEmpty()) return
        val idx = list.indexOf(lang.id)
        val next = list[(idx + 1).mod(list.size)]
        prefs.currentLanguage = next
        lang = Languages.byId(next); engine.load(lang)
        mode = Mode.ALPHA
        applySettings(); updateShift(); showIdleSuggestions()
    }

    override fun onPopupAction(token: String) {
        when (token) {
            Key.POP_EMOJI -> openEmoji()
            Key.POP_SETTINGS -> launch(SettingsActivity::class.java)
            Key.POP_CLIPBOARD -> openClipboard()
        }
    }

    override fun onCursorMove(steps: Int) {
        val ic = currentInputConnection ?: return
        finishComposing()
        val code = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(steps)) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    override fun onDeleteSwipe(words: Int) {
        val ic = currentInputConnection ?: return
        finishComposing()
        swipeWords = words
        val before = ic.getTextBeforeCursor(400, 0)?.toString() ?: return
        var i = before.length; var n = 0
        while (n < words && i > 0) {
            while (i > 0 && before[i - 1].isWhitespace()) i--
            while (i > 0 && !before[i - 1].isWhitespace()) i--
            n++
        }
        val ex = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0) ?: return
        val end = ex.startOffset + ex.selectionEnd
        val start = end - (before.length - i)
        ic.setSelection(start, end)
    }

    override fun onDeleteSwipeEnd(apply: Boolean) {
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)
        if (apply && !sel.isNullOrEmpty()) ic.commitText("", 1)
        else {
            val ex = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
            if (ex != null) { val e = ex.startOffset + ex.selectionEnd; ic.setSelection(e, e) }
        }
        swipeWords = 0
        updateShift(); showIdleSuggestions()
    }

    override fun onLongPressFunctional(code: Int): Boolean {
        if (code == Key.LANG || code == Key.SPACE) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
            return true
        }
        if (code == Key.ENTER || code == Key.SETTINGS) { launch(SettingsActivity::class.java); return true }
        return false
    }

    // ---------------------------------------------------------------- suggestions

    private fun refreshSuggestions() {
        if (!::strip.isInitialized) return
        if (noSuggest || !prefs.suggestions || composing.isEmpty()) { showIdleSuggestions(); return }
        val typed = composing.toString()
        val r = engine.suggest(typed, 3, prefs.autocorrect)
        strip.setSuggestions(r.words, if (prefs.autocorrect) r.autocorrect else null, typed)
    }

    private fun showIdleSuggestions() {
        if (!::strip.isInitialized) return
        strip.setSuggestions(emptyList(), null, "")
    }

    override fun onSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        revert = null
        if (composing.isEmpty()) { ic.commitText("$word ", 1) }
        else {
            ic.commitText("$word ", 1)
            if (prefs.learn && !password) engine.learn(word)
            composing.setLength(0)
        }
        updateShift(); showIdleSuggestions()
    }

    override fun onToolbar(action: SuggestionStrip.Action) {
        when (action) {
            SuggestionStrip.Action.SETTINGS -> launch(SettingsActivity::class.java)
            SuggestionStrip.Action.THEME -> launch(ThemesActivity::class.java)
            SuggestionStrip.Action.EMOJI -> openEmoji()
            SuggestionStrip.Action.CLIPBOARD -> openClipboard()
            SuggestionStrip.Action.NUMBERS -> { prefs.toggleNumberRow(); applySettings() }
            SuggestionStrip.Action.CONVERT -> { finishComposing(); strip.showConvert() }
            SuggestionStrip.Action.HIDE -> requestHideSelf(0)
            SuggestionStrip.Action.BACK -> { closePanels(); strip.showToolbar() }
        }
    }

    override fun onConvert(target: String) {
        val ic = currentInputConnection ?: return
        finishComposing()
        val sel = ic.getSelectedText(0)?.toString()
        var done = false
        if (!sel.isNullOrEmpty()) {
            val out = Transliterator.convert(sel, target)
            if (out != sel) { ic.commitText(out, 1); done = true }
        } else {
            val before = ic.getTextBeforeCursor(5000, 0)?.toString() ?: ""
            val after = ic.getTextAfterCursor(5000, 0)?.toString() ?: ""
            val b2 = Transliterator.convert(before, target); val a2 = Transliterator.convert(after, target)
            if (b2 != before || a2 != after) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(before.length, after.length)
                ic.commitText(b2, 1); ic.commitText(a2, 0)
                ic.endBatchEdit(); done = true
            }
        }
        Toast.makeText(this, if (done) R.string.converted else R.string.nothing_to_convert, Toast.LENGTH_SHORT).show()
        strip.showToolbar()
    }

    // ---------------------------------------------------------------- panels

    private fun openEmoji() {
        finishComposing()
        clipPanel.visibility = View.GONE
        emoji.visibility = View.VISIBLE; emoji.open()
        kv.cancelAll(); kv.visibility = View.INVISIBLE
    }

    private fun openClipboard() {
        finishComposing()
        clipboard?.let { clipHistory.capture(it) }
        emoji.visibility = View.GONE
        clipPanel.visibility = View.VISIBLE; clipPanel.refresh()
        kv.cancelAll(); kv.visibility = View.INVISIBLE
    }

    private fun closePanels() {
        if (!::kv.isInitialized) return
        emoji.visibility = View.GONE; clipPanel.visibility = View.GONE
        kv.visibility = View.VISIBLE
    }

    override fun onEmoji(emoji: String) { currentInputConnection?.commitText(emoji, 1) }

    override fun onEmojiDelete() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
        val n = if (before.length == 2 && Character.isSurrogatePair(before[0], before[1])) 2 else 1
        if (before.isNotEmpty()) ic.deleteSurroundingText(minOf(n, before.length), 0)
    }

    override fun onEmojiClose() { closePanels() }

    override fun onClipPaste(text: String) { currentInputConnection?.commitText(text, 1); closePanels() }

    override fun onClipClose() { closePanels() }

    private fun launch(cls: Class<*>) {
        finishComposing()
        try { startActivity(Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        requestHideSelf(0)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (layoutDirty || styleDirty) applySettings()
    }
}
