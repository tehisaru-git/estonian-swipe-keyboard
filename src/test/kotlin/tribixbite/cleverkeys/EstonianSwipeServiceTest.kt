package tribixbite.cleverkeys

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import android.widget.LinearLayout
import android.widget.Button
import android.widget.EditText
import org.robolectric.RuntimeEnvironment
import android.view.View
import android.view.KeyEvent
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
        input.accept(tap(et, KeyKind.LANGUAGE))
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

    @Test fun inputViewWrapsKeyboardWithThreeHiddenCandidates() {
        val service = Robolectric.buildService(EstonianSwipeService::class.java).get()
        val layout = service.onCreateInputView() as LinearLayout
        assertEquals(3, layout.childCount)
        val bar = layout.getChildAt(0) as LinearLayout
        assertEquals(View.GONE, bar.visibility)
        assertEquals(3, bar.childCount)
        assertTrue((0..2).all { bar.getChildAt(it) is Button })
        assertTrue(layout.getChildAt(1) is EmojiPanelView)
        assertEquals(View.GONE, layout.getChildAt(1).visibility)
        assertTrue(layout.getChildAt(2) is KeyboardSurfaceView)
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
