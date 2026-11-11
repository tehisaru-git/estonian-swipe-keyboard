package tribixbite.cleverkeys

import android.graphics.PointF
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tribixbite.cleverkeys.estonianemoji.EmojiPanelView
import tribixbite.cleverkeys.estonianemoji.OfflineEmojiSearch
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.ExtractedTextRequest
import android.view.ViewGroup
import tribixbite.cleverkeys.swipeui.SwipeSuggestionSession
import tribixbite.cleverkeys.estonianui.KeyKind
import tribixbite.cleverkeys.estonianui.KeyboardGeometry as SurfaceGeometry
import tribixbite.cleverkeys.estonianui.KeyboardLanguage
import tribixbite.cleverkeys.estonianui.KeyboardSurfaceView
import tribixbite.cleverkeys.estonianui.SurfaceGesture
import tribixbite.cleverkeys.swipe.CtcEngineAdapter
import tribixbite.cleverkeys.swipe.GeometricEngineAdapter
import tribixbite.cleverkeys.a11y.KeyboardGeometry as AdapterGeometry

/** Independent IME boundary. The surface owns all coordinates; adapters never see an old layout. */
class EstonianSwipeService : InputMethodService() {
    private var surface: KeyboardSurfaceView? = null
    private var editor: EditorInfo? = null
    private var epoch = 0L
    private var lastCandidatesStart = -1
    private var lastCandidatesEnd = -1
    private var ctc: CtcEngineAdapter? = null
    private var geometric: GeometricEngineAdapter? = null
    private var emojiPanel: EmojiPanelView? = null
    private var emojiSearch: OfflineEmojiSearch? = null
    private var emojiScope: CoroutineScope? = null
    private val suggestions = EstonianSuggestionController()
    private var suggestionRow: LinearLayout? = null
    private var suggestionButtons: List<Button> = emptyList()
    private var ownedConnection: InputConnection? = null
    private var ownedEditor: EditorInfo? = null
    private var ownedWordSink: OwnedWordSink? = null
    private var expectedOwnSelection: Pair<Int, Int>? = null
    private var writingSuggestion = false

    private fun clearSuggestions() {
        suggestions.clear()
        ownedWordSink?.forget()
        expectedOwnSelection = null
        renderSuggestions()
    }

