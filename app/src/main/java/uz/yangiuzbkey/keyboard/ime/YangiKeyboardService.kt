package uz.yangiuzbkey.keyboard.ime

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import uz.yangiuzbkey.keyboard.keyboard.AnchorFrame
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
    EmojiPanel.Listener, ClipboardPanel.Listener, ToolbarEditPanel.Listener, EditPanel.Listener, SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var engine: SuggestionEngine
    private lateinit var clipHistory: ClipboardHistory
    private var clipboard: ClipboardManager? = null

    private var root: ThemedFrame? = null
    private lateinit var strip: SuggestionStrip
    private lateinit var kv: KeyboardView
    private lateinit var emoji: EmojiPanel
    private lateinit var clipPanel: ClipboardPanel
    private lateinit var tbEdit: ToolbarEditPanel
    private lateinit var editPanel: EditPanel
    private var recognizer: SpeechRecognizer? = null
    private lateinit var overlay: OverlayView

    private var lang: Language = Languages.all.first()
    private var mode = Mode.ALPHA
    private var variant = FieldVariant.NORMAL
    private var password = false
    private var noSuggest = false
    private var rawInput = false
    private var incognito = false

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
    private var navInset = 0

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
        if (key == "learned_reload") engine.reload(lang)
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View {
        val frame = ThemedFrame(this).apply { anchored = true }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        strip = SuggestionStrip(this).also { it.listener = this }
        val body = AnchorFrame(this)
        overlay = OverlayView(this)
        kv = KeyboardView(this).also { it.listener = this; it.overlay = overlay }
        emoji = EmojiPanel(this).also { it.listener = this; it.visibility = View.GONE }
        clipPanel = ClipboardPanel(this, clipHistory).also { it.listener = this; it.visibility = View.GONE }
        val mp = ViewGroup.LayoutParams.MATCH_PARENT
        body.addView(kv, FrameLayout.LayoutParams(mp, ViewGroup.LayoutParams.WRAP_CONTENT))
        body.addView(emoji, FrameLayout.LayoutParams(mp, mp))
        body.addView(clipPanel, FrameLayout.LayoutParams(mp, mp))
        tbEdit = ToolbarEditPanel(this).also { it.listener = this; it.visibility = View.GONE }
        body.addView(tbEdit, FrameLayout.LayoutParams(mp, mp))
        editPanel = EditPanel(this).also { it.listener = this; it.visibility = View.GONE }
        body.addView(editPanel, FrameLayout.LayoutParams(mp, mp))
        column.addView(strip, LinearLayout.LayoutParams(mp, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(body, LinearLayout.LayoutParams(mp, ViewGroup.LayoutParams.WRAP_CONTENT))
        frame.addView(column, FrameLayout.LayoutParams(mp, ViewGroup.LayoutParams.WRAP_CONTENT))
        frame.addView(overlay, FrameLayout.LayoutParams(mp, mp))
        if (Build.VERSION.SDK_INT >= 20) {
            frame.setOnApplyWindowInsetsListener { _, insets ->
                navInset = computeBottom(insets)
                applyBottomPadding()
                insets
            }
        }
        root = frame
        layoutDirty = true; styleDirty = true
        return frame
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        if (!restarting) lastWord = ""
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
        incognito = prefs.incognito || info.imeOptions and 0x1000000 != 0
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

    /**
     * Bottom space the system needs below the keys. Gesture-nav phones (Honor,
     * Xiaomi, Pixel on Android 15+) draw a back / keyboard-switch button row
     * over the IME that is taller than the reported navigation bar inset, so
     * we take the largest of every bottom inset and the system bar frame height.
     */
    private fun computeBottom(insets: android.view.WindowInsets): Int {
        var bottom = when {
            Build.VERSION.SDK_INT >= 30 -> {
                maxOf(insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom,
                    insets.getInsets(android.view.WindowInsets.Type.tappableElement()).bottom,
                    insets.getInsets(android.view.WindowInsets.Type.mandatorySystemGestures()).bottom)
            }
            Build.VERSION.SDK_INT >= 29 -> @Suppress("DEPRECATION") maxOf(insets.systemWindowInsetBottom,
                insets.tappableElementInsets.bottom, insets.mandatorySystemGestureInsets.bottom)
            else -> @Suppress("DEPRECATION") insets.systemWindowInsetBottom
        }
        if (prefs.navSafe && bottom > 0) bottom = maxOf(bottom, navFrameHeight())
        return bottom
    }

    private fun navFrameHeight(): Int {
        for (name in arrayOf("navigation_bar_frame_height", "navigation_bar_height")) {
            val id = resources.getIdentifier(name, "dimen", "android")
            if (id != 0) {
                val h = try { resources.getDimensionPixelSize(id) } catch (_: Exception) { 0 }
                if (h > 0) return h
            }
        }
        return 0
    }

    private fun applyBottomPadding() {
        val v = root ?: return
        val bottom = navInset + (prefs.bottomGap * resources.displayMetrics.density).toInt()
        if (v.paddingBottom != bottom) v.setPadding(0, 0, 0, bottom)
    }

    /** Paints the system navigation bar in the keyboard colour so buttons stay visible. */
    private fun applyNavBarColor(bg: Int) {
        val w = window?.window ?: return
        try {
            if (Build.VERSION.SDK_INT >= 29) w.isNavigationBarContrastEnforced = false
            @Suppress("DEPRECATION")
            w.navigationBarColor = bg
            val light = (0.299 * ((bg shr 16) and 0xFF) + 0.587 * ((bg shr 8) and 0xFF) + 0.114 * (bg and 0xFF)) / 255 > 0.6
            if (Build.VERSION.SDK_INT >= 30) {
                val f = android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                w.insetsController?.setSystemBarsAppearance(if (light) f else 0, f)
            } else if (Build.VERSION.SDK_INT >= 26) {
                @Suppress("DEPRECATION")
                val flags = w.decorView.systemUiVisibility
                @Suppress("DEPRECATION")
                w.decorView.systemUiVisibility = if (light) flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    else flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
        } catch (_: Exception) {}
    }

    // ---------------------------------------------------------------- setup

    private fun applySettings(force: Boolean = false) {
        if (!::kv.isInitialized) return
        if (styleDirty || force) {
            val st = ThemeManager.resolve(this, prefs)
            root?.style = st; kv.setStyle(st); strip.style = st
            emoji.setStyle(st); clipPanel.setStyle(st); tbEdit.setStyle(st); editPanel.setStyle(st)
            applyNavBarColor(st.theme.bg)
            strip.invalidate(); root?.invalidate()
            styleDirty = false
        }
        kv.heightPercent = prefs.heightPercent
        kv.longPressMs = prefs.longPressMs
        kv.keyPopup = prefs.keyPopup
        kv.spaceCursor = prefs.spaceCursor
        kv.spaceLang = prefs.spaceLang && prefs.languages.size > 1
        kv.deleteSwipe = prefs.deleteSwipe
        kv.locale = lang.locale
        strip.numberRowOn = prefs.numberRow
        strip.toolbarEnabled = prefs.toolbar
        strip.items = SuggestionStrip.parse(prefs.toolbarItems)
        strip.activeActions = buildSet {
            if (incognito) add(SuggestionStrip.Action.INCOGNITO)
            if (prefs.oneHand != "off") add(SuggestionStrip.Action.ONE_HAND)
        }
        kv.glide = prefs.glide && !password && !noSuggest && mode == Mode.ALPHA
        kv.oneHand = when (prefs.oneHand) { "left" -> -1; "right" -> 1; else -> 0 }
        kv.oneHandFrac = prefs.oneHandWidth / 100f
        applyBottomPadding()
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

    /** sh→ş, ch→ç, oʻ→ö, gʻ→ğ while typing in the new alphabet. */
    private fun liveConvert() {
        val s = composing
        if (s.length < 2) return
        val a = s[s.length - 2]; val b = s[s.length - 1]
        val rep: String? = when {
            b.lowercaseChar() == 'h' && a.lowercaseChar() == 's' -> if (a.isUpperCase()) "Ş" else "ş"
            b.lowercaseChar() == 'h' && a.lowercaseChar() == 'c' -> if (a.isUpperCase()) "Ç" else "ç"
            (b == 'ʻ' || b == '`' || b == '‘') && a.lowercaseChar() == 'o' -> if (a.isUpperCase()) "Ö" else "ö"
            (b == 'ʻ' || b == '`' || b == '‘') && a.lowercaseChar() == 'g' -> if (a.isUpperCase()) "Ğ" else "ğ"
            else -> null
        }
        if (rep != null) { s.setLength(s.length - 2); s.append(rep) }
    }

    /** Converts "oʼ"/"o'" style apostrophes only when that yields a dictionary word. */
    private fun finalConvert(word: String): String {
        if (lang.id != "uz_new" || !prefs.autoConvert) return word
        if (!word.any { it == 'ʼ' || it == '\'' }) return word
        val conv = word.replace(Regex("([oOgG])['ʼ]")) { m ->
            when (m.groupValues[1]) { "o" -> "ö"; "O" -> "Ö"; "g" -> "ğ"; else -> "Ğ" }
        }
        return if (conv != word && engine.isKnown(conv.lowercase(lang.locale))) conv else word
    }

    private fun commitSeparator(sep: String) {
        val ic = currentInputConnection ?: return
        if (composing.isEmpty()) { ic.commitText(sep, 1); showIdleSuggestions(); return }
        var word = finalConvert(composing.toString())
        val original = word
        val sepOk = sep.isNotEmpty() && sep.first().let { it == ' ' || it in ".,!?;:\n" }
        var expanded = false
        if (sepOk && !password) prefs.shortcutMap[word.lowercase(lang.locale)]?.let { word = it; expanded = true }
        if (!expanded && !password && prefs.uzSpell && lang.id == "uz_latn") word = fixUzApostrophe(word)
        if (!expanded && !noSuggest && prefs.autocorrect && sep.isNotEmpty() && sep.first().let { it == ' ' || it in ".,!?;:" }) {
            val r = engine.suggest(word, 3, true)
            val corr = r.autocorrect
            if (corr != null && !corr.equals(word, true)) word = corr
        }
        ic.commitText(word + sep, 1)
        if (word != original) revert = (word + sep) to original
        if (!password && prefs.learn && !noSuggest && !incognito && !expanded) { engine.learnPair(lastWord, word); engine.learn(word) }
        lastWord = if (sep.isNotEmpty() && sep.first() in ".!?\n") "" else word
        composing.setLength(0)
        showIdleSuggestions()
    }

    private fun finishComposing() {
        if (composing.isEmpty()) return
        val ic = currentInputConnection
        val w = finalConvert(composing.toString())
        if (w != composing.toString()) ic?.setComposingText(w, 1)
        ic?.finishComposingText()
        if (!password && prefs.learn && !noSuggest && !incognito) engine.learn(w)
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
                if (prefs.learn && !password && !incognito) engine.learn(original)
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

    private fun nextLanguage(step: Int = 1) {
        finishComposing()
        val list = prefs.languages
        if (list.isEmpty()) return
        val idx = list.indexOf(lang.id)
        val next = list[(idx + step).mod(list.size)]
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

    override fun onSpaceSwipe(dir: Int) { nextLanguage(dir) }

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

    /** Last committed word in this field, used for next-word prediction. */
    private var lastWord = ""

    private fun showIdleSuggestions() {
        if (!::strip.isInitialized) return
        if (noSuggest || password || !prefs.suggestions) { strip.setSuggestions(emptyList(), null, ""); return }
        val before = currentInputConnection?.getTextBeforeCursor(64, 0)?.toString() ?: ""
        // Predict only right after "word " - not mid-word and not after a sentence end.
        val prev = PREV_WORD.find(before)?.groupValues?.get(1) ?: ""
        val next = if (prev.isEmpty()) emptyList() else engine.predict(prev, 3)
        strip.setSuggestions(next, null, null)
    }

    override fun onSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        revert = null
        if (composing.isEmpty()) {
            ic.commitText("$word ", 1)
            if (prefs.learn && !password && !incognito) engine.learnPair(lastWord, word)
        }
        else {
            ic.commitText("$word ", 1)
            if (prefs.learn && !password && !incognito) { engine.learnPair(lastWord, word); engine.learn(word) }
            composing.setLength(0)
        }
        lastWord = word
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
            SuggestionStrip.Action.UNDO -> ctrlKey(KeyEvent.KEYCODE_Z, false)
            SuggestionStrip.Action.REDO -> ctrlKey(KeyEvent.KEYCODE_Z, true)
            SuggestionStrip.Action.SELECT_ALL -> contextAction(android.R.id.selectAll)
            SuggestionStrip.Action.COPY -> contextAction(android.R.id.copy)
            SuggestionStrip.Action.CUT -> contextAction(android.R.id.cut)
            SuggestionStrip.Action.PASTE -> contextAction(android.R.id.paste)
            SuggestionStrip.Action.CURSOR_LEFT -> { finishComposing(); sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT) }
            SuggestionStrip.Action.CURSOR_RIGHT -> { finishComposing(); sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT) }
            SuggestionStrip.Action.LANGUAGE -> { finishComposing(); nextLanguage() }
            SuggestionStrip.Action.KEYBOARD_PICK ->
                (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
            SuggestionStrip.Action.MIC -> startVoice()
            SuggestionStrip.Action.EDIT_PANEL -> openEditPanel()
            SuggestionStrip.Action.ONE_HAND -> {
                prefs.oneHand = when (prefs.oneHand) { "off" -> "right"; "right" -> "left"; else -> "off" }
                applySettings()
            }
            SuggestionStrip.Action.INCOGNITO -> {
                prefs.incognito = !prefs.incognito
                incognito = prefs.incognito || (currentInputEditorInfo?.imeOptions ?: 0) and 0x1000000 != 0
                Toast.makeText(this, if (incognito) R.string.incognito_on else R.string.incognito_off, Toast.LENGTH_SHORT).show()
                applySettings()
            }
        }
    }

    private fun ctrlKey(code: Int, shift: Boolean) {
        val ic = currentInputConnection ?: return
        finishComposing()
        val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or (if (shift) KeyEvent.META_SHIFT_ON else 0)
        val t = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta))
    }

    private fun contextAction(id: Int) {
        finishComposing()
        currentInputConnection?.performContextMenuAction(id)
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
        clipPanel.visibility = View.GONE; tbEdit.visibility = View.GONE; editPanel.visibility = View.GONE
        emoji.visibility = View.VISIBLE; emoji.open()
        kv.cancelAll(); kv.visibility = View.INVISIBLE
    }

    private fun openClipboard() {
        finishComposing()
        clipboard?.let { clipHistory.capture(it) }
        emoji.visibility = View.GONE; tbEdit.visibility = View.GONE; editPanel.visibility = View.GONE
        clipPanel.visibility = View.VISIBLE; clipPanel.refresh()
        kv.cancelAll(); kv.visibility = View.INVISIBLE
    }

    private fun closePanels() {
        if (!::kv.isInitialized) return
        emoji.visibility = View.GONE; clipPanel.visibility = View.GONE; tbEdit.visibility = View.GONE
        editPanel.visibility = View.GONE
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

    override fun onImagePaste(uri: android.net.Uri) {
        val store = ScreenshotStore(this)
        val shot = store.recent().firstOrNull { it.uri == uri }
        val shared = shot?.let { store.share(it) }
        if (shared == null) { closePanels(); return }
        val ei = currentInputEditorInfo
        val ic = currentInputConnection
        val accepts = ei != null && androidx.core.view.inputmethod.EditorInfoCompat.getContentMimeTypes(ei)
            .any { android.content.ClipDescription.compareMimeTypes("image/png", it) }
        var sent = false
        if (accepts && ic != null) {
            val info = androidx.core.view.inputmethod.InputContentInfoCompat(shared,
                android.content.ClipDescription("Screenshot", arrayOf("image/png")), null)
            val flags = if (Build.VERSION.SDK_INT >= 25)
                androidx.core.view.inputmethod.InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION else 0
            sent = androidx.core.view.inputmethod.InputConnectionCompat.commitContent(ic, ei!!, info, flags, null)
        }
        if (!sent) {
            try {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newUri(contentResolver, "Screenshot", shared))
                Toast.makeText(this, R.string.clip_image_copied, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) { }
        }
        closePanels()
    }

    override fun onClipPaste(text: String) { currentInputConnection?.commitText(text, 1); closePanels() }

    override fun onClipClose() { closePanels() }

    override fun onToolbarEdit() {
        if (!::kv.isInitialized) return
        finishComposing()
        emoji.visibility = View.GONE; clipPanel.visibility = View.GONE; editPanel.visibility = View.GONE
        tbEdit.visibility = View.VISIBLE; tbEdit.open(strip.items)
        kv.cancelAll(); kv.visibility = View.INVISIBLE
    }

    override fun onToolbarEditDone(items: List<SuggestionStrip.Action>) {
        prefs.toolbarItems = items.map { it.name }
        strip.items = items
        strip.showToolbar()
        closePanels()
    }

    private fun launch(cls: Class<*>) {
        finishComposing()
        try { startActivity(Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        requestHideSelf(0)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (layoutDirty || styleDirty) applySettings()
    }

    // ---------------------------------------------------------------- extras

    private fun fixUzApostrophe(w: String): String {
        if (w.length < 2) return w
        return UZ_APOS.replace(w) { m -> m.groupValues[1] + "\u02BB" }
    }

    private fun startVoice() {
        finishComposing()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            try {
                startActivity(Intent(this, uz.yangiuzbkey.keyboard.settings.VoicePermissionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {}
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_LONG).show(); return
        }
        recognizer?.destroy()
        val rec = try { SpeechRecognizer.createSpeechRecognizer(this) } catch (_: Exception) { null }
        if (rec == null) { Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_LONG).show(); return }
        recognizer = rec
        val tag = when {
            lang.id.startsWith("uz") -> "uz-UZ"
            lang.id.startsWith("ru") -> "ru-RU"
            lang.id.startsWith("tr") -> "tr-TR"
            else -> "en-US"
        }
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(this@YangiKeyboardService, R.string.voice_speak, Toast.LENGTH_SHORT).show()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                    Toast.makeText(this@YangiKeyboardService, R.string.voice_nomatch, Toast.LENGTH_SHORT).show()
                else Toast.makeText(this@YangiKeyboardService, R.string.voice_unavailable, Toast.LENGTH_SHORT).show()
                rec.destroy(); if (recognizer === rec) recognizer = null
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                val ic = currentInputConnection
                if (!text.isNullOrBlank() && ic != null) {
                    val before = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
                    val lead = if (before.isEmpty() || before.last().isWhitespace()) "" else " "
                    ic.commitText(lead + text, 1)
                    updateShift(); showIdleSuggestions()
                }
                rec.destroy(); if (recognizer === rec) recognizer = null
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        try { rec.startListening(intent) } catch (_: Exception) {
            Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun openEditPanel() {
        if (!::kv.isInitialized) return
        finishComposing()
        emoji.visibility = View.GONE; clipPanel.visibility = View.GONE; tbEdit.visibility = View.GONE
        editPanel.visibility = View.VISIBLE; editPanel.open()
        kv.cancelAll(); kv.visibility = View.INVISIBLE
    }

    override fun onEditAction(id: String) {
        val ic = currentInputConnection ?: return
        finishComposing()
        fun arrow(code: Int) {
            val meta = if (editPanel.selecting) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
            val t = SystemClock.uptimeMillis()
            ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta))
            ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta))
        }
        when (id) {
            "left" -> arrow(KeyEvent.KEYCODE_DPAD_LEFT)
            "right" -> arrow(KeyEvent.KEYCODE_DPAD_RIGHT)
            "up" -> arrow(KeyEvent.KEYCODE_DPAD_UP)
            "down" -> arrow(KeyEvent.KEYCODE_DPAD_DOWN)
            "home" -> arrow(KeyEvent.KEYCODE_MOVE_HOME)
            "end" -> arrow(KeyEvent.KEYCODE_MOVE_END)
            "select_all" -> contextAction(android.R.id.selectAll)
            "copy" -> { contextAction(android.R.id.copy); editPanel.selecting = false }
            "cut" -> { contextAction(android.R.id.cut); editPanel.selecting = false }
            "paste" -> contextAction(android.R.id.paste)
            "undo" -> ctrlKey(KeyEvent.KEYCODE_Z, false)
            "redo" -> ctrlKey(KeyEvent.KEYCODE_Z, true)
            "delete" -> {
                val sel = ic.getSelectedText(0)
                if (!sel.isNullOrEmpty()) ic.commitText("", 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            }
        }
    }

    override fun onEditClose() { closePanels(); strip.showToolbar() }

    override fun onGlide(path: String) {
        val ic = currentInputConnection ?: return
        finishComposing()
        revert = null
        val list = engine.glide(path, 4)
        if (list.isEmpty()) return
        var best = list.first()
        if (kv.shift != KeyboardView.ShiftState.OFF) {
            best = if (kv.shift == KeyboardView.ShiftState.LOCKED) best.uppercase(lang.locale)
                   else best.replaceFirstChar { it.titlecase(lang.locale) }
        }
        val before = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
        val lead = if (before.isEmpty() || before.last().isWhitespace() || before.last() in "([\"«") "" else " "
        ic.commitText(lead + best, 1)
        if (prefs.learn && !incognito) engine.learnPair(lastWord, list.first())
        lastWord = list.first()
        if (oneShotShift) { kv.shift = KeyboardView.ShiftState.OFF; oneShotShift = false }
        glideWord = lead + best
        strip.setSuggestions(list.drop(1), null, null)
    }

    private var glideWord: String? = null

    private companion object {
        val UZ_APOS = Regex("([oOgG])['ʼ`‘’]")
        val PREV_WORD = Regex("([\\p{L}'ʼ‘’-]+) $")
    }
}
