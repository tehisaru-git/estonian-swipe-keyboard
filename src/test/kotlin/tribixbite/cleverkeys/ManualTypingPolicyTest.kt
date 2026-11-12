package tribixbite.cleverkeys

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ManualTypingPolicyTest {
    private fun editor(text: String): Pair<EditText, InputConnection> {
        val field = EditText(RuntimeEnvironment.getApplication())
        field.setText(text)
        field.setSelection(field.length())
        return field to field.onCreateInputConnection(EditorInfo())
    }

    @Test fun firstManualLetterAfterSwipeCommitsSeparatorAndLetterAtomically() {
        val (field, connection) = editor("tere")
        val policy = ManualTypingPolicy()
        assertTrue(policy.commitLetterAfterSwipe(connection, "tere", "k"))
        assertEquals("tere k", field.text.toString())
        assertTrue(policy.commitLetterAfterSwipe(connection, null, "a"))
        assertEquals("tere ka", field.text.toString())
    }

    @Test fun disabledAutoSpaceKeepsNextManualLetterAdjacent() {
        val (field, connection) = editor("tere")
        val policy = ManualTypingPolicy()
        assertTrue(policy.commitLetterAfterSwipe(connection, "tere", "k", autoSpace = false))
        assertEquals("terek", field.text.toString())
    }

    @Test fun changedCaretOrMissingSuffixNeverInsertsUnexpectedSpace() {
        val (field, connection) = editor("tere")
        val policy = ManualTypingPolicy()
        field.setSelection(2)
        assertTrue(policy.commitLetterAfterSwipe(connection, "tere", "x"))
        assertEquals("texre", field.text.toString())
        assertTrue(policy.commitLetterAfterSwipe(connection, "vale", "y"))
        assertEquals("texyre", field.text.toString())
    }

    @Test fun rejectedCombinedCommitDoesNotDeleteOriginalWord() {
        val (field, base) = editor("tere")
        val connection = object : InputConnectionWrapper(base, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean =
                if (text?.toString() == " k") false else super.commitText(text, newCursorPosition)
        }
        assertTrue(ManualTypingPolicy().commitLetterAfterSwipe(connection, "tere", "k"))
        assertEquals("terek", field.text.toString()) // Preserve the typed key without corrupting the word.
    }

    @Test fun doubleSpaceWithinWindowChangesWordSpaceToPeriodSpace() {
        var now = 1000L
        val policy = ManualTypingPolicy { now }
        val (field, connection) = editor("tere")
        assertTrue(policy.space(connection))
        now = 1300L
        assertTrue(policy.space(connection))
        assertEquals("tere. ", field.text.toString())
    }

    @Test fun slowOrPunctuatedDoubleSpaceLeavesLiteralSpaces() {
        var now = 1000L
        val policy = ManualTypingPolicy { now }
        val (field, connection) = editor("tere")
        assertTrue(policy.space(connection))
        now = 2400L
        assertTrue(policy.space(connection))
        assertEquals("tere  ", field.text.toString())
        policy.reset()
        val (punct, second) = editor("tere!")
        now = 3000L
        assertTrue(policy.space(second))
        now = 3100L
        assertTrue(policy.space(second))
        assertEquals("tere!  ", punct.text.toString())
    }

    @Test fun failedReplacementKeepsFirstSpaceAndCanFallbackToSecondSpace() {
        var now = 1000L
        val policy = ManualTypingPolicy { now }
        val (field, base) = editor("tere")
        val connection = object : InputConnectionWrapper(base, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean =
                if (text?.toString() == ". ") false else super.commitText(text, newCursorPosition)
        }
        assertTrue(policy.space(connection))
        now = 1200L
        assertTrue(policy.space(connection))
        assertEquals("tere  ", field.text.toString())
    }
}
