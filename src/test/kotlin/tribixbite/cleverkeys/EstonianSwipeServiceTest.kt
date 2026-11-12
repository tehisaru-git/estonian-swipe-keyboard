package tribixbite.cleverkeys

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.HorizontalScrollView
import org.robolectric.RuntimeEnvironment
import android.view.View
import android.view.KeyEvent
import android.view.MotionEvent
import tribixbite.cleverkeys.swipeui.SwipeSuggestionSession
import tribixbite.cleverkeys.PredictionResult
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnectionWrapper
import tribixbite.cleverkeys.estonianui.*
import tribixbite.cleverkeys.estonianemoji.EmojiPanelView
import tribixbite.cleverkeys.a11y.KeyboardGeometry as AdapterGeometry

@RunWith(RobolectricTestRunner::class)
class EstonianSwipeServiceTest {
    private fun tap(geometry: KeyboardGeometry, kind: KeyKind, label: String? = null) =
        SurfaceGesture.Tap(geometry.keys.first { it.kind == kind && (label == null || it.label == label) }, geometry.language)

    @Test fun tapLettersShiftBackspaceSpaceAndEnter() {
        val texts = mutableListOf<String>()
        var deletes = 0
        var enters = 0
        val input = EstonianTapInput({ texts.add(it) }, { deletes++ }, { enters++ })
        val board = KeyboardGeometry(301, 235, KeyboardLanguage.ESTONIAN)
        input.accept(tap(board, KeyKind.LETTER, "õ"))
        input.accept(tap(board, KeyKind.SHIFT))
        input.accept(tap(board, KeyKind.LETTER, "ä"))
        input.accept(tap(board, KeyKind.LETTER, "ü"))
        input.accept(tap(board, KeyKind.BACKSPACE))
        input.accept(tap(board, KeyKind.SPACE))
        input.accept(tap(board, KeyKind.ENTER))
        assertEquals(listOf("õ", "Ä", "ü", " "), texts)
        assertEquals(1, deletes)
        assertEquals(1, enters)
    }

    @Test fun switchLanguageRejectsOldSurfaceEventsAndResetsShift() {
        val texts = mutableListOf<String>()
        val input = EstonianTapInput({ texts.add(it) }, {}, {})
        val et = KeyboardGeometry(301, 235, KeyboardLanguage.ESTONIAN)
        val en = KeyboardGeometry(301, 235, KeyboardLanguage.ENGLISH)
        input.accept(tap(et, KeyKind.SHIFT))
        input.switchLanguage()
        input.accept(tap(et, KeyKind.LETTER, "õ"))
        input.accept(tap(en, KeyKind.LETTER, "a"))
        assertEquals(KeyboardLanguage.ENGLISH, input.language)
        assertEquals(listOf("a"), texts)
    }

    @Test fun symbolsAndEmojiDoNotChangeLanguage() {
        val texts = mutableListOf<String>()
        var symbols = 0
        var emoji = 0
        val input = EstonianTapInput({ texts.add(it) }, {}, {}, { symbols++ }, { emoji++ })
        val letters = KeyboardGeometry(301, 235, KeyboardLanguage.ESTONIAN)
        val page = KeyboardGeometry(301, 235, KeyboardLanguage.ESTONIAN, symbolMode = true)
        input.accept(tap(letters, KeyKind.SYMBOLS))
        input.accept(tap(page, KeyKind.SYMBOL, "€"))
        input.accept(tap(page, KeyKind.EMOJI))
        assertEquals(1, symbols)
        assertEquals(1, emoji)
        assertEquals(listOf("€"), texts)
        assertEquals(KeyboardLanguage.ESTONIAN, input.language)
    }

    private class Buffer : SwipeSuggestionSession.Sink {
        var text = ""
        var composing = ""
        override fun beforeCursor(): String = text
        override fun setComposingText(value: String): Boolean {
            if (composing.isNotEmpty()) text = text.removeSuffix(composing)
            text += value
            composing = value
            return true
        }
        override fun finishComposingText(): Boolean { composing = ""; return true }
        override fun commitText(value: String): Boolean { text += value; return true }
    }

    @Test fun suggestionControllerComposesAndRendersThreeSelectableSlots() {
        val buffer = Buffer()
        val controller = EstonianSuggestionController()
        controller.start(buffer, allowed = true)
        val ticket = controller.begin()!!
        assertTrue(controller.accept(ticket, PredictionResult(listOf("tere", "tore", "tõre", "terve"), listOf(4, 3, 2, 1))))
        assertEquals(listOf("tere", "tore", "tõre"), controller.words)
        assertEquals("tere", buffer.text)
        assertTrue(controller.choose(2))
        assertEquals("tõre", buffer.text)
        assertEquals("tõre", buffer.composing)
    }

