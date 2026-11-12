package tribixbite.cleverkeys

import android.content.SharedPreferences

/** Only options used by the independent Viipio IME; inherited Config switches are not read. */
internal object ViipioBehaviorPreference {
    const val TYPED_SUGGESTIONS = "viipio_typed_suggestions"
    const val BACKSPACE_WORD_UNDO = "viipio_backspace_word_undo"
    const val GLIDE_DELETE = "viipio_glide_delete"
    const val CORNER_SYMBOLS = "viipio_corner_symbols"
    private val keys = setOf(TYPED_SUGGESTIONS, BACKSPACE_WORD_UNDO, GLIDE_DELETE, CORNER_SYMBOLS)

    fun enabled(prefs: SharedPreferences, key: String): Boolean {
        if (key !in keys) return false
        return try { prefs.getBoolean(key, true) } catch (_: ClassCastException) { true }
    }
}