    private fun renderSuggestions() {
        val words = suggestions.words
        suggestionButtons.forEachIndexed { index, button ->
            button.text = words.getOrNull(index) ?: ""
            button.visibility = if (index < words.size) View.VISIBLE else View.INVISIBLE
        }
        suggestionRow?.visibility = if (words.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun ownSelectionAfterWrite(connection: InputConnection) {
        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0)
        expectedOwnSelection = extracted?.let { it.selectionStart to it.selectionEnd }
    }

    private fun startSuggestions(info: EditorInfo?) {
        clearSuggestions()
        ownedWordSink = null
        val connection = currentInputConnection
        ownedConnection = connection
        ownedEditor = info
        if (connection == null || info == null) return
        val sink = OwnedWordSink(connection) {
            editor === info && currentInputConnection === connection
        }
        ownedWordSink = sink
        suggestions.start(sink, isSwipeAllowed(info))
    }
    private val taps = EstonianTapInput(
        { currentInputConnection?.commitText(it, 1) },
        { currentInputConnection?.let { connection -> deleteBackward(connection) } },
        {
            val info = editor
            val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
            currentInputConnection?.let { submitEnter(it, action) }
        },
        { surface?.let { it.symbolMode = !it.symbolMode } },
        { showEmojiPanel() },
    )

    override fun onCreate() {
        super.onCreate()
        val prefs = DirectBootAwarePreferences.get_shared_preferences(this)
        Config.initGlobalConfig(prefs, resources, null, false)
        tribixbite.cleverkeys.swipe.CtcInstalledPacks.bind(this)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        emojiScope = scope
        scope.launch {
            try {
                val loaded = OfflineEmojiSearch.fromAssets(this@EstonianSwipeService)
                emojiSearch = loaded
                emojiPanel?.setSearchIndex(loaded)
            } catch (_: Exception) {
                // No network fallback: common emojis remain available if the asset fails.
            }
        }
    }

    override fun onCreateInputView(): View {
        epoch++
        clearSuggestions()
        emojiPanel?.let { it.onEmojiPicked = null; it.onClose = null }
        surface?.onGesture = null
        val view = KeyboardSurfaceView(this).also {
            surface = it
            it.language = taps.language
            it.onGesture = ::handleGesture
        }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xff30343b.toInt())
            visibility = View.GONE
        }
        suggestionRow = row
        suggestionButtons = (0..2).map { index ->
            Button(this).apply {
                isAllCaps = false
                setOnClickListener {
                    val info = editor
                    val connection = currentInputConnection
                    val selected = if (info != null && info === ownedEditor && connection != null && connection === ownedConnection) {
                        writingSuggestion = true
                        try { suggestions.choose(index) } finally { writingSuggestion = false }
                    } else false
                    if (selected && connection != null) {
                        ownSelectionAfterWrite(connection)
                        renderSuggestions()
                    } else if (info !== ownedEditor || connection !== ownedConnection) {
                        epoch++
                        clearSuggestions()
                    }
                }
            }.also { row.addView(it, LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
        }
        container.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        val panel = EmojiPanelView(this).apply {
            visibility = View.GONE
            setSearchIndex(emojiSearch)
        }
        emojiPanel = panel
        container.addView(panel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            (194 * resources.displayMetrics.density).toInt()))
        container.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            (240 * resources.displayMetrics.density).toInt()))
        return container
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        epoch++
        clearSuggestions()
        ownedWordSink = null
        ownedConnection = null
        ownedEditor = null
        editor = attribute
        closeEmojiPanel()
        lastCandidatesStart = -1
        lastCandidatesEnd = -1
        taps.resetShift()
        surface?.symbolMode = false
    }

    override fun onFinishInput() {
        epoch++
        suggestions.end()
        renderSuggestions()
        ownedWordSink?.forget()
        ownedWordSink = null
        ownedConnection = null
        ownedEditor = null
        editor = null
        taps.resetShift()
        super.onFinishInput()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        epoch++
        editor = info
        startSuggestions(info)
        surface?.onGesture = ::handleGesture
        surface?.language = taps.language
        val view = surface
        view?.post {
            if (surface === view && editor === info && view.width > 0 && view.height > 0) {
                prewarmSwipe(view)
            }
        }
    }

    private fun prewarmSwipe(view: KeyboardSurfaceView) {
        val geometry = view.geometry
        if (geometry.width != view.width || geometry.height != view.height || geometry.symbolMode) return
        val source = adapterKeyboard(geometry)
        val params = AdapterGeometry.Params(1f, 1f, 0f, 0f)
        val language = if (geometry.language == KeyboardLanguage.ESTONIAN) "et" else "en"
        if (!CtcEngineAdapter.supportsLanguage(language)) return
        val engine = ctc ?: CtcEngineAdapter(this).also { ctc = it }
        if (engine.hasLexiconSource(language) &&
            !engine.isModelPermanentlyUnavailable(language) &&
            engine.supportsLayout(source, params, view.width.toFloat(), view.height.toFloat(), language)) {
            engine.warmUpAsync(source, params, view.width.toFloat(), view.height.toFloat(), language)
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        epoch++
        suggestions.end()
        renderSuggestions()
        ownedWordSink?.forget()
        ownedWordSink = null
        ownedConnection = null
        ownedEditor = null
        closeEmojiPanel()
        surface?.onGesture = null
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        val changed = oldSelStart != newSelStart || oldSelEnd != newSelEnd ||
            lastCandidatesStart != candidatesStart || lastCandidatesEnd != candidatesEnd
        if (changed && !writingSuggestion) {
            val connection = currentInputConnection
            val composed = suggestions.composingWord
            val readback = if (connection === ownedConnection && editor === ownedEditor)
                connection?.getTextBeforeCursor(4096, 0)?.toString() else null
            val sameComposingEnd = isOwnSuggestionSelection(composed, readback,
                newSelStart, newSelEnd, candidatesStart, candidatesEnd, expectedOwnSelection)
            if (!sameComposingEnd) {
                epoch++
                suggestions.cursorChanged()
                ownedWordSink?.forget()
                expectedOwnSelection = null
                renderSuggestions()
            }
        }
        lastCandidatesStart = candidatesStart
        lastCandidatesEnd = candidatesEnd
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
            candidatesStart, candidatesEnd)
    }

    override fun onDestroy() {
        epoch++
        emojiScope?.cancel()
        emojiScope = null
        emojiSearch = null
        suggestions.end()
        suggestionRow = null
        suggestionButtons = emptyList()
        ownedWordSink?.forget()
        ownedWordSink = null
        ownedConnection = null
        ownedEditor = null
        closeEmojiPanel()
        emojiPanel = null
        editor = null
        surface?.onGesture = null
        surface = null
        ctc?.shutdown()
        geometric?.shutdown()
        ctc = null
        geometric = null
        super.onDestroy()
    }

    private fun handleGesture(gesture: SurfaceGesture) {
        if (editor == null || currentInputConnection == null) return
        val panel = emojiPanel
        if (panel != null && panel.visibility == View.VISIBLE) {
            if (gesture is SurfaceGesture.Tap && gesture.language == taps.language) {
                epoch++
                when (gesture.key.kind) {
                    KeyKind.LETTER, KeyKind.NUMBER, KeyKind.SYMBOL,
                    KeyKind.COMMA, KeyKind.PERIOD -> panel.appendQuery(gesture.key.label)
                    KeyKind.SPACE -> panel.appendQuery(" ")
                    KeyKind.BACKSPACE -> panel.backspaceQuery()
                    KeyKind.ENTER -> panel.visibleEmoji.firstOrNull()?.let(panel::pickForTest)
                    KeyKind.EMOJI -> closeEmojiPanel()
                    KeyKind.LANGUAGE -> { taps.switchLanguage(); surface?.language = taps.language }
                    KeyKind.SYMBOLS -> surface?.let { it.symbolMode = !it.symbolMode }
                    else -> Unit
                }
            }
            return // Swipes search nothing and never insert into the editor from this pane.
        }
        when (gesture) {
            is SurfaceGesture.Tap -> {
                if (gesture.language != taps.language) return
                epoch++ // A tap invalidates any in-flight decode, including the fallback.
                clearSuggestions()
                taps.accept(gesture)
                if (gesture.key.kind == KeyKind.LANGUAGE) {
                    surface?.let { view ->
                        view.language = taps.language
                        view.post { if (surface === view) prewarmSwipe(view) }
                    }
                }
            }
            is SurfaceGesture.Hold -> {
                if (gesture.language != taps.language) return
                epoch++
                clearSuggestions()
                taps.acceptHold(gesture)
            }
            is SurfaceGesture.Swipe -> decode(gesture)
        }
    }

    /** Keep the picker inside the IME view so Android resizes the editor above it. */
    private fun showEmojiPanel() {
        val anchor = surface ?: return
        val panel = emojiPanel ?: return
        if (panel.visibility == View.VISIBLE) { closeEmojiPanel(); return }
        val connection = currentInputConnection ?: return
        val field = editor ?: return
        panel.setSearchIndex(emojiSearch)
        panel.clearQuery()
        panel.onClose = ::closeEmojiPanel
        panel.onEmojiPicked = { emoji ->
            if (panel.visibility == View.VISIBLE && editor === field &&
                currentInputConnection === connection && surface === anchor) {
                connection.commitText(emoji, 1)
            }
            closeEmojiPanel()
        }
        panel.visibility = View.VISIBLE
    }

    private fun closeEmojiPanel() {
        emojiPanel?.let { panel ->
            panel.visibility = View.GONE
            panel.clearQuery()
            panel.onEmojiPicked = null
            panel.onClose = null
        }
    }

    private fun decode(gesture: SurfaceGesture.Swipe) {
        val view = surface ?: return
        if (gesture.language != taps.language || gesture.samples.isEmpty()) return
        val geometry = view.geometry
        if (geometry.width != view.width || geometry.height != view.height || geometry.language != gesture.language) return
        val source = adapterKeyboard(geometry)
        val params = AdapterGeometry.Params(1f, 1f, 0f, 0f)
        val points = gesture.samples.map { PointF(it.x, it.y) }
        val times = gesture.samples.map { it.timeMs }
        val language = if (gesture.language == KeyboardLanguage.ESTONIAN) "et" else "en"
        val owner = ++epoch
        val connection = currentInputConnection ?: return
        val info = editor ?: return
        if (!isSwipeAllowed(info) || info !== ownedEditor || connection !== ownedConnection) return
        val ticket = suggestions.begin() ?: return
        renderSuggestions()
        val accept: (PredictionResult) -> Unit = { result ->
            if (owner == epoch && editor === info && currentInputConnection === connection &&
                surface === view && view.geometry === geometry && taps.language == gesture.language
            ) {
                writingSuggestion = true
                try {
                    if (suggestions.accept(ticket, result)) {
                        ownSelectionAfterWrite(connection)
                        renderSuggestions()
                    }
                } finally { writingSuggestion = false }
            }
        }
        val fallback = {
            if (owner == epoch && editor === info && currentInputConnection === connection) {
                val engine = geometric ?: GeometricEngineAdapter(this).also { geometric = it }
                engine.decodeAsync(source, params, view.width.toFloat(), view.height.toFloat(),
                    points, times, language, accept)
            }
        }
        if (CtcEngineAdapter.supportsLanguage(language)) {
            val engine = ctc ?: CtcEngineAdapter(this).also { ctc = it }
            val eligible = engine.hasLexiconSource(language) &&
                !engine.isModelPermanentlyUnavailable(language) &&
                engine.supportsLayout(source, params, view.width.toFloat(), view.height.toFloat(), language)
            if (!eligible) {
                fallback()
                return
            }
            engine.decodeAsync(source, params, view.width.toFloat(), view.height.toFloat(),
                points, times, language, onDecodeFailure = fallback, onResult = { result ->
                    if (result.words.isEmpty()) fallback() else accept(result)
                })
        } else fallback()
    }

    companion object {
        /** Editors sometimes advertise an action but decline it; do not drop Enter. */
        internal fun submitEnter(connection: InputConnection, action: Int): Boolean {
            if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED &&
                connection.performEditorAction(action)) return true
            val down = connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            val up = connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            return down && up
        }

        /** A selected range wins; otherwise delete one Unicode code point, not a UTF-16 half. */
        internal fun deleteBackward(connection: InputConnection): Boolean {
            val selected = connection.getSelectedText(0)
            if (!selected.isNullOrEmpty()) return connection.commitText("", 1)
            val extracted = connection.getExtractedText(ExtractedTextRequest(), 0)
            if (extracted != null && extracted.selectionStart != extracted.selectionEnd) {
                return connection.commitText("", 1)
            }
            if (connection.deleteSurroundingTextInCodePoints(1, 0)) return true
            val down = connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            val up = connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            return down && up
        }

        /** Secret fields never send traces to the predictor or display candidate text. */
        internal fun isSwipeAllowed(info: EditorInfo): Boolean {
            val kind = info.inputType and InputType.TYPE_MASK_CLASS
            val variation = info.inputType and InputType.TYPE_MASK_VARIATION
            if (kind == InputType.TYPE_CLASS_NUMBER &&
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) return false
            if (kind != InputType.TYPE_CLASS_TEXT) return false
            return variation != InputType.TYPE_TEXT_VARIATION_PASSWORD &&
                variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
                variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        }

        /** Use integer cell dimensions so adapter rects equal rendered/touch/a11y rects. */
        internal fun adapterKeyboard(geometry: SurfaceGeometry): KeyboardData {
            val lang = if (geometry.language == KeyboardLanguage.ESTONIAN) "et" else "en"
            val xml = buildString {
                append("<keyboard bottom_row=\"false\" script=\"latin\" language=\"")
                append(lang)
                append("\">")
                for (row in geometry.rows) {
                    append("<row height=\"").append(row.first().bounds.bottom - row.first().bounds.top).append("\">")
                    for (key in row) {
                        append("<key c=\"")
                        // Nonletters remain non-swipe nodes; only the letter keys affect model geometry.
                        append(if (key.kind == KeyKind.LETTER) key.label else ".")
                        append("\" width=\"").append(key.bounds.right - key.bounds.left).append("\"/>")
                    }
                    append("</row>")
                }
                append("</keyboard>")
            }
            return KeyboardData.load_string_exn(xml)
        }
    }
}