    @Test fun suggestionControllerConsecutiveSwipesAndExternalCursorRevoke() {
        val buffer = Buffer()
        val controller = EstonianSuggestionController()
        controller.start(buffer, allowed = true)
        controller.accept(controller.begin()!!, PredictionResult(listOf("tere", "tore"), listOf(2, 1)))
        controller.accept(controller.begin()!!, PredictionResult(listOf("maailm", "maa"), listOf(2, 1)))
        assertEquals("tere maailm", buffer.text)
        assertEquals(listOf("maailm", "maa"), controller.words)
        controller.cursorChanged()
        assertFalse(controller.choose(1))
        assertTrue(controller.words.isEmpty())
    }

    @Test fun suggestionControllerDoesNotCorrectExternallyMutatedText() {
        val buffer = Buffer()
        val controller = EstonianSuggestionController()
        controller.start(buffer, allowed = true)
        controller.accept(controller.begin()!!, PredictionResult(listOf("tere", "tore"), listOf(2, 1)))
        buffer.text = "different"
        assertFalse(controller.choose(1))
        assertEquals("different", buffer.text)
        assertTrue(controller.words.isEmpty())
    }

    @Test fun suggestionControllerClearsOnTapFieldAndRejectsStaleDecode() {
        val buffer = Buffer()
        val controller = EstonianSuggestionController()
        controller.start(buffer, allowed = true)
        val pending = controller.begin()!!
        controller.clear()
        assertFalse(controller.accept(pending, PredictionResult(listOf("leak"), listOf(1))))
        assertEquals("", buffer.text)
        controller.start(Buffer(), allowed = false)
        assertNull(controller.begin())
        assertTrue(controller.words.isEmpty())
    }

    @Test fun longPressCommitsOnlyChosenCharacterInMatchingLanguageAndRespectsShift() {
        val typed = mutableListOf<String>()
        val input = EstonianTapInput(typed::add, {}, {})
        input.acceptLongPress("?", KeyboardLanguage.ESTONIAN)
        input.acceptLongPress("ğ", KeyboardLanguage.ENGLISH)
        val geometry = KeyboardGeometry(330, 250, KeyboardLanguage.ESTONIAN)
        input.accept(SurfaceGesture.Tap(geometry.keys.first { it.kind == KeyKind.SHIFT }, KeyboardLanguage.ESTONIAN))
        input.acceptLongPress("ğ", KeyboardLanguage.ESTONIAN)
        assertEquals(listOf("?", "Ğ"), typed)
    }

    @Test fun emojiBrowseAndSearchReuseKeyboardHeightWithoutHidingEditor() {
        val service = Robolectric.buildService(EstonianSwipeService::class.java).get()
        val layout = service.onCreateInputView() as LinearLayout
        val bar = layout.getChildAt(0)
        val panel = layout.getChildAt(1) as EmojiPanelView
        val keys = layout.getChildAt(2) as KeyboardSurfaceView
        val base = bar.layoutParams.height + keys.layoutParams.height
        panel.visibility = View.VISIBLE
        service.resizeForEmoji(true, false)
        assertEquals(View.GONE, bar.visibility)
        assertEquals(View.GONE, keys.visibility)
        assertEquals(base, panel.layoutParams.height)
        service.resizeForEmoji(true, true)
        assertEquals(View.GONE, bar.visibility)
        assertEquals(View.VISIBLE, keys.visibility)
        assertEquals(base, panel.layoutParams.height + keys.layoutParams.height)
        service.resizeForEmoji(false)
        panel.visibility = View.GONE
        assertEquals(View.VISIBLE, bar.visibility)
        assertEquals(base, bar.layoutParams.height + keys.layoutParams.height)
    }

