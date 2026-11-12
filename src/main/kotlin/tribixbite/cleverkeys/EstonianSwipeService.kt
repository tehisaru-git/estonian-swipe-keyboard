package tribixbite.cleverkeys

import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tribixbite.cleverkeys.swipeui.ManualCompletions
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
import tribixbite.cleverkeys.estonianui.CandidateLabelView
import tribixbite.cleverkeys.estonianui.SurfaceGesture
import tribixbite.cleverkeys.estonianstudy.SwipeStudyBridge
import tribixbite.cleverkeys.estoniantelemetry.TelemetryRuntime
import tribixbite.cleverkeys.estoniantelemetry.SwipeTrace
import tribixbite.cleverkeys.estoniantelemetry.TelemetryPoint
import tribixbite.cleverkeys.estoniantelemetry.TelemetryKey
import tribixbite.cleverkeys.estoniantelemetry.TelemetryGeometry
import tribixbite.cleverkeys.estoniantelemetry.TelemetryPolicy
import android.os.SystemClock
import java.util.UUID
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
    private var manualCompletions: ManualCompletions? = null
    private var suggestionRow: FrameLayout? = null
    private var toolbarRow: LinearLayout? = null
    private var candidatesRow: LinearLayout? = null
    private var candidateScroller: HorizontalScrollView? = null
    private var candidateSeparators: List<View> = emptyList()
    private var lastRenderedCandidateWords: List<String> = emptyList()
    private var toolbarLanguageButton: TextView? = null
    private var toolbarArrowButton: TextView? = null
    private var forceToolbar = false
    private var toolbarCompact = false
    private var suggestionButtons: List<CandidateLabelView> = emptyList()
    private var ownedConnection: InputConnection? = null
    private var ownedEditor: EditorInfo? = null
    private var ownedWordSink: OwnedWordSink? = null
    private var expectedOwnSelection: Pair<Int, Int>? = null
    private var writingSuggestion = false
    private var writingManual = false
    private var manualLetterGesture = false
    private var afterSwipeWord: String? = null
    private val manualPolicy = ManualTypingPolicy()
    private data class LoggedSwipe(val id: String, val info: EditorInfo, val connection: InputConnection, var word: String)
    private var loggedSwipe: LoggedSwipe? = null
    private var undoneSwipe: LoggedSwipe? = null
    private var undoAnchor: String? = null
    private val replacementText = StringBuilder()
    private val correctionTracker = SwipeCorrectionTracker()

    private fun clearSuggestions() {
        suggestions.clear()
        manualCompletions?.clear()
        ownedWordSink?.forget()
        expectedOwnSelection = null
        forceToolbar = false
        renderSuggestions()
    }

    private fun renderSuggestions() {
        val words = suggestions.words.ifEmpty { manualCompletions?.words.orEmpty() }
        if (words != lastRenderedCandidateWords) {
            candidateScroller?.scrollTo(0, 0)
            lastRenderedCandidateWords = words.toList()
        }
        suggestionButtons.forEachIndexed { index, button ->
            button.bind(words.getOrNull(index))
        }
        candidateSeparators.forEachIndexed { index, separator ->
            separator.visibility = if (words.size > index + 1) View.VISIBLE else View.GONE
        }
        val showCandidates = words.isNotEmpty() && !forceToolbar
        candidatesRow?.visibility = if (showCandidates) View.VISIBLE else View.GONE
        toolbarRow?.visibility = if (showCandidates) View.GONE else View.VISIBLE
    }

    private fun refreshManualCompletions() {
        val info = editor
        val connection = currentInputConnection
        if (info == null || connection == null || connection !== ownedConnection ||
            info !== ownedEditor || suggestions.words.isNotEmpty() ||
            emojiPanel?.visibility == View.VISIBLE || afterSwipeWord != null) {
            manualCompletions?.clear()
        } else manualCompletions?.update(connection, info, taps.language)
        renderSuggestions()
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
    private fun commitManualValue(value: String) {
        val connection = currentInputConnection ?: return
        val previous = loggedSwipe
        val ownSwipeSuffix = previous != null && afterSwipeWord == previous.word && verifiedSuffix(previous)
        if (!manualLetterGesture && value.isNotEmpty() && value != " " && value != "\n") {
            finishUndoneWord() // A punctuation mark can close a manually retyped correction.
        } else if (value == " ") finishUndoneWord()
        writingManual = true
        try {
            val success = when {
                manualLetterGesture -> manualPolicy.commitLetterAfterSwipe(connection, afterSwipeWord, value,
                    autoSpace = afterSwipeWord != null && ViipioSpacingPreference.enabled(
                        DirectBootAwarePreferences.get_shared_preferences(this)))
                value == " " && editor?.let(::isSwipeAllowed) == true -> manualPolicy.space(connection)
                else -> { manualPolicy.reset(); connection.commitText(value, 1) }
            }
            if (!success) return
            if (manualLetterGesture && undoneSwipe?.connection === connection &&
                value.length == 1 && value[0].isLetter() && replacementText.length < 40) {
                replacementText.append(value)
            }
            if (ownSwipeSuffix && previous != null) {
                val suffix = if (manualLetterGesture) previous.word + " " + value else previous.word + " "
                if (connection.getTextBeforeCursor(suffix.length, 0)?.toString() == suffix) {
                    TelemetryRuntime.recordFinalization(this, previous.id, previous.info, previous.word)
                    loggedSwipe = null
                }
            }
            afterSwipeWord = null
        } finally { writingManual = false }
    }

    private val taps = EstonianTapInput(
        ::commitManualValue,
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
        TelemetryRuntime.initialize(this)
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
        scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    ManualCompletions.fromBundledAssets(this@EstonianSwipeService)
                }
                manualCompletions = loaded
                refreshManualCompletions()
            } catch (_: Exception) {
                // The keyboard remains usable without a dictionary.
            }
        }
    }

    override fun onCreateInputView(): View {
        epoch++
        clearSuggestions()
        toolbarCompact = false
        emojiPanel?.let { it.onEmojiPicked = null; it.onClose = null }
        surface?.onGesture = null
        val view = KeyboardSurfaceView(this).also {
            surface = it
            it.language = taps.language
            it.onGesture = ::handleGesture
        }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val dp = resources.displayMetrics.density
        val row = FrameLayout(this).apply { setBackgroundColor(view.palette.surface) }
        suggestionRow = row
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        toolbarRow = toolbar
        val candidates = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        candidatesRow = candidates
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        candidateScroller = scroll
        lastRenderedCandidateWords = emptyList()
        val candidateTrack = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        scroll.addView(candidateTrack, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        fun navButton(label: String, description: String): TextView = TextView(this).apply {
            text = label
            contentDescription = description
            gravity = Gravity.CENTER
            textSize = 23f
            setTextColor(view.palette.label)
            background = GradientDrawable().apply {
                setColor(view.palette.letter)
                shape = GradientDrawable.OVAL
            }
        }
        val arrow = navButton("‹", getString(R.string.swipe_toolbar_toggle))
        toolbarArrowButton = arrow
        val arrowSize = (48 * dp).toInt()
        toolbar.addView(arrow, LinearLayout.LayoutParams(arrowSize, arrowSize))
        val tools = listOf(
            Triple("☺︎", R.string.swipe_toolbar_emoji, { showEmojiPanel() }),
            Triple("?123", R.string.swipe_toolbar_symbols, {
                epoch++; clearSuggestions(); surface?.let { it.symbolMode = !it.symbolMode }
            }),
            Triple(if (taps.language == KeyboardLanguage.ESTONIAN) "ET" else "EN",
                R.string.swipe_toolbar_language, {
                    epoch++; clearSuggestions(); taps.switchLanguage()
                    view.language = taps.language
                    toolbarLanguageButton?.apply {
                        text = if (taps.language == KeyboardLanguage.ESTONIAN) "ET" else "EN"
                        contentDescription = getString(if (taps.language == KeyboardLanguage.ESTONIAN)
                            R.string.swipe_toolbar_to_english else R.string.swipe_toolbar_to_estonian)
                    }
                    view.post { if (surface === view && view.width > 0) prewarmSwipe(view) }
                }),
            Triple("⚙", R.string.swipe_toolbar_settings, {
                startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
        )
        tools.forEachIndexed { index, (label, description, action) ->
            val button = TextView(this).apply {
                text = label
                textSize = if (label == "?123" || label == "ET" || label == "EN") 17f else 23f
                gravity = Gravity.CENTER
                setTextColor(view.palette.label)
                contentDescription = getString(description)
                setOnClickListener { action() }
            }
            if (index == 2) {
                toolbarLanguageButton = button
                button.contentDescription = getString(if (taps.language == KeyboardLanguage.ESTONIAN)
                    R.string.swipe_toolbar_to_english else R.string.swipe_toolbar_to_estonian)
            }
            toolbar.addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        arrow.setOnClickListener {
            if ((suggestions.words.isNotEmpty() || manualCompletions?.words?.isNotEmpty() == true) && forceToolbar) {
                forceToolbar = false
                renderSuggestions()
            } else {
                toolbarCompact = !toolbarCompact
                arrow.text = if (toolbarCompact) "›" else "‹"
                for (i in 1 until toolbar.childCount) toolbar.getChildAt(i).visibility =
                    if (toolbarCompact) View.INVISIBLE else View.VISIBLE
            }
        }
        val candidateArrow = navButton("‹", getString(R.string.swipe_toolbar_toggle)).apply {
            setOnClickListener { forceToolbar = true; renderSuggestions() }
        }
        candidates.addView(candidateArrow, LinearLayout.LayoutParams(arrowSize, arrowSize))
        val separators = mutableListOf<View>()
        suggestionButtons = (0..2).map { index ->
            if (index > 0) candidateTrack.addView(View(this).apply {
                setBackgroundColor(if (view.darkMode) 0xff696b70.toInt() else 0xffc6cbd3.toInt())
                visibility = View.GONE
                separators.add(this)
            }, LinearLayout.LayoutParams((1 * dp).toInt().coerceAtLeast(1), (24 * dp).toInt()))
            CandidateLabelView(this).apply {
                setTextColor(view.palette.label)
                visibility = View.INVISIBLE
                setOnClickListener {
                    val info = editor
                    val connection = currentInputConnection
                    var manualAttempted = false
                    val selected = if (info != null && info === ownedEditor && connection != null && connection === ownedConnection) {
                        writingSuggestion = true
                        try {
                            if (suggestions.words.isNotEmpty()) suggestions.choose(index)
                            else {
                                manualAttempted = true
                                manualCompletions?.choose(connection, index) == true
                            }
                        } finally { writingSuggestion = false }
                    } else false
                    if (manualAttempted && !selected) {
                        manualCompletions?.clear()
                        renderSuggestions()
                    }
                    if (selected && connection != null) {
                        if (suggestions.words.isEmpty()) {
                            refreshManualCompletions()
                            return@setOnClickListener
                        }
                        val newWord = suggestions.composingWord
                        val previous = loggedSwipe
                        if (newWord != null && previous != null && previous.info === info &&
                            previous.connection === connection && previous.word != newWord) {
                            val correction = correctionTracker.onSwipeReplacedFromBar(previous.word, newWord)
                            correction?.rejected?.forEach { rejected ->
                                rejected.traceId?.let { id ->
                                    TelemetryRuntime.recordCorrection(this@EstonianSwipeService, id, info,
                                        rejected.word, newWord, if (id == previous.id) "candidate" else "manual")
                                    if (id != previous.id) TelemetryRuntime.recordFinalization(
                                        this@EstonianSwipeService, id, info, newWord)
                                }
                            }
                            previous.word = newWord
                            correctionTracker.onSwipeAutoInserted(SwipeCorrectionTracker.SwipeRecord(
                                newWord, suggestions.words, previous.id),
                                connection.getTextBeforeCursor(96, 0)?.toString())
                        }
                        afterSwipeWord = newWord
                        ownSelectionAfterWrite(connection)
                        renderSuggestions()
                    } else if (info !== ownedEditor || connection !== ownedConnection) {
                        epoch++
                        clearSuggestions()
                    }
                }
            }.also { candidateTrack.addView(it, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)) }
        }
        candidateSeparators = separators
        candidates.addView(scroll, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        row.addView(toolbar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        row.addView(candidates, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        container.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            (48 * dp).toInt()))
        val panel = EmojiPanelView(this).apply {
            visibility = View.GONE
            setSearchIndex(emojiSearch)
        }
        emojiPanel = panel
        container.addView(panel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            (100 * resources.displayMetrics.density).toInt()))
        container.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            (286 * resources.displayMetrics.density).toInt()))
        return container
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        finishTelemetryField()
        epoch++
        clearSuggestions()
        afterSwipeWord = null
        manualPolicy.reset()
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
        finishTelemetryField()
        epoch++
        suggestions.end()
        manualCompletions?.clear()
        renderSuggestions()
        afterSwipeWord = null
        manualPolicy.reset()
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
        refreshManualCompletions()
        surface?.onGesture = ::handleGesture
        surface?.language = taps.language
        val view = surface
        val imeWindow = window?.window
        if (view != null && imeWindow != null) {
            imeWindow.navigationBarColor = view.palette.surface
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                val flag = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                imeWindow.decorView.systemUiVisibility = if (view.darkMode)
                    imeWindow.decorView.systemUiVisibility and flag.inv()
                else imeWindow.decorView.systemUiVisibility or flag
            }
            if (android.os.Build.VERSION.SDK_INT >= 29) imeWindow.isNavigationBarContrastEnforced = false
        }
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
        finishTelemetryField()
        epoch++
        suggestions.end()
        manualCompletions?.clear()
        renderSuggestions()
        afterSwipeWord = null
        manualPolicy.reset()
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
        if (changed && !writingSuggestion && !writingManual) {
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
                afterSwipeWord = null
                loggedSwipe = null
                undoneSwipe = null
                undoAnchor = null
                replacementText.clear()
                correctionTracker.clear()
                expectedOwnSelection = null
                renderSuggestions()
            }
            refreshManualCompletions()
        }
        lastCandidatesStart = candidatesStart
        lastCandidatesEnd = candidatesEnd
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
            candidatesStart, candidatesEnd)
    }

    override fun onDestroy() {
        finishTelemetryField()
        epoch++
        emojiScope?.cancel()
        emojiScope = null
        emojiSearch = null
        manualCompletions = null
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

    private fun deleteOnePress() {
        afterSwipeWord = null
        manualPolicy.reset()
        val tracked = loggedSwipe
        val owned = ownedWordSink?.deleteOwnedWord()
        if (owned == true && tracked != null && editor === tracked.info &&
            currentInputConnection === tracked.connection) {
            val anchor = tracked.connection.getTextBeforeCursor(SwipeCorrectionTracker.ANCHOR_WINDOW, 0)?.toString()
            correctionTracker.onSwipeUndone(tracked.word, anchor)
            TelemetryRuntime.recordUndo(this, tracked.id, tracked.info)
            undoAnchor = anchor
            undoneSwipe = tracked
            replacementText.clear()
            loggedSwipe = null
        }
        clearSuggestions()
        if (owned == null) {
            val connection = currentInputConnection
            val deletingReplacement = undoneSwipe?.connection === connection && replacementText.isNotEmpty() &&
                connection?.getTextBeforeCursor(1, 0)?.toString() == replacementText.last().toString()
            if (connection != null && deleteBackward(connection)) {
                if (deletingReplacement) replacementText.deleteCharAt(replacementText.lastIndex)
                else if (tracked != null) { loggedSwipe = null; correctionTracker.clear() }
            }
        }
        refreshManualCompletions()
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
                if (gesture.key.kind == KeyKind.BACKSPACE) {
                    deleteOnePress()
                    return
                }
                if (gesture.key.kind != KeyKind.LETTER && gesture.key.kind != KeyKind.SPACE &&
                    gesture.key.kind != KeyKind.SHIFT) {
                    finalizeLoggedWord()
                    finishUndoneWord()
                    afterSwipeWord = null
                    manualPolicy.reset()
                    if (gesture.key.kind == KeyKind.LANGUAGE) correctionTracker.clear()
                }
                clearSuggestions()
                manualLetterGesture = gesture.key.kind == KeyKind.LETTER
                try { taps.accept(gesture) } finally { manualLetterGesture = false }
                if (gesture.key.kind == KeyKind.LETTER || gesture.key.kind == KeyKind.LANGUAGE ||
                    gesture.key.kind == KeyKind.SPACE) refreshManualCompletions()
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
                manualLetterGesture = true
                try { taps.acceptHold(gesture) } finally { manualLetterGesture = false }
                refreshManualCompletions()
            }
            is SurfaceGesture.LongPress -> {
                if (gesture.language != taps.language) return
                epoch++
                val letter = gesture.value.length == 1 && gesture.value[0].isLetter()
                if (!letter) {
                    finalizeLoggedWord()
                    finishUndoneWord()
                    afterSwipeWord = null
                    manualPolicy.reset()
                }
                clearSuggestions()
                manualLetterGesture = letter
                try { taps.acceptLongPress(gesture.value, gesture.language) }
                finally { manualLetterGesture = false }
                refreshManualCompletions()
            }
            is SurfaceGesture.DeleteWord -> {
                if (gesture.language != taps.language) return
                epoch++
                afterSwipeWord = null
                manualPolicy.reset()
                val tracked = loggedSwipe
                val owned = ownedWordSink?.deleteOwnedWord()
                if (owned == true && tracked != null && editor === tracked.info &&
                    currentInputConnection === tracked.connection) {
                    val anchor = tracked.connection.getTextBeforeCursor(SwipeCorrectionTracker.ANCHOR_WINDOW, 0)?.toString()
                    correctionTracker.onSwipeUndone(tracked.word, anchor)
                    TelemetryRuntime.recordUndo(this, tracked.id, tracked.info)
                    undoAnchor = anchor
                    undoneSwipe = tracked
                    replacementText.clear()
                    loggedSwipe = null
                }
                clearSuggestions()
                if (owned == null) currentInputConnection?.let(::deletePreviousWord)
                refreshManualCompletions()
            }
            is SurfaceGesture.RepeatBackspace -> {
                if (gesture.language != taps.language) return
                epoch++
                deleteOnePress()
            }
            is SurfaceGesture.Swipe -> {
                afterSwipeWord = null
                manualPolicy.reset()
                decode(gesture)
            }
        }
    }

    private fun verifiedSuffix(record: LoggedSwipe): Boolean =
        editor === record.info && currentInputConnection === record.connection &&
            record.connection.getSelectedText(0).isNullOrEmpty() &&
            record.connection.getTextBeforeCursor(record.word.length, 0)?.toString() == record.word

    private fun finalizeLoggedWord() {
        val record = loggedSwipe ?: return
        if (verifiedSuffix(record)) TelemetryRuntime.recordFinalization(this, record.id, record.info, record.word)
        loggedSwipe = null // A moved cursor or unreadable editor is unresolved, never guessed.
    }

    private fun resolveCorrection(result: SwipeCorrectionTracker.Correction?) {
        val info = editor ?: return
        val chosen = result?.chosen ?: return
        if (!TelemetryPolicy.word(chosen)) return
        result.rejected.forEach { rejected ->
            rejected.traceId?.let { id ->
                if (TelemetryPolicy.word(rejected.word)) {
                    TelemetryRuntime.recordCorrection(this, id, info, rejected.word, chosen, "manual")
                    TelemetryRuntime.recordFinalization(this, id, info, chosen)
                }
            }
        }
    }

    private fun finishUndoneWord() {
        val record = undoneSwipe ?: return
        if (editor !== record.info || currentInputConnection !== record.connection) {
            undoneSwipe = null; undoAnchor = null; replacementText.clear(); correctionTracker.clear(); return
        }
        val replacement = replacementText.toString()
        val before = record.connection.getTextBeforeCursor(
            SwipeCorrectionTracker.ANCHOR_WINDOW + replacement.length + SwipeCorrectionTracker.RESOLUTION_SLACK, 0)?.toString()
        if (replacement.isNotEmpty() && TelemetryPolicy.word(replacement) &&
            before?.endsWith(replacement) == true) {
            resolveCorrection(correctionTracker.onWordCommitted(replacement, before))
        } else if (replacement.isEmpty()) {
            if (undoAnchor != null &&
                record.connection.getSelectedText(0).isNullOrEmpty() &&
                record.connection.getTextBeforeCursor(SwipeCorrectionTracker.ANCHOR_WINDOW, 0)?.toString() == undoAnchor) {
                TelemetryRuntime.recordFinalization(this, record.id, record.info, null)
            }
            correctionTracker.settleOrClear()
        } else correctionTracker.clear()
        undoneSwipe = null
        undoAnchor = null
        replacementText.clear()
    }

    private fun recordDecodedSwipe(info: EditorInfo, connection: InputConnection,
                                   gesture: SurfaceGesture.Swipe, geometry: SurfaceGeometry,
                                   words: List<String>, committed: String) {
        if (!TelemetryRuntime.collecting || gesture.language != KeyboardLanguage.ESTONIAN ||
            !TelemetryPolicy.editor(info) || !TelemetryPolicy.word(committed) || gesture.samples.size < 2) return
        val samples = gesture.samples
        val count = minOf(samples.size, 256)
        val compressed = (0 until count).map { i -> samples[i * (samples.size - 1) / (count - 1)] }
        val now = SystemClock.uptimeMillis()
        val id = UUID.randomUUID().toString().replace("-", "")
        val trace = SwipeTrace(id, "et", info,
            compressed.map { sample -> TelemetryPoint(
                (sample.x / geometry.width).coerceIn(0f, 1f),
                (sample.y / geometry.height).coerceIn(0f, 1f), sample.timeMs) },
            TelemetryGeometry(geometry.width, geometry.height, geometry.keys
                .filter { it.kind == KeyKind.LETTER }.map { key ->
                    TelemetryKey(key.label, key.bounds.left, key.bounds.top, key.bounds.right, key.bounds.bottom)
                }),
            words.filter(TelemetryPolicy::word).take(5), committed, samples.first().timeMs, now)
        TelemetryRuntime.recordSwipe(this, trace)
        loggedSwipe = LoggedSwipe(id, info, connection, committed)
        val resolution = correctionTracker.onSwipeAutoInserted(
            SwipeCorrectionTracker.SwipeRecord(committed, words.take(5), id),
            connection.getTextBeforeCursor(SwipeCorrectionTracker.ANCHOR_WINDOW + committed.length +
                SwipeCorrectionTracker.RESOLUTION_SLACK, 0)?.toString())
        resolveCorrection(resolution)
        undoneSwipe = null
        undoAnchor = null
        replacementText.clear()
    }

    private fun finishTelemetryField() {
        finalizeLoggedWord()
        finishUndoneWord()
        correctionTracker.clear()
    }

    /** Hide the normal word bar while browsing; reuse its 48dp for emoji content. */
    internal fun resizeForEmoji(expanded: Boolean, search: Boolean = false) {
        val dp = resources.displayMetrics.density
        val keyHeight = (286 * dp).toInt()
        val searchHeight = (100 * dp).toInt()
        val envelope = (48 * dp).toInt() + keyHeight
        (surface?.parent as? ViewGroup)?.getChildAt(0)?.visibility =
            if (expanded) View.GONE else View.VISIBLE
        emojiPanel?.let { panel ->
            panel.layoutParams?.let { params ->
                params.height = if (expanded && !search) envelope else searchHeight
                panel.layoutParams = params
            }
        }
        surface?.let { view ->
            val params = view.layoutParams ?: return@let
            params.height = if (expanded && search) envelope - searchHeight else keyHeight
            view.layoutParams = params
            view.visibility = if (expanded && !search) View.GONE else View.VISIBLE
        }
    }

    /** Keep the picker inside the IME view so Android resizes the editor above it. */
    private fun showEmojiPanel() {
        val anchor = surface ?: return
        val panel = emojiPanel ?: return
        if (panel.visibility == View.VISIBLE) { closeEmojiPanel(); return }
        val connection = currentInputConnection ?: return
        val field = editor ?: return
        epoch++ // An older decode must not commit behind the emoji browser.
        clearSuggestions()
        finalizeLoggedWord()
        finishUndoneWord()
        afterSwipeWord = null
        manualPolicy.reset()
        correctionTracker.clear()
        panel.setSearchIndex(emojiSearch)
        panel.setSearchMode(false)
        panel.clearQuery()
        panel.onSearchModeChanged = { searching ->
            if (emojiPanel === panel && panel.visibility == View.VISIBLE) resizeForEmoji(true, searching)
        }
        panel.onClose = ::closeEmojiPanel
        panel.onEmojiPicked = { emoji ->
            val inserted = panel.visibility == View.VISIBLE && editor === field &&
                currentInputConnection === connection && surface === anchor && connection.commitText(emoji, 1)
            if (inserted) closeEmojiPanel()
            inserted
        }
        resizeForEmoji(true, false)
        panel.visibility = View.VISIBLE
    }

    private fun closeEmojiPanel() {
        emojiPanel?.let { panel ->
            panel.visibility = View.GONE
            panel.onSearchModeChanged = null
            panel.setSearchMode(false)
            panel.clearQuery()
            panel.onEmojiPicked = null
            panel.onClose = null
        }
        resizeForEmoji(false)
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
        if (undoneSwipe != null && replacementText.isNotEmpty()) finishUndoneWord()
        finalizeLoggedWord()
        manualCompletions?.clear()
        val ticket = suggestions.begin()
        renderSuggestions()
        if (ticket == null) return
        val accept: (PredictionResult) -> Unit = { result ->
            if (owner == epoch && emojiPanel?.visibility != View.VISIBLE &&
                editor === info && currentInputConnection === connection &&
                surface === view && view.geometry === geometry && taps.language == gesture.language
            ) {
                writingSuggestion = true
                try {
                    SwipeStudyBridge.onSwipeDecoded(info, gesture.samples, geometry, result.words)
                    if (suggestions.accept(ticket, result)) {
                        afterSwipeWord = suggestions.composingWord
                        ownSelectionAfterWrite(connection)
                        afterSwipeWord?.let { committed ->
                            recordDecodedSwipe(info, connection, gesture, geometry, result.words, committed)
                        }
                        renderSuggestions()
                    }
                } finally { writingSuggestion = false }
            }
        }
        val fallback = {
            if (owner == epoch && emojiPanel?.visibility != View.VISIBLE &&
                editor === info && currentInputConnection === connection) {
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
            // Repeating a code-point deletion on an empty Compose field can
            // throw inside the app's EditProcessor and terminate its process.
            // Never issue it without observing at least one preceding character.
            val before = connection.getTextBeforeCursor(2, 0)
            if (before == null) {
                // Blind editors cannot prove that a code-point deletion is safe at
                // offset zero. Route a normal DEL key instead; never send the
                // crashing deleteSurroundingTextInCodePoints command blindly.
                val down = connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                val up = connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                return down && up
            }
            if (before.isEmpty()) return false
            if (connection.deleteSurroundingTextInCodePoints(1, 0)) return true
            val down = connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            val up = connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            return down && up
        }

        /** Select a bounded verified word plus any trailing whitespace before deleting. */
        internal fun deletePreviousWord(connection: InputConnection): Boolean {
            val selection = connection.getExtractedText(ExtractedTextRequest(), 0) ?: return false
            val end = selection.startOffset.toLong() + selection.selectionEnd
            if (selection.startOffset < 0 || selection.selectionEnd < 0 ||
                selection.selectionStart != selection.selectionEnd || end <= 0 || end > Int.MAX_VALUE) return false
            val caret = end.toInt()
            val before = connection.getTextBeforeCursor(256, 0)?.toString() ?: return false
            if (before.isEmpty()) return false
            var start = before.length
            while (start > 0 && before[start - 1].isWhitespace()) start--
            while (start > 0) {
                val cp = Character.codePointBefore(before, start)
                val type = Character.getType(cp)
                if (!Character.isLetterOrDigit(cp) && type != Character.NON_SPACING_MARK.toInt() &&
                    type != Character.COMBINING_SPACING_MARK.toInt() && cp != '-'.code && cp != '\''.code) break
                start -= Character.charCount(cp)
            }
            // An incomplete 256-unit prefix might split a word; never remove an unknown suffix.
            if (start == 0 && before.length == 256 && caret > 256) return false
            if (start == before.length) return false
            val from = caret - (before.length - start)
            if (from < 0 || !connection.setSelection(from, caret)) return false
            if (connection.getSelectedText(0)?.toString() != before.substring(start)) {
                connection.setSelection(caret, caret)
                return false
            }
            if (!connection.commitText("", 1)) {
                connection.setSelection(caret, caret)
                return false
            }
            return true
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

    private fun collapsedCaret(): Int? {
        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0) ?: return null
        if (extracted.startOffset < 0 || extracted.selectionEnd < 0 ||
            extracted.selectionStart != extracted.selectionEnd) return null
        val absolute = extracted.startOffset.toLong() + extracted.selectionEnd
        return if (absolute in 0..Int.MAX_VALUE.toLong()) absolute.toInt() else null
    }

    /** null = not our word; false = editor rejected deletion; true = word removed. */
    fun deleteOwnedWord(): Boolean? {
        val word = ownedWord ?: return null
        if (!isCurrent() || pendingSeparator.isNotEmpty() ||
            connection.getTextBeforeCursor(word.length, 0)?.toString() != word) return null
        val end = collapsedCaret() ?: return null
        if (end < word.length) return null
        if (!connection.setSelection(end - word.length, end)) return false
        if (connection.getSelectedText(0)?.toString() != word) {
            connection.setSelection(end, end)
            return false
        }
        val deleted = connection.commitText("", 1)
        if (!deleted) connection.setSelection(end, end)
        else forget()
        return deleted
    }

    override fun beforeCursor(): String? =
        if (isCurrent()) connection.getTextBeforeCursor(4096, 0)?.toString() else null

    override fun setComposingText(value: String): Boolean {
        if (!isCurrent()) return false
        val previous = ownedWord
        if (previous != null) {
            if (pendingSeparator.isNotEmpty() || connection.getSelectedText(0)?.isNotEmpty() == true ||
                connection.getTextBeforeCursor(previous.length, 0)?.toString() != previous) return false
            val end = collapsedCaret() ?: return false // A blind editor cannot prove the range we own.
            if (end < previous.length) return false
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
    fun acceptLongPress(value: String, activeLanguage: KeyboardLanguage) {
        if (activeLanguage != language || value.isEmpty() || value.length > 4 ||
            value.any { it.code < 32 || it.code == 127 }) return
        commit(if (shifted && value.length == 1 && value[0].isLetter()) value.uppercase() else value)
        shifted = false
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
