package tribixbite.cleverkeys.swipeui

import org.junit.Assert.*
import org.junit.Test
import tribixbite.cleverkeys.PredictionResult

class SwipeSuggestionSessionTest {
    private class Editor(var text: String = "") : SwipeSuggestionSession.Sink {
        var composing = ""
        override fun beforeCursor(): String = text
        override fun setComposingText(value: String): Boolean {
            if (composing.isNotEmpty() && text.endsWith(composing)) text = text.dropLast(composing.length)
            text += value
            composing = value
            return true
        }
        override fun finishComposingText(): Boolean { composing = ""; return true }
        override fun commitText(value: String): Boolean { text += value; return true }
    }

    private fun result(vararg words: String) = PredictionResult(words.toList(), words.indices.map { 100 - it })

    @Test fun topThreeComposeAndReplaceInPlace() {
        val editor = Editor()
        val session = SwipeSuggestionSession()
        val field = session.startField(editor, isPassword = false)
        val request = session.beginSwipe(field)!!
        val offer = session.accept(request, result("tere", "tore", "tõre", "terve"))!!
        assertEquals(listOf("tere", "tore", "tõre"), offer.words)
        assertEquals("tere", editor.text)
        assertEquals("tere", editor.composing)
        assertTrue(session.select(field, offer, 2))
        assertEquals("tõre", editor.text)
        assertEquals("tõre", editor.composing)
    }

    @Test fun consecutiveSwipesSeparateWordsAndKeepLatestCorrectable() {
        val editor = Editor()
        val session = SwipeSuggestionSession()
        val field = session.startField(editor, isPassword = false)
        val first = session.accept(session.beginSwipe(field)!!, result("tere", "tore"))!!
        val second = session.accept(session.beginSwipe(field)!!, result("maailm", "sõber"))!!
        assertEquals("tere maailm", editor.text)
        assertEquals("maailm", editor.composing)
        assertFalse(session.select(field, first, 1))
        assertTrue(session.select(field, second, 1))
        assertEquals("tere sõber", editor.text)
    }

    @Test fun punctuationAndExistingSpaceArePreserved() {
        for ((prefix, expected) in listOf("Tere," to "Tere, maailm", "Tere. " to "Tere. maailm", "( " to "( maailm", "(" to "(maailm", "" to "maailm")) {
            val editor = Editor(prefix)
            val session = SwipeSuggestionSession()
            val field = session.startField(editor, isPassword = false)
            session.accept(session.beginSwipe(field)!!, result("maailm"))
            assertEquals(prefix, expected, editor.text)
        }
    }

    @Test fun staleDecodeAndOldOffersCannotWriteIntoAnotherField() {
        val one = Editor()
        val two = Editor("private ")
        val session = SwipeSuggestionSession()
        val oldField = session.startField(one, isPassword = false)
        val oldRequest = session.beginSwipe(oldField)!!
        val oldOffer = session.accept(oldRequest, result("vana", "uus"))!!
        val pending = session.beginSwipe(oldField)!!
        val newField = session.startField(two, isPassword = false)
        assertNull(session.accept(pending, result("leak")))
        assertNull(session.beginSwipe(oldField))
        assertFalse(session.select(newField, oldOffer, 1))
        assertEquals("private ", two.text)
        assertEquals("vana", one.text)
    }

    @Test fun cursorChangeInvalidatesInFlightDecodeAndOffer() {
        val editor = Editor()
        val session = SwipeSuggestionSession()
        val field = session.startField(editor, isPassword = false)
        val offer = session.accept(session.beginSwipe(field)!!, result("tere", "tore"))!!
        val pending = session.beginSwipe(field)!!
        val renewed = session.cursorChanged(field)!!
        assertNull(session.accept(pending, result("late")))
        assertFalse(session.select(field, offer, 1))
        assertNotNull(session.beginSwipe(renewed))
        assertEquals("tere", editor.text)
    }

    @Test fun passwordFieldNeitherOffersNorWritesAndCannotUseOldTicket() {
        val editor = Editor()
        val session = SwipeSuggestionSession()
        val previous = session.startField(Editor(), isPassword = false)
        val pending = session.beginSwipe(previous)!!
        val secret = session.startField(editor, isPassword = true)
        assertNull(session.beginSwipe(secret))
        assertNull(session.accept(pending, result("secret")))
        assertEquals("", editor.text)
    }

    @Test fun finishingFieldInvalidatesBothPendingDecodeAndOffer() {
        val editor = Editor()
        val session = SwipeSuggestionSession()
        val field = session.startField(editor, isPassword = false)
        val offer = session.accept(session.beginSwipe(field)!!, result("tere", "tore"))!!
        val pending = session.beginSwipe(field)!!
        session.endField(field)
        assertNull(session.accept(pending, result("late")))
        assertFalse(session.select(field, offer, 1))
        assertNull(session.beginSwipe(field))
        assertEquals("tere", editor.text)
    }

    @Test fun externalEditAtSameCaretRevokesPendingSwipeWithoutWriting() {
        val editor = Editor("tere ")
        val session = SwipeSuggestionSession()
        val field = session.startField(editor, isPassword = false)
        val pending = session.beginSwipe(field)!!
        editor.text = "uus  " // Same cursor length; no selection callback.
        assertNull(session.accept(pending, result("hilinenud")))
        assertEquals("uus  ", editor.text)
    }

    @Test fun editorMutationWithoutSelectionCallbackRefusesCorrection() {
        val editor = Editor()
        val session = SwipeSuggestionSession()
        val field = session.startField(editor, isPassword = false)
        val offer = session.accept(session.beginSwipe(field)!!, result("tere", "tore"))!!
        editor.text = "other text"
        assertFalse(session.select(field, offer, 1))
        assertEquals("other text", editor.text)
    }

    @Test fun invalidResultAndReorderedDecodeDoNotCommit() {
        val editor = Editor()
        val session = SwipeSuggestionSession()
        val field = session.startField(editor, isPassword = false)
        val old = session.beginSwipe(field)!!
        val latest = session.beginSwipe(field)!!
        assertNull(session.accept(old, result("stale")))
        assertNull(session.accept(latest, result("", "   ")))
        assertEquals("", editor.text)
    }
}