    @Test fun longCandidatesStayInOneStableRowAndAreFullyReachable() {
        val service = Robolectric.buildService(EstonianSwipeService::class.java).get()
        val layout = service.onCreateInputView() as LinearLayout
        val strip = layout.getChildAt(0) as FrameLayout
        val row = strip.getChildAt(1) as LinearLayout
        row.visibility = View.VISIBLE
        val words = listOf("funktsionaalsus", "funktsionaalseid", "funktsionaalsuses")
        val track = (row.getChildAt(1) as HorizontalScrollView).getChildAt(0) as LinearLayout
        val buttons = (0..2).map { track.getChildAt(it * 2) as CandidateLabelView }
        buttons.zip(words).forEach { (button, word) -> button.bind(word) }
        val width = View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(88, View.MeasureSpec.EXACTLY)
        strip.measure(width, height)
        strip.layout(0, 0, 720, 88)
        buttons.forEachIndexed { index, button ->
            assertEquals(words[index], button.contentDescription)
            assertEquals(1, button.lineCount)
            assertTrue(button.measuredHeight <= 88)
            assertTrue(button.isClickable)
        }
        assertEquals(88, strip.measuredHeight)
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun longCandidateWordsStayWholeAndTheStripScrollsSideways() {
        val service = Robolectric.buildService(EstonianSwipeService::class.java).get()
        val layout = service.onCreateInputView() as LinearLayout
        val bar = layout.getChildAt(0) as FrameLayout
        val candidates = bar.getChildAt(1) as LinearLayout
        candidates.visibility = View.VISIBLE
        val scroll = candidates.getChildAt(1) as HorizontalScrollView
        val strip = scroll.getChildAt(0) as LinearLayout
        val first = strip.getChildAt(0) as CandidateLabelView
        val second = strip.getChildAt(2) as CandidateLabelView
        val third = strip.getChildAt(4) as CandidateLabelView
        val words = listOf("rahvusvahelistumine", "ebaproportsionaalsusega", "mitmetähenduslikkusest")
        listOf(first, second, third).forEachIndexed { index, button -> button.bind(words[index]) }
        strip.getChildAt(1).visibility = View.VISIBLE
        strip.getChildAt(3).visibility = View.VISIBLE
        val width = View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(88, View.MeasureSpec.EXACTLY)
        candidates.measure(width, height)
        candidates.layout(0, 0, 360, 88)
        assertEquals(words[0], first.text.toString())
        assertEquals(words[2], third.contentDescription.toString())
        assertNull("a long word must not be silently ellipsized", first.ellipsize)
        assertTrue("candidate viewport must be measured: ${scroll.width}", scroll.width > 0)
        assertTrue("candidate strip must be measured: ${strip.width}", strip.width > 0)
        assertTrue("the complete alternatives need horizontal overflow: ${strip.width} vs ${scroll.width}",
            strip.width > scroll.width)
        scroll.scrollTo(180, 0)
        assertTrue(scroll.scrollX > 0)
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun shortCandidatesCannotScrollWhenAllWordsFitTheRow() {
        val service = Robolectric.buildService(EstonianSwipeService::class.java).get()
        val layout = service.onCreateInputView() as LinearLayout
        val candidates = (layout.getChildAt(0) as FrameLayout).getChildAt(1) as LinearLayout
        candidates.visibility = View.VISIBLE
        val scroll = candidates.getChildAt(1) as HorizontalScrollView
        val track = scroll.getChildAt(0) as LinearLayout
        listOf("ja", "on", "ei").forEachIndexed { index, word ->
            (track.getChildAt(index * 2) as CandidateLabelView).bind(word)
        }
        track.getChildAt(1).visibility = View.VISIBLE
        track.getChildAt(3).visibility = View.VISIBLE
        val width = View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(88, View.MeasureSpec.EXACTLY)
        candidates.measure(width, height)
        candidates.layout(0, 0, 320, 88)
        assertTrue("short words should fit without overflow", track.width <= scroll.width)
        assertEquals("fitting words should use the whole row", scroll.width, track.width)
        val labels = (0..2).map { track.getChildAt(it * 2) as CandidateLabelView }
        assertTrue("short words should be spread evenly: ${labels.map { it.width }}",
            labels.maxOf { it.width } - labels.minOf { it.width } <= 2)
        assertTrue("each short word should receive a wide tap slot", labels.all { it.width >= (scroll.width - 2) / 3 - 2 })
        scroll.scrollTo(100, 0)
        assertEquals("a fitting row must not move", 0, scroll.scrollX)
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun draggingAnOverflowingRowNeverClicksAWord() {
        val service = Robolectric.buildService(EstonianSwipeService::class.java).get()
        val layout = service.onCreateInputView() as LinearLayout
        val row = (layout.getChildAt(0) as FrameLayout).getChildAt(1) as LinearLayout
        row.visibility = View.VISIBLE
        val scroll = row.getChildAt(1) as HorizontalScrollView
        val track = scroll.getChildAt(0) as LinearLayout
        var selected = 0
        listOf("rahvusvahelistumine", "ebaproportsionaalsusega", "mitmetähenduslikkusest")
            .forEachIndexed { index, word ->
                (track.getChildAt(index * 2) as CandidateLabelView).apply {
                    bind(word)
                    setOnClickListener { selected++ }
                }
            }
        track.getChildAt(1).visibility = View.VISIBLE
        track.getChildAt(3).visibility = View.VISIBLE
        row.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(88, View.MeasureSpec.EXACTLY))
        row.layout(0, 0, 360, 88)
        fun send(action: Int, x: Float, time: Long) {
            MotionEvent.obtain(0, time, action, x, 44f, 0).also {
                scroll.dispatchTouchEvent(it)
                it.recycle()
            }
        }
        send(MotionEvent.ACTION_DOWN, 230f, 1)
        send(MotionEvent.ACTION_MOVE, 130f, 60)
        send(MotionEvent.ACTION_MOVE, 30f, 120)
        send(MotionEvent.ACTION_UP, 30f, 150)
        assertTrue("overflow should move with a finger", scroll.scrollX > 0)
        assertEquals("dragging must not select a word", 0, selected)
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun rebindingFitOverflowFitResetsScrollAndSpreadsShortWordsAgain() {
        val service = Robolectric.buildService(EstonianSwipeService::class.java).get()
        val layout = service.onCreateInputView() as LinearLayout
        val row = (layout.getChildAt(0) as FrameLayout).getChildAt(1) as LinearLayout
        row.visibility = View.VISIBLE
        val scroll = row.getChildAt(1) as HorizontalScrollView
        val track = scroll.getChildAt(0) as LinearLayout
        track.getChildAt(1).visibility = View.VISIBLE
        track.getChildAt(3).visibility = View.VISIBLE
        fun bind(words: List<String>, viewport: Int) {
            words.forEachIndexed { index, word ->
                (track.getChildAt(index * 2) as CandidateLabelView).bind(word)
            }
            row.measure(View.MeasureSpec.makeMeasureSpec(viewport, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(88, View.MeasureSpec.EXACTLY))
            row.layout(0, 0, viewport, 88)
        }
        bind(listOf("ja", "on", "ei"), 320)
        assertEquals(scroll.width, track.width)
        bind(listOf("rahvusvahelistumine", "ebaproportsionaalsusega", "mitmetähenduslikkusest"), 240)
        assertTrue(track.width > scroll.width)
        scroll.scrollTo(120, 0)
        assertTrue(scroll.scrollX > 0)
        bind(listOf("ja", "on", "ei"), 320)
        assertEquals(scroll.width, track.width)
        assertEquals(0, scroll.scrollX)
        val labels = (0..2).map { track.getChildAt(it * 2) as CandidateLabelView }
        assertTrue(labels.maxOf { it.width } - labels.minOf { it.width } <= 2)
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun fittingMixedLengthWordsGiveTheLongWordItsFullNaturalWidth() {
        val service = Robolectric.buildService(EstonianSwipeService::class.java).get()
        val layout = service.onCreateInputView() as LinearLayout
        val row = (layout.getChildAt(0) as FrameLayout).getChildAt(1) as LinearLayout
        row.visibility = View.VISIBLE
        val scroll = row.getChildAt(1) as HorizontalScrollView
        val track = scroll.getChildAt(0) as LinearLayout
        listOf("ja", "rahvusvahelistumine", "ei").forEachIndexed { index, word ->
            (track.getChildAt(index * 2) as CandidateLabelView).bind(word)
        }
        track.getChildAt(1).visibility = View.VISIBLE
        track.getChildAt(3).visibility = View.VISIBLE
        val labels = (0..2).map { track.getChildAt(it * 2) as CandidateLabelView }
        val naturalWidths = labels.map { label ->
            label.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(88, View.MeasureSpec.EXACTLY))
            label.measuredWidth
        }
        val separatorWidths = track.getChildAt(1).layoutParams.width + track.getChildAt(3).layoutParams.width
        val natural = naturalWidths.sum() + separatorWidths
        val rowWidth = natural + 24 + row.getChildAt(0).layoutParams.width
        row.measure(View.MeasureSpec.makeMeasureSpec(rowWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(88, View.MeasureSpec.EXACTLY))
        row.layout(0, 0, rowWidth, 88)
        assertTrue("test precondition: all complete words fit naturally", natural <= scroll.width)
        val long = labels[1]
        val longNatural = naturalWidths[1]
        assertTrue("test precondition: long word exceeds an equal slot",
            longNatural > (scroll.width - separatorWidths) / 3)
        assertTrue("fitting mixed row must not clip its long word: ${long.width} < $longNatural",
            long.width >= longNatural)
        assertEquals(scroll.width, track.width)
        scroll.scrollTo(100, 0)
        assertEquals(0, scroll.scrollX)
    }

    @Test fun candidateNavigationHasAccessibleTouchAreaWithoutGrowingIme() {
        val service = Robolectric.buildService(EstonianSwipeService::class.java).get()
        val layout = service.onCreateInputView() as LinearLayout
        val bar = layout.getChildAt(0) as FrameLayout
        val keys = layout.getChildAt(2) as KeyboardSurfaceView
        val candidates = bar.getChildAt(1) as LinearLayout
        val arrow = candidates.getChildAt(0)
        val dp = service.resources.displayMetrics.density
        assertTrue("candidate row should be at least 48dp high", bar.layoutParams.height / dp >= 48f)
        assertTrue("toolbar arrow needs a 48dp target", arrow.layoutParams.width / dp >= 48f)
        assertTrue("toolbar arrow needs a 48dp target", arrow.layoutParams.height / dp >= 48f)
        assertEquals("larger touch area must not hide the editor", 334f,
            (bar.layoutParams.height + keys.layoutParams.height) / dp, 0.1f)
    }

    @Test fun inputViewKeepsToolbarAndCandidatesInOneFixedHeightSlot() {
        val service = Robolectric.buildService(EstonianSwipeService::class.java).get()
        val layout = service.onCreateInputView() as LinearLayout
        assertEquals(3, layout.childCount)
        val bar = layout.getChildAt(0) as FrameLayout
        assertEquals(View.VISIBLE, bar.visibility)
        assertTrue(bar.layoutParams.height > 0)
        val toolbar = bar.getChildAt(0) as LinearLayout
        val candidates = bar.getChildAt(1) as LinearLayout
        assertEquals(View.VISIBLE, toolbar.visibility)
        assertEquals(View.GONE, candidates.visibility)
        assertTrue((layout.getChildAt(2).layoutParams.height / service.resources.displayMetrics.density) >= 280f)
        assertEquals(2, candidates.childCount)
        val track = (candidates.getChildAt(1) as HorizontalScrollView).getChildAt(0) as LinearLayout
        assertEquals(5, track.childCount)
        assertTrue((0..2).all { track.getChildAt(it * 2) is CandidateLabelView })
        assertTrue(toolbar.childCount >= 3)
        assertTrue(layout.getChildAt(1) is EmojiPanelView)
        assertEquals(View.GONE, layout.getChildAt(1).visibility)
        assertEquals((100 * service.resources.displayMetrics.density).toInt(), layout.getChildAt(1).layoutParams.height)
        assertTrue(layout.getChildAt(2) is KeyboardSurfaceView)
    }

    @Test fun recreatingInputViewResetsToolbarCompactState() {
        val service = Robolectric.buildService(EstonianSwipeService::class.java).get()
        fun toolbar() = ((service.onCreateInputView() as LinearLayout).getChildAt(0) as FrameLayout)
            .getChildAt(0) as LinearLayout
        val first = toolbar()
        first.getChildAt(0).performClick()
        assertEquals(View.INVISIBLE, first.getChildAt(1).visibility)
        val second = toolbar()
        assertEquals(View.VISIBLE, second.getChildAt(1).visibility)
        second.getChildAt(0).performClick()
        assertEquals(View.INVISIBLE, second.getChildAt(1).visibility)
    }

    @Test fun toolbarLanguageSwitchPreservesKeyboardHeightAndChangesDecoderLayout() {
        val service = Robolectric.buildService(EstonianSwipeService::class.java).get()
        val layout = service.onCreateInputView() as LinearLayout
        val bar = layout.getChildAt(0) as FrameLayout
        val toolbar = bar.getChildAt(0) as LinearLayout
        val keySurface = layout.getChildAt(2) as KeyboardSurfaceView
        val initialBarHeight = bar.layoutParams.height
        val initialKeysHeight = keySurface.layoutParams.height
        assertEquals(KeyboardLanguage.ESTONIAN, keySurface.language)
        val languageButton = (0 until toolbar.childCount).map { toolbar.getChildAt(it) }
            .filterIsInstance<TextView>().first { it.contentDescription == "Switch to English" }
        languageButton.performClick()
        assertEquals(KeyboardLanguage.ENGLISH, keySurface.language)
        assertEquals(initialBarHeight, bar.layoutParams.height)
        assertEquals(initialKeysHeight, keySurface.layoutParams.height)
        assertEquals("Switch to Estonian", languageButton.contentDescription)
    }

    @Test fun ownCompositionSelectionKeepsOfferButExternalSelectionDoesNot() {
        assertTrue(isOwnSuggestionSelection("tere", "tere", 4, 4, 0, 4, null))
        assertTrue(isOwnSuggestionSelection("tere", "tere", 4, 4, -1, -1, 4 to 4))
        assertFalse(isOwnSuggestionSelection("tere", "ter", 3, 3, 0, 4, 4 to 4))
        assertFalse(isOwnSuggestionSelection("tere", "tere", 2, 4, 0, 4, 4 to 4))
        assertFalse(isOwnSuggestionSelection("tere", null, 4, 4, 0, 4, 4 to 4))
        assertFalse(isOwnSuggestionSelection("tere", "tere", 4, 4, -1, -1, null))
    }

    @Test fun emojiSearchIsAnIndependentOfflinePanelNotAnInputMethodTextField() {
        val source = java.io.File("src/main/kotlin/tribixbite/cleverkeys/EstonianSwipeService.kt").readText()
        assertTrue(source.contains("OfflineEmojiSearch.fromAssets("))
        assertTrue(source.contains("EmojiPanelView(this)"))
        assertTrue(source.contains("panel.appendQuery("))
        assertFalse(source.contains("EmojiGridView"))
    }

    @Test fun inputViewAndCaretChangesInvalidatePendingDecode() {
        val source = java.io.File("src/main/kotlin/tribixbite/cleverkeys/EstonianSwipeService.kt").readText()
        assertTrue(source.contains("override fun onFinishInputView("))
        assertTrue(source.contains("override fun onUpdateSelection("))
        assertTrue(source.contains("surface?.onGesture = null"))
    }

    @Test fun ctcChecksAllEligibilityGatesBeforeDispatch() {
        val source = java.io.File("src/main/kotlin/tribixbite/cleverkeys/EstonianSwipeService.kt").readText()
        val gate = source.substringAfter("val eligible =")
        assertTrue(gate.contains("engine.hasLexiconSource(language)"))
        assertTrue(gate.contains("engine.isModelPermanentlyUnavailable(language)"))
        assertTrue(gate.contains("engine.supportsLayout(source, params"))
    }

    @Test fun committedSwipeWordSurvivesFollowingSpaceInControlledEditor() {
        val field = EditText(RuntimeEnvironment.getApplication())
        val connection = field.onCreateInputConnection(EditorInfo())
        val sink = OwnedWordSink(connection) { true }
        val session = SwipeSuggestionSession()
        val owner = session.startField(sink, isPassword = false)
        val offer = session.accept(session.beginSwipe(owner)!!,
            PredictionResult(listOf("tere", "tore"), listOf(2, 1)))!!
        assertEquals("tere", field.text.toString())
        assertTrue(session.select(owner, offer, 1))
        assertEquals("tore", field.text.toString())
        sink.forget()
        assertTrue(connection.commitText(" ", 1))
        assertEquals("tore ", field.text.toString())
        val serviceSource = java.io.File("src/main/kotlin/tribixbite/cleverkeys/EstonianSwipeService.kt").readText()
        assertFalse(serviceSource.contains("currentInputConnection?.finishComposingText()"))
    }

    @Test fun failedCandidateCommitKeepsOriginalWordAndNoStraySeparator() {
        val field = EditText(RuntimeEnvironment.getApplication())
        val base = field.onCreateInputConnection(EditorInfo())
        val connection = object : InputConnectionWrapper(base, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean =
                if (text?.toString()?.trim() == "tore" || text?.toString()?.trim() == "uus") false
                else super.commitText(text, newCursorPosition)
        }
        val sink = OwnedWordSink(connection) { true }
        val session = SwipeSuggestionSession()
        val owner = session.startField(sink, isPassword = false)
        val first = session.accept(session.beginSwipe(owner)!!,
            PredictionResult(listOf("tere", "tore"), listOf(2, 1)))!!
        assertFalse(session.select(owner, first, 1))
        assertEquals("tere", field.text.toString())
        assertNull(session.accept(session.beginSwipe(owner)!!,
            PredictionResult(listOf("uus"), listOf(1))))
        assertEquals("tere", field.text.toString())
    }

    @Test fun candidateCorrectionWorksWithoutCodePointDeletionSupport() {
        val field = EditText(RuntimeEnvironment.getApplication())
        val base = field.onCreateInputConnection(EditorInfo())
        val connection = object : InputConnectionWrapper(base, false) {
            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int) = false
        }
        val sink = OwnedWordSink(connection) { true }
        val session = SwipeSuggestionSession()
        val owner = session.startField(sink, false)
        val offer = session.accept(session.beginSwipe(owner)!!,
            PredictionResult(listOf("tere", "tore"), listOf(2, 1)))!!
        assertTrue(session.select(owner, offer, 1))
        assertEquals("tore", field.text.toString())
    }

    @Test fun estonianHeldLettersCommitRareDiacriticsWithoutChangingEnglish() {
        val committed = mutableListOf<String>()
        val taps = EstonianTapInput(committed::add, {}, {})
        val geometry = KeyboardGeometry(330, 250, KeyboardLanguage.ESTONIAN)
        taps.acceptHold(SurfaceGesture.Hold(geometry.rows[2][1], KeyboardLanguage.ESTONIAN))
        taps.acceptHold(SurfaceGesture.Hold(geometry.rows[3][1], KeyboardLanguage.ESTONIAN))
        assertEquals(listOf("š", "ž"), committed)
        taps.switchLanguage()
        taps.acceptHold(SurfaceGesture.Hold(geometry.rows[2][1], KeyboardLanguage.ESTONIAN))
        assertEquals(listOf("š", "ž"), committed)
    }

    @Test fun declinedEditorActionFallsBackToEnterKeyEvents() {
        val base = EditText(RuntimeEnvironment.getApplication()).onCreateInputConnection(EditorInfo())
        val events = mutableListOf<Pair<Int, Int>>()
        val connection = object : InputConnectionWrapper(base, false) {
            override fun performEditorAction(editorAction: Int): Boolean = false
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                events += event.action to event.keyCode
                return true
            }
        }
        assertTrue(EstonianSwipeService.submitEnter(connection, EditorInfo.IME_ACTION_SEND))
        assertEquals(listOf(KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_ENTER,
            KeyEvent.ACTION_UP to KeyEvent.KEYCODE_ENTER), events)
    }

    @Test fun measuredSurfaceWarmsEstonianDecoderBeforeFirstSwipe() {
        val source = java.io.File("src/main/kotlin/tribixbite/cleverkeys/EstonianSwipeService.kt").readText()
        assertTrue(source.contains("engine.warmUpAsync(source, params"))
    }

    @Test fun serviceInitializesDecoderConfigOnColdStart() {
        val source = java.io.File("src/main/kotlin/tribixbite/cleverkeys/EstonianSwipeService.kt").readText()
        assertTrue(source.contains("Config.initGlobalConfig("))
        assertTrue(source.contains("CtcInstalledPacks.bind(this)"))
    }

    @Test fun backspaceImmediatelyAfterSwipeDeletesOnlyOwnedWord() {
        val field = EditText(RuntimeEnvironment.getApplication()).apply { setText("algus "); setSelection(text.length) }
        val connection = field.onCreateInputConnection(EditorInfo())
        val sink = OwnedWordSink(connection) { true }
        val session = SwipeSuggestionSession()
        val owner = session.startField(sink, false)
        session.accept(session.beginSwipe(owner)!!, PredictionResult(listOf("tere"), listOf(1)))!!
        assertEquals("algus tere", field.text.toString())
        assertEquals(true, sink.deleteOwnedWord())
        assertEquals("algus ", field.text.toString())
        assertNull(sink.deleteOwnedWord())
    }

    @Test fun failedOwnedWordDeleteNeverFallsBackToCharacterDeletion() {
        val field = EditText(RuntimeEnvironment.getApplication())
        val base = field.onCreateInputConnection(EditorInfo())
        val connection = object : InputConnectionWrapper(base, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean =
                if (text?.isEmpty() == true) false else super.commitText(text, newCursorPosition)
        }
        val sink = OwnedWordSink(connection) { true }
        val session = SwipeSuggestionSession()
        val owner = session.startField(sink, false)
        session.accept(session.beginSwipe(owner)!!, PredictionResult(listOf("tere"), listOf(1)))!!
        assertEquals(false, sink.deleteOwnedWord())
        assertEquals("tere", field.text.toString())
    }

    @Test fun wordDeleteSelectsPreviousWordAndSeparatingWhitespace() {
        val field = EditText(RuntimeEnvironment.getApplication()).apply {
            setText("üks kaks "); setSelection(text.length)
        }
        val connection = field.onCreateInputConnection(EditorInfo())
        assertTrue(EstonianSwipeService.deletePreviousWord(connection))
        assertEquals("üks ", field.text.toString())
        assertTrue(EstonianSwipeService.deletePreviousWord(connection))
        assertEquals("", field.text.toString())
    }

    @Test fun wordDeleteDeclinesUninspectableEditorWithoutDestroyingText() {
        val field = EditText(RuntimeEnvironment.getApplication()).apply { setText("tere"); setSelection(text.length) }
        val base = field.onCreateInputConnection(EditorInfo())
        val blind = object : InputConnectionWrapper(base, false) {
            override fun getExtractedText(request: android.view.inputmethod.ExtractedTextRequest?, flags: Int) = null
        }
        assertFalse(EstonianSwipeService.deletePreviousWord(blind))
        assertEquals("tere", field.text.toString())
    }

    @Test fun offsetExtractedTextDeleteWordLeavesIdenticalEarlierWordUntouched() {
        val field = EditText(RuntimeEnvironment.getApplication())
        field.setText("foo foo")
        field.setSelection(field.length())
        val connection = offsetConnection(field)
        assertTrue(EstonianSwipeService.deletePreviousWord(connection))
        assertEquals("foo ", field.text.toString())
    }

    @Test fun offsetExtractedTextOwnedDeleteLeavesIdenticalEarlierWordUntouched() {
        val field = EditText(RuntimeEnvironment.getApplication())
        field.setText("foo ")
        field.setSelection(field.length())
        val connection = offsetConnection(field)
        val sink = OwnedWordSink(connection) { true }
        assertTrue(sink.setComposingText("foo"))
        assertEquals("foo foo", field.text.toString())
        assertEquals(true, sink.deleteOwnedWord())
        assertEquals("foo ", field.text.toString())
    }

    @Test fun offsetExtractedTextCorrectionReplacesOnlyOwnedWord() {
        val field = EditText(RuntimeEnvironment.getApplication())
        field.setText("foo ")
        field.setSelection(field.length())
        val connection = offsetConnection(field)
        val sink = OwnedWordSink(connection) { true }
        assertTrue(sink.setComposingText("foo"))
        assertTrue(sink.setComposingText("bar"))
        assertEquals("foo bar", field.text.toString())
    }

    private fun offsetConnection(field: EditText): android.view.inputmethod.InputConnection {
        val base = field.onCreateInputConnection(EditorInfo())
        return object : InputConnectionWrapper(base, false) {
            override fun getExtractedText(request: android.view.inputmethod.ExtractedTextRequest?, flags: Int): android.view.inputmethod.ExtractedText? {
                val extracted = super.getExtractedText(request, flags) ?: return null
                extracted.startOffset = 4
                extracted.text = field.text.substring(4)
                extracted.selectionStart -= 4
                extracted.selectionEnd -= 4
                return extracted
            }
        }
    }

    @Test fun blindEditorBackspaceUsesKeyEventsInsteadOfUnsafeCodePointDeletion() {
        val field = EditText(RuntimeEnvironment.getApplication())
        field.setText("ab")
        field.setSelection(2)
        val base = field.onCreateInputConnection(EditorInfo())
        var codePointDeletes = 0
        val connection = object : InputConnectionWrapper(base, false) {
            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? = null
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DEL &&
                    field.length() > 0) field.text.delete(field.length() - 1, field.length())
                return true
            }
            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                codePointDeletes++
                return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
            }
        }
        assertTrue(EstonianSwipeService.deleteBackward(connection))
        assertEquals("a", field.text.toString())
        assertEquals(0, codePointDeletes)
    }