/**
 * Direct-commit word ownership for editors that discard a composing span on finish.
 * Replacement is permitted only when the exact owned suffix is still before the caret.
 */
internal class OwnedWordSink(
    private val connection: InputConnection,
    private val isCurrent: () -> Boolean,
) : SwipeSuggestionSession.Sink {
    private var ownedWord: String? = null
    private var pendingSeparator = ""

    fun forget() { ownedWord = null; pendingSeparator = "" }

    override fun beforeCursor(): String? =
        if (isCurrent()) connection.getTextBeforeCursor(4096, 0)?.toString() else null

    override fun setComposingText(value: String): Boolean {
        if (!isCurrent()) return false
        val previous = ownedWord
        if (previous != null) {
            if (pendingSeparator.isNotEmpty() || connection.getSelectedText(0)?.isNotEmpty() == true ||
                connection.getTextBeforeCursor(previous.length, 0)?.toString() != previous) return false
            val selection = connection.getExtractedText(ExtractedTextRequest(), 0)
                ?: return false // A blind editor cannot prove the range we own.
            val end = selection.selectionEnd
            if (selection.selectionStart != end || end < previous.length) return false
            if (!connection.setSelection(end - previous.length, end)) return false
            if (connection.getSelectedText(0)?.toString() != previous) {
                connection.setSelection(end, end)
                return false
            }
            if (!connection.commitText(value, 1)) {
                connection.setSelection(end, end) // The old word was only selected, never deleted.
                return false
            }
        } else {
            val text = pendingSeparator + value
            pendingSeparator = ""
            if (!connection.commitText(text, 1)) return false
        }
        ownedWord = value
        return true
    }

    override fun finishComposingText(): Boolean {
        if (!isCurrent()) return false
        ownedWord = null
        pendingSeparator = ""
        return true // Already committed: never ask the editor to discard a composing span.
    }

    override fun commitText(value: String): Boolean {
        if (!isCurrent()) return false
        if (value == " ") { pendingSeparator = value; return true }
        return connection.commitText(value, 1)
    }
}

