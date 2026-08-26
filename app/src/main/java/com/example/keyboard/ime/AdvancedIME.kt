package com.example.keyboard.ime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.core.content.ContextCompat
import com.example.keyboard.R
import com.example.keyboard.clipboard.ClipboardStore
import com.example.keyboard.emoji.EmojiData
import com.example.keyboard.input.VietnameseEngine
import com.example.keyboard.settings.Prefs
import com.example.keyboard.util.Feedback
import java.util.Locale

class AdvancedIME : InputMethodService() {

    private lateinit var prefs: Prefs
    private lateinit var engine: VietnameseEngine
    private lateinit var clips: ClipboardStore

    private var shiftOn = false
    private var capsLock = false
    private var symbolMode = false
    private var panelMode = Panel.NONE
    private var composingLen = 0
    private var root: View? = null
    private var speech: SpeechRecognizer? = null
    private var listening = false

    // Floating drag
    private var floatDx = 0f
    private var floatDy = 0f

    private enum class Panel { NONE, EMOJI, CLIP, CALC }

    private val qwerty = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0"),
        listOf("q","w","e","r","t","y","u","i","o","p"),
        listOf("a","s","d","f","g","h","j","k","l"),
        listOf("⇧","z","x","c","v","b","n","m","⌫"),
        listOf("?123","🌐","😊","␣",",",".","↵")
    )
    private val symbols = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0"),
        listOf("@","#","$","%","&","*","-","+","(",")"),
        listOf("\"","'",":",";","!","?","/","\\","=","_"),
        listOf("ABC","€","¥","₫","•","~","^","[","]","⌫"),
        listOf("ABC","🌐","😊","␣",",",".","↵")
    )

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        engine = VietnameseEngine().also { it.mode = prefs.inputMode }
        clips = ClipboardStore(this)
        Feedback.init(this)
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.addPrimaryClipChangedListener {
            val t = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (!t.isNullOrBlank() && !isSecureEditor()) clips.add(t)
        }
    }

    override fun onDestroy() {
        stopVoice()
        Feedback.release()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        root = layoutInflater.inflate(R.layout.keyboard_root, null)
        // Nền cửa sổ IME không trong suốt → tránh khoảng trống lộ activity phía dưới
        window?.window?.setBackgroundDrawableResource(R.color.keyboard_bg)
        bindToolbar()
        applyBackground()
        applyOneHandLayout()
        applyHeight()
        rebuildKeyboard()
        return root!!
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        engine.mode = if (isSecureEditor()) VietnameseEngine.Mode.OFF else prefs.inputMode
        engine.reset(); composingLen = 0
        panelMode = Panel.NONE
        // Tắt floating mỗi lần mở lại để tránh bàn phím bị kéo lệch
        if (prefs.floating) {
            prefs.floating = false
        }
        root?.findViewById<View>(R.id.contentColumn)?.apply {
            translationX = 0f
            translationY = 0f
            setOnTouchListener(null)
        }
        applyBackground()
        applyOneHandLayout()
        applyHeight()
        rebuildKeyboard()
    }

    override fun onComputeInsets(outInsets: InputMethodService.Insets) {
        super.onComputeInsets(outInsets)
        val r = root ?: return
        // Content nằm ngay trên bàn phím, không chừa khoảng trống trong vùng IME
        outInsets.contentTopInsets = outInsets.visibleTopInsets
        outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_CONTENT
        val loc = IntArray(2)
        r.getLocationInWindow(loc)
        outInsets.touchableRegion.set(0, loc[1], r.width, loc[1] + r.height)
    }

    private fun isSecureEditor(): Boolean {
        val t = currentInputEditorInfo?.inputType ?: return false
        val v = t and EditorInfo.TYPE_MASK_VARIATION
        return v == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
            v == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            v == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            v == EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun bindToolbar() {
        val r = root ?: return
        r.findViewById<View>(R.id.btnToolbarSelectAll)?.setOnClickListener {
            currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
        }
        r.findViewById<View>(R.id.btnToolbarCopy)?.setOnClickListener { sendDownUp(KeyEvent.KEYCODE_COPY) }
        r.findViewById<View>(R.id.btnToolbarPaste)?.setOnClickListener {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val t = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (!t.isNullOrBlank()) currentInputConnection?.commitText(t, 1)
        }
        r.findViewById<View>(R.id.btnToolbarClip)?.setOnClickListener {
            panelMode = if (panelMode == Panel.CLIP) Panel.NONE else Panel.CLIP
            rebuildKeyboard()
        }
        r.findViewById<View>(R.id.btnToolbarEmoji)?.setOnClickListener {
            panelMode = if (panelMode == Panel.EMOJI) Panel.NONE else Panel.EMOJI
            rebuildKeyboard()
        }
        r.findViewById<View>(R.id.btnToolbarCalc)?.setOnClickListener {
            panelMode = if (panelMode == Panel.CALC) Panel.NONE else Panel.CALC
            rebuildKeyboard()
        }
        r.findViewById<View>(R.id.btnToolbarMic)?.setOnClickListener { toggleVoice() }
        r.findViewById<View>(R.id.btnToolbarOneHand)?.setOnClickListener { cycleOneHand() }
        r.findViewById<View>(R.id.btnToolbarFloat)?.setOnClickListener {
            prefs.floating = !prefs.floating
            Toast.makeText(this, if (prefs.floating) "Floating: bật (kéo thanh công cụ)" else "Floating: tắt", Toast.LENGTH_SHORT).show()
            setupFloatingDrag()
        }
        r.findViewById<View>(R.id.btnHide)?.setOnClickListener { requestHideSelf(0) }

        r.findViewById<View>(R.id.btnOhLeft)?.setOnClickListener {
            prefs.oneHanded = "left"; applyOneHandLayout(); rebuildKeyboard()
        }
        r.findViewById<View>(R.id.btnOhRight)?.setOnClickListener {
            prefs.oneHanded = "right"; applyOneHandLayout(); rebuildKeyboard()
        }
        r.findViewById<View>(R.id.btnOhFull)?.setOnClickListener {
            prefs.oneHanded = "off"; applyOneHandLayout(); rebuildKeyboard()
        }
        setupFloatingDrag()
    }

    private fun setupFloatingDrag() {
        val r = root ?: return
        val toolbar = (r as? ViewGroup)?.getChildAt(0) // not reliable
        // Drag on whole content when floating
        val col = r.findViewById<View>(R.id.contentColumn) ?: return
        if (!prefs.floating) {
            col.setOnTouchListener(null)
            col.translationX = 0f
            col.translationY = 0f
            return
        }
        col.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    floatDx = v.x - e.rawX
                    floatDy = v.y - e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    v.animate().x(e.rawX + floatDx).y(e.rawY + floatDy).setDuration(0).start()
                    true
                }
                else -> false
            }
        }
    }

    private fun cycleOneHand() {
        prefs.oneHanded = when (prefs.oneHanded) {
            "off" -> "right"
            "right" -> "left"
            else -> "off"
        }
        applyOneHandLayout()
        rebuildKeyboard()
        Toast.makeText(this, "Một tay: ${prefs.oneHanded}", Toast.LENGTH_SHORT).show()
    }

    private fun applyOneHandLayout() {
        val r = root ?: return
        val bar = r.findViewById<View>(R.id.oneHandBar)
        val area = r.findViewById<View>(R.id.keyboardArea)
        val align = r.findViewById<LinearLayout>(R.id.keyboardAlign)
        val mode = prefs.oneHanded
        if (mode == "off") {
            bar?.visibility = View.GONE
            area?.layoutParams = area.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
            align?.gravity = Gravity.CENTER
        } else {
            bar?.visibility = View.VISIBLE
            val w = (resources.displayMetrics.widthPixels * 0.72f).toInt()
            area?.layoutParams = area.layoutParams.apply { width = w }
            align?.gravity = if (mode == "left") Gravity.START else Gravity.END
        }
        area?.requestLayout()
    }

    private fun applyHeight() {
        val kb = root?.findViewById<View>(R.id.keyboardArea) ?: return
        val hDp = when (prefs.heightLevel) { 0 -> 220; 2 -> 320; else -> 270 }
        val hPx = (hDp * resources.displayMetrics.density).toInt()
        val lp = kb.layoutParams
        if (lp.height != hPx) {
            lp.height = hPx
            kb.layoutParams = lp
            kb.requestLayout()
            root?.requestLayout()
        }
    }

    private fun applyBackground() {
        val r = root ?: return
        val img = r.findViewById<ImageView>(R.id.bgImage)
        val overlay = r.findViewById<View>(R.id.bgOverlay)
        val uri = prefs.bgImageUri
        if (uri.isNotBlank()) {
            try {
                contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    img?.setImageDrawable(BitmapDrawable(resources, bmp))
                    img?.visibility = View.VISIBLE
                }
            } catch (_: Exception) {
                img?.visibility = View.GONE
            }
        } else {
            img?.visibility = View.GONE
        }
        val alpha = (prefs.bgOpacity.coerceIn(0.3f, 1f) * 255).toInt()
        val base = ContextCompat.getColor(this, R.color.keyboard_bg)
        overlay?.setBackgroundColor(Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base)))
    }

    private fun rebuildKeyboard() {
        val r = root ?: return
        val panel = r.findViewById<FrameLayout>(R.id.panelContainer)
        val rows = r.findViewById<LinearLayout>(R.id.rowsContainer) ?: return
        val kbArea = r.findViewById<View>(R.id.keyboardArea)
        rows.removeAllViews()
        panel?.removeAllViews()
        panel?.visibility = View.GONE
        panel?.layoutParams = panel?.layoutParams?.apply { height = 0 }

        when (panelMode) {
            Panel.EMOJI, Panel.CLIP, Panel.CALC -> {
                val panelH = (260 * resources.displayMetrics.density).toInt()
                panel?.layoutParams = panel?.layoutParams?.apply { height = panelH }
                panel?.visibility = View.VISIBLE
                kbArea?.visibility = View.GONE
                when (panelMode) {
                    Panel.EMOJI -> panel?.addView(buildEmojiPanel())
                    Panel.CLIP -> panel?.addView(buildClipPanel())
                    Panel.CALC -> panel?.addView(buildCalcPanel())
                    else -> {}
                }
                r.requestLayout()
                return
            }
            else -> {
                kbArea?.visibility = View.VISIBLE
            }
        }

        val layout = if (symbolMode) symbols else qwerty
        val start = if (prefs.showNumberRow) 0 else 1
        for (i in start until layout.size) rows.addView(buildRow(layout[i]))
        if (prefs.showArrowKeys) rows.addView(buildRow(listOf("←","↑","↓","→")))
        r.requestLayout()
    }

    private fun buildRow(keys: List<String>): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply { setMargins(0, 4, 0, 4) }
        }
        for (key in keys) {
            if (key.isEmpty()) continue
            val label = displayLabel(key)
            val tv = TextView(this).apply {
                text = label
                textSize = prefs.fontSizeSp
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(
                    if (key == "↵") Color.WHITE
                    else ContextCompat.getColor(this@AdvancedIME, R.color.key_text)
                )
                background = ContextCompat.getDrawable(
                    this@AdvancedIME,
                    when (key) {
                        "↵" -> R.drawable.key_enter
                        "⇧","⌫","?123","ABC","␣","🌐","😊" -> R.drawable.key_special
                        else -> R.drawable.key_bg
                    }
                )
                elevation = 2f
                val w = when (key) {
                    "␣" -> 4.2f
                    "⇧","⌫","?123","ABC","↵","🌐","😊" -> 1.55f
                    else -> 1f
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, w).apply {
                    setMargins(4, 0, 4, 0)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { onKey(key) }
                if (key == "⌫") {
                    setOnLongClickListener {
                        currentInputConnection?.deleteSurroundingText(Int.MAX_VALUE, 0)
                        engine.reset(); composingLen = 0; true
                    }
                }
                if (key.length == 1 && key[0].isLetter()) {
                    setOnLongClickListener { showLongPressPopup(key, this); true }
                }
                if (key == "␣") setOnTouchListener(SpaceSwipeListener())
            }
            row.addView(tv)
        }
        return row
    }

    private fun displayLabel(key: String): String {
        if (symbolMode) return key
        return when (key) {
            "␣" -> " "
            else -> if (key.length == 1 && key[0].isLetter()) {
                if (shiftOn || capsLock) key.uppercase() else key.lowercase()
            } else key
        }
    }

    private inner class SpaceSwipeListener : View.OnTouchListener {
        private var startX = 0f
        override fun onTouch(v: View?, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { startX = e.x; return false }
                MotionEvent.ACTION_UP -> {
                    val dx = e.x - startX
                    if (kotlin.math.abs(dx) > 40) {
                        val code = if (dx > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                        repeat((kotlin.math.abs(dx) / 40).toInt().coerceAtMost(10)) { sendDownUp(code) }
                        return true
                    }
                }
            }
            return false
        }
    }

    private fun showLongPressPopup(key: String, anchor: View) {
        val map = mapOf(
            "a" to listOf("á","à","ả","ã","ạ","ă","â"),
            "e" to listOf("é","è","ẻ","ẽ","ẹ","ê"),
            "o" to listOf("ó","ò","ỏ","õ","ọ","ô","ơ"),
            "u" to listOf("ú","ù","ủ","ũ","ụ","ư"),
            "i" to listOf("í","ì","ỉ","ĩ","ị"),
            "y" to listOf("ý","ỳ","ỷ","ỹ","ỵ"),
            "d" to listOf("đ")
        )
        val opts = map[key.lowercase()] ?: return
        PopupMenu(this, anchor).apply {
            opts.forEachIndexed { i, s -> menu.add(0, i, i, s) }
            setOnMenuItemClickListener { commitRaw(opts[it.itemId]); true }
            show()
        }
    }

    private fun onKey(key: String) {
        Feedback.keyPress(this, prefs)
        val ic = currentInputConnection ?: return
        when (key) {
            "⌫" -> {
                if (composingLen > 0) {
                    val next = engine.backspace()
                    ic.deleteSurroundingText(composingLen, 0)
                    if (next.isNotEmpty()) { ic.commitText(next, 1); composingLen = next.length }
                    else composingLen = 0
                } else {
                    val sel = ic.getSelectedText(0)
                    if (!sel.isNullOrEmpty()) ic.commitText("", 1) else ic.deleteSurroundingText(1, 0)
                }
            }
            "↵" -> {
                finishComposing()
                val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: 0
                if (action != 0 && action != EditorInfo.IME_ACTION_NONE) ic.performEditorAction(action)
                else sendDownUp(KeyEvent.KEYCODE_ENTER)
            }
            "␣" -> {
                expandShortcutIfAny(); finishComposing(); ic.commitText(" ", 1)
                prefs.typingCountToday = prefs.typingCountToday + 1
            }
            "⇧" -> {
                if (shiftOn) { capsLock = !capsLock; shiftOn = capsLock } else shiftOn = true
                rebuildKeyboard()
            }
            "?123" -> { symbolMode = true; rebuildKeyboard() }
            "ABC" -> { symbolMode = false; rebuildKeyboard() }
            "😊" -> { panelMode = Panel.EMOJI; rebuildKeyboard() }
            "🌐" -> {
                val modes = VietnameseEngine.Mode.entries
                val next = modes[(prefs.inputMode.ordinal + 1) % modes.size]
                prefs.inputMode = next; engine.mode = next
                Toast.makeText(this, "Bộ gõ: ${next.name}", Toast.LENGTH_SHORT).show()
            }
            "←" -> sendDownUp(KeyEvent.KEYCODE_DPAD_LEFT)
            "→" -> sendDownUp(KeyEvent.KEYCODE_DPAD_RIGHT)
            "↑" -> sendDownUp(KeyEvent.KEYCODE_DPAD_UP)
            "↓" -> sendDownUp(KeyEvent.KEYCODE_DPAD_DOWN)
            else -> {
                val text = displayLabel(key).ifBlank { key }
                val ch = text.firstOrNull()
                // Telex: chữ cái | VNI: chữ cái + số 1-9 (dấu/mũ)
                val canCompose = ch != null && !symbolMode && !isSecureEditor() && (
                    ch.isLetter() ||
                    (engine.mode == VietnameseEngine.Mode.VNI && ch in '1'..'9')
                )
                if (canCompose && ch != null) {
                    if (composingLen > 0) ic.deleteSurroundingText(composingLen, 0)
                    val composed = maybeAutoCap(engine.process(ch))
                    ic.commitText(composed, 1)
                    composingLen = if (engine.current().isNotEmpty()) composed.length else 0
                } else {
                    finishComposing(); ic.commitText(text, 1)
                }
                if (shiftOn && !capsLock && ch != null && ch.isLetter()) {
                    shiftOn = false; rebuildKeyboard()
                }
                prefs.typingCountToday = prefs.typingCountToday + 1
            }
        }
    }

    private fun maybeAutoCap(s: String): String {
        if (!prefs.autoCapitalize || s.isEmpty()) return s
        val before = currentInputConnection?.getTextBeforeCursor(2, 0)?.toString() ?: ""
        val need = before.isEmpty() || before.endsWith(". ") || before.endsWith("? ") ||
            before.endsWith("! ") || before.endsWith("\n") || before in listOf(".", "?", "!")
        return if (need) s.replaceFirstChar { it.uppercaseChar() } else s
    }

    private fun expandShortcutIfAny() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(20, 0)?.toString() ?: return
        val word = before.trimEnd().substringAfterLast(' ')
        val exp = prefs.getShortcuts()[word.lowercase()] ?: return
        ic.deleteSurroundingText(word.length, 0)
        ic.commitText(exp, 1)
    }

    private fun finishComposing() { engine.reset(); composingLen = 0 }
    private fun commitRaw(s: String) {
        finishComposing(); currentInputConnection?.commitText(s, 1)
        Feedback.keyPress(this, prefs)
    }
    private fun sendDownUp(code: Int) {
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    // ---------- Voice ----------
    private fun toggleVoice() {
        if (listening) { stopVoice(); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // IME cannot easily request runtime permission; use RecognizerIntent activity
            startVoiceIntent()
            return
        }
        startVoiceRecognizer()
    }

    private fun startVoiceIntent() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói tiếng Việt...")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Results via activity not ideal from IME; prefer SpeechRecognizer
            startVoiceRecognizer()
        } catch (e: Exception) {
            Toast.makeText(this, "Không mở được nhận giọng nói", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startVoiceRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Máy không hỗ trợ SpeechRecognizer", Toast.LENGTH_LONG).show()
            return
        }
        stopVoice()
        speech = SpeechRecognizer.createSpeechRecognizer(this)
        speech?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
                Toast.makeText(this@AdvancedIME, "🎤 Đang nghe...", Toast.LENGTH_SHORT).show()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) {
                listening = false
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Lỗi audio"
                    SpeechRecognizer.ERROR_CLIENT -> "Lỗi client"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Cần quyền Micro"
                    SpeechRecognizer.ERROR_NETWORK -> "Lỗi mạng"
                    SpeechRecognizer.ERROR_NO_MATCH -> "Không nhận được"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Hết thời gian"
                    else -> "Lỗi $error"
                }
                Toast.makeText(this@AdvancedIME, msg, Toast.LENGTH_SHORT).show()
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = list?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    finishComposing()
                    currentInputConnection?.commitText(text, 1)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try {
            speech?.startListening(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Không bật micro được: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopVoice() {
        listening = false
        try { speech?.cancel(); speech?.destroy() } catch (_: Exception) {}
        speech = null
    }

    // ---------- Emoji ----------
    private fun buildEmojiPanel(): View {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
        }
        val search = EditText(this).apply {
            hint = "Tìm emoji..."
            setSingleLine()
            setPadding(16, 12, 16, 12)
        }
        val grid = GridLayout(this).apply { columnCount = 8 }
        fun fill(list: List<String>) {
            grid.removeAllViews()
            list.take(80).forEach { e ->
                grid.addView(TextView(this@AdvancedIME).apply {
                    text = e; textSize = 24f; gravity = Gravity.CENTER
                    setPadding(10, 12, 10, 12)
                    setOnClickListener {
                        prefs.addRecentEmoji(e)
                        commitRaw(e)
                    }
                })
            }
        }
        // Recent first
        val recent = prefs.recentEmojis.split("|").filter { it.isNotBlank() }
        if (recent.isNotEmpty()) fill(recent) else fill(EmojiData.categories.values.flatten())

        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                fill(EmojiData.search(s?.toString() ?: ""))
            }
        })
        val tabs = HorizontalScrollView(this)
        val tabRow = LinearLayout(this)
        tabRow.addView(chip("Gần đây") {
            val r = prefs.recentEmojis.split("|").filter { it.isNotBlank() }
            fill(if (r.isEmpty()) EmojiData.categories.values.flatten().take(40) else r)
        })
        EmojiData.categories.keys.forEach { name ->
            tabRow.addView(chip(name) { fill(EmojiData.categories[name] ?: emptyList()) })
        }
        tabRow.addView(chip("Kaomoji") {
            grid.removeAllViews()
            EmojiData.kaomoji.forEach { k ->
                grid.addView(TextView(this@AdvancedIME).apply {
                    text = k; textSize = 14f; setPadding(8, 10, 8, 10)
                    setOnClickListener { commitRaw(k) }
                })
            }
        })
        tabs.addView(tabRow)
        box.addView(search); box.addView(tabs); box.addView(grid)
        scroll.addView(box)
        return scroll
    }

    private fun chip(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 13f
            setPadding(18, 10, 18, 10)
            setOnClickListener { onClick() }
        }
    }

    // ---------- Clipboard ----------
    private fun buildClipPanel(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(12, 12, 12, 12)
        }
        clips.all().forEach { item ->
            box.addView(TextView(this).apply {
                text = (if (item.pinned) "📌 " else "") + item.text.take(100)
                textSize = 15f; setPadding(14, 16, 14, 16)
                setOnClickListener { commitRaw(item.text) }
                setOnLongClickListener {
                    clips.pin(item.text, !item.pinned)
                    panelMode = Panel.CLIP; rebuildKeyboard(); true
                }
            })
        }
        if (clips.all().isEmpty()) {
            box.addView(TextView(this).apply {
                text = "Chưa có clipboard"; setPadding(12, 24, 12, 24)
            })
        }
        return ScrollView(this).apply { addView(box) }
    }

    // ---------- Calc ----------
    private fun buildCalcPanel(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8, 8, 8, 8) }
        val display = TextView(this).apply {
            text = "0"; textSize = 28f; gravity = Gravity.END; setPadding(16, 16, 16, 16)
        }
        var expr = ""
        fun upd() { display.text = expr.ifEmpty { "0" } }
        box.addView(display)
        listOf(
            listOf("7","8","9","/"), listOf("4","5","6","*"),
            listOf("1","2","3","-"), listOf("0",".","=","+"), listOf("C","⌫","↵")
        ).forEach { rowKeys ->
            val row = LinearLayout(this)
            rowKeys.forEach { k ->
                row.addView(Button(this).apply {
                    text = k
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        when (k) {
                            "C" -> { expr = ""; upd() }
                            "⌫" -> { expr = expr.dropLast(1); upd() }
                            "=" -> try {
                                expr = evalSimple(expr).toString(); upd()
                            } catch (_: Exception) { expr = "Lỗi"; upd() }
                            "↵" -> {
                                commitRaw(display.text.toString())
                                panelMode = Panel.NONE; rebuildKeyboard()
                            }
                            else -> { expr += k; upd() }
                        }
                    }
                })
            }
            box.addView(row)
        }
        return box
    }

    private fun evalSimple(e: String): Double {
        val t = e.replace(" ", "")
        val nums = t.split(Regex("[+\\-*/]")).map { it.toDouble() }
        val ops = t.filter { it in "+-*/" }.toList()
        var r = nums.firstOrNull() ?: 0.0
        for (i in ops.indices) {
            val b = nums.getOrElse(i + 1) { 0.0 }
            r = when (ops[i]) { '+' -> r + b; '-' -> r - b; '*' -> r * b; '/' -> r / b; else -> r }
        }
        return r
    }
}