    @Test fun repeatBackspaceAtEmptyEditorDoesNotSendDeleteCommand() {
        val field = EditText(RuntimeEnvironment.getApplication())
        val base = field.onCreateInputConnection(EditorInfo())
        var deletes = 0
        val connection = object : InputConnectionWrapper(base, false) {
            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                deletes++
                return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
            }
        }
        assertFalse(EstonianSwipeService.deleteBackward(connection))
        assertEquals(0, deletes)
    }

    @Test fun backspaceRemovesSelectedTextOrWholeUnicodeCodePoint() {
        val field = EditText(RuntimeEnvironment.getApplication())
        field.setText("a😀b")
        val connection = field.onCreateInputConnection(EditorInfo())
        field.setSelection(1, 3) // the surrogate pair is selected
        assertTrue(EstonianSwipeService.deleteBackward(connection))
        assertEquals("ab", field.text.toString())
        field.setSelection(1)
        assertTrue(EstonianSwipeService.deleteBackward(connection))
        assertEquals("b", field.text.toString())
    }

    @Test fun passwordEditorsNeverEnterSwipePrediction() {
        val variations = listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        for (variation in variations) {
            val editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or variation }
            assertFalse("password variation $variation", EstonianSwipeService.isSwipeAllowed(editor))
        }
        val numeric = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        assertFalse(EstonianSwipeService.isSwipeAllowed(numeric))
        val ordinary = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        assertTrue(EstonianSwipeService.isSwipeAllowed(ordinary))
    }

    @Test fun adapterCellsMatchSurfaceCellsIncludingOddPixelWidths() {
        val board = KeyboardGeometry(301, 233, KeyboardLanguage.ESTONIAN)
        val keyboard = EstonianSwipeService.adapterKeyboard(board)
        val rects = AdapterGeometry.computeKeyRects(keyboard, AdapterGeometry.Params(1f, 1f, 0f, 0f))
        assertEquals(board.keys.size, rects.size)
        board.keys.zip(rects).forEach { (key, rect) ->
            assertEquals(key.bounds.left.toFloat(), rect.bounds.left, 0.001f)
            assertEquals(key.bounds.right.toFloat(), rect.bounds.right, 0.001f)
            assertEquals(key.bounds.top.toFloat(), rect.bounds.top, 0.001f)
            assertEquals(key.bounds.bottom.toFloat(), rect.bounds.bottom, 0.001f)
        }
    }
}