/** Readback plus editor-reported composing range identify our asynchronous selection callbacks. */
internal fun isOwnSuggestionSelection(
    composing: String?, beforeCursor: String?, start: Int, end: Int,
    candidatesStart: Int, candidatesEnd: Int, expected: Pair<Int, Int>?,
): Boolean {
    if (composing == null || beforeCursor?.endsWith(composing) != true || start != end) return false
    if (expected == (start to end)) return true
    return candidatesStart >= 0 && candidatesEnd == end &&
        candidatesEnd - candidatesStart == composing.length
}

/** Service-side ownership wrapper: the policy alone never decides which editor is current. */
internal class EstonianSuggestionController {
    private val session = SwipeSuggestionSession()
    private var field: SwipeSuggestionSession.FieldToken? = null
    private var offer: SwipeSuggestionSession.Offer? = null
    var words: List<String> = emptyList()
        private set
    var composingWord: String? = null
        private set

    fun start(sink: SwipeSuggestionSession.Sink, allowed: Boolean) {
        end()
        field = session.startField(sink, isPassword = !allowed)
    }

    fun end() {
        field?.let(session::endField)
        field = null
        offer = null
        words = emptyList()
        composingWord = null
    }

    fun clear() { cursorChanged() }

    fun cursorChanged() {
        field = field?.let(session::cursorChanged)
        offer = null
        words = emptyList()
        composingWord = null
    }

