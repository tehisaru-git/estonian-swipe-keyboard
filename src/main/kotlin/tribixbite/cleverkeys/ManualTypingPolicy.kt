package tribixbite.cleverkeys

import android.os.SystemClock
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/** Explicit, editor-verified spacing policy. Never deletes text before a replacement commit. */
internal class ManualTypingPolicy(private val now: () -> Long = SystemClock::uptimeMillis) {
    private var previousSpaceAt = -1L
    private var previousSpaceCaret: Int? = null
    private var previousSpaceConnection: InputConnection? = null

    fun reset() {
        previousSpaceAt = -1L
        previousSpaceCaret = null
        previousSpaceConnection = null
    }

    private fun caret(connection: InputConnection): Int? {
        val e = connection.getExtractedText(ExtractedTextRequest(), 0) ?: return null
        if (e.startOffset < 0 || e.selectionStart < 0 || e.selectionStart != e.selectionEnd) return null
        val absolute = e.startOffset.toLong() + e.selectionEnd
        return absolute.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
    }

    /** A swipe-committed suffix and collapsed caret must still match before adding a space. */
    fun commitLetterAfterSwipe(connection: InputConnection, swipeWord: String?, letter: String,
                               autoSpace: Boolean = true): Boolean {
        reset()
        if (!autoSpace) return connection.commitText(letter, 1)
        val matches = !swipeWord.isNullOrEmpty() && letter.isNotEmpty() &&
            caret(connection) != null &&
            connection.getTextBeforeCursor(swipeWord.length, 0)?.toString() == swipeWord
        if (matches && connection.commitText(" $letter", 1)) return true
        return connection.commitText(letter, 1) // If a combined commit failed, keep the typed key.
    }

    /** Replace the first IME-inserted space with ". " on a quick second press. */
    fun space(connection: InputConnection): Boolean {
        val end = caret(connection)
        val before = connection.getTextBeforeCursor(64, 0)?.toString()
        val second = previousSpaceConnection === connection && end != null &&
            end == previousSpaceCaret && previousSpaceAt >= 0 &&
            now() - previousSpaceAt in 0..700 && before != null &&
            before.length >= 2 && before.last() == ' ' && before[before.length - 2].isLetterOrDigit()
        if (second) {
            reset()
            if (connection.setSelection(end - 1, end)) {
                if (connection.getSelectedText(0)?.toString() == " " && connection.commitText(". ", 1)) return true
                connection.setSelection(end, end)
            }
            return connection.commitText(" ", 1)
        }
        reset()
        if (!connection.commitText(" ", 1)) return false
        // Use the editor's own absolute cursor position, not a guessed text length.
        if (before?.lastOrNull()?.isLetterOrDigit() == true && end != null) {
            previousSpaceConnection = connection
            previousSpaceCaret = end + 1
            previousSpaceAt = now()
        }
        return true
    }
}
