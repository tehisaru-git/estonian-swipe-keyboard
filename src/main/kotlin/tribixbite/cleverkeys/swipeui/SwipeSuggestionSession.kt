package tribixbite.cleverkeys.swipeui

import tribixbite.cleverkeys.PredictionResult

/**
 * Editor-independent swipe policy. Confine calls to the IME thread; never logs or learns text.
 *
 * Integrator: startField for every editor/connection (including restart), beginSwipe before
 * launching async decode, accept only with that ticket, and render Offer.words. Pass the same
 * Offer to select on a bar tap. Call cursorChanged for external selection/composition changes
 * and replace the saved FieldToken with its return; call endField on finish. Suppress callbacks
 * for the IME's own composition/space writes, or they would invalidate their own offer.
 * Classify password fields BEFORE invoking the decoder: isPassword blocks this controller's
 * writes but does not prevent a caller from decoding or retaining a secret trace.
 */
class SwipeSuggestionSession {
    /**
     * InputConnection adapter: beforeCursor must include at least the entire composed word.
     * Use getTextBeforeCursor(...), not a cached field string; null means unreadable editor.
     * Map setComposingText(value, 1), commitText(value, 1), finishComposingText() to Boolean.
     */
    interface Sink {
        fun beforeCursor(): String?
        fun setComposingText(value: String): Boolean
        fun finishComposingText(): Boolean
        fun commitText(value: String): Boolean
    }

    data class FieldToken internal constructor(val id: Long)
    data class SwipeToken internal constructor(val field: FieldToken, val id: Long,
        val beforeCursor: String)
    data class Offer internal constructor(val field: FieldToken, val id: Long, val words: List<String>)

    private var serial = 0L
    private var field: FieldToken? = null
    private var sink: Sink? = null
    private var password = false
    private var request: SwipeToken? = null
    private var offer: Offer? = null
    private var composing: String? = null

    fun startField(editor: Sink, isPassword: Boolean): FieldToken {
        field = FieldToken(++serial)
        sink = editor
        password = isPassword
        request = null
        offer = null
        composing = null
        return field!!
    }

    /** Rotate ownership on an external cursor/selection change; old callbacks and offers expire. */
    fun cursorChanged(owner: FieldToken): FieldToken? {
        if (field != owner) return null
        field = FieldToken(++serial)
        request = null
        offer = null
        composing = null // Do not touch the editor: Android owns the moved selection.
        return field
    }

    /** Invalidate without writing to a potentially detached InputConnection. */
    fun endField(owner: FieldToken) {
        if (field == owner) {
            field = null
            sink = null
            request = null
            offer = null
            composing = null
        }
    }

    fun beginSwipe(owner: FieldToken): SwipeToken? {
        if (field != owner || password) return null
        offer = null
        val snapshot = sink?.beforeCursor() ?: return null
        return SwipeToken(owner, ++serial, snapshot).also { request = it }
    }

    fun accept(ticket: SwipeToken, result: PredictionResult): Offer? {
        if (request != ticket || field != ticket.field || password) return null
        request = null
        val words = result.words.asSequence().filter { it.isNotBlank() &&
            it == it.trim() && it.none(Char::isWhitespace) }.distinct().take(3).toList()
        if (words.isEmpty()) return null
        val editor = sink ?: return null
        var before = editor.beforeCursor() ?: return null
        if (before != ticket.beforeCursor) {
            offer = null
            composing = null
            return null // An editor may rewrite text without a selection callback.
        }
        val previous = composing
        if (previous != null) {
            if (!before.endsWith(previous)) { offer = null; composing = null; return null }
            if (!editor.finishComposingText()) return null
            composing = null
            before = editor.beforeCursor() ?: return null
            if (!before.endsWith(previous)) return null
        }
        // Insert a separator before the new composing word, never after punctuation itself.
        if (before.isNotEmpty() && !before.last().isWhitespace() &&
            before.last() !in "([{“‘") {
            if (!editor.commitText(" ")) return null
        }
        if (!editor.setComposingText(words.first())) return null
        composing = words.first()
        return Offer(ticket.field, ++serial, words).also { offer = it }
    }

    fun select(owner: FieldToken, chosen: Offer, index: Int): Boolean {
        if (field != owner || offer !== chosen || chosen.field != owner || password) return false
        val word = chosen.words.getOrNull(index) ?: return false
        val editor = sink ?: return false
        if (editor.beforeCursor()?.endsWith(composing ?: return false) != true) {
            offer = null
            composing = null
            return false
        }
        if (!editor.setComposingText(word)) return false
        composing = word
        return true
    }
}