    fun begin(): SwipeSuggestionSession.SwipeToken? {
        offer = null
        words = emptyList()
        return field?.let(session::beginSwipe)
    }

    fun accept(ticket: SwipeSuggestionSession.SwipeToken, result: PredictionResult): Boolean {
        val accepted = session.accept(ticket, result) ?: return false
        offer = accepted
        words = accepted.words
        composingWord = accepted.words.first()
        return true
    }

    fun choose(index: Int): Boolean {
        val owner = field ?: return false
        val current = offer ?: return false
        if (!session.select(owner, current, index)) {
            // A failed readback invalidates the UI even if the editor omitted a selection event.
            cursorChanged()
            return false
        }
        composingWord = words[index]
        return true
    }
}

/** Tap state independent of Android so editor effects and language ownership are testable. */
internal class EstonianTapInput(
    private val commit: (String) -> Unit,
    private val delete: () -> Unit,
    private val enter: () -> Unit,
    private val symbols: () -> Unit = {},
    private val emoji: () -> Unit = {},
) {
    var language = KeyboardLanguage.ESTONIAN
        private set
    private var shifted = false
    fun resetShift() { shifted = false }
    fun switchLanguage() {
        language = if (language == KeyboardLanguage.ESTONIAN) KeyboardLanguage.ENGLISH else KeyboardLanguage.ESTONIAN
        resetShift()
    }
    fun acceptHold(hold: SurfaceGesture.Hold) {
        if (hold.language != language || language != KeyboardLanguage.ESTONIAN) return
        val alternate = when (hold.key.label) { "s" -> "š"; "z" -> "ž"; else -> return }
        commit(if (shifted) alternate.uppercase() else alternate)
        shifted = false
    }
    fun accept(tap: SurfaceGesture.Tap) {
        if (tap.language != language) return
        when (tap.key.kind) {
            KeyKind.LETTER -> {
                commit(if (shifted) tap.key.label.uppercase() else tap.key.label)
                shifted = false
            }
            KeyKind.NUMBER, KeyKind.SYMBOL, KeyKind.COMMA, KeyKind.PERIOD -> commit(tap.key.label)
            KeyKind.SHIFT -> shifted = !shifted
            KeyKind.BACKSPACE -> delete()
            KeyKind.SPACE -> commit(" ")
            KeyKind.LANGUAGE -> switchLanguage()
            KeyKind.SYMBOLS -> symbols()
            KeyKind.EMOJI -> emoji()
            KeyKind.ENTER -> enter()
            else -> Unit
        }
    }
}
