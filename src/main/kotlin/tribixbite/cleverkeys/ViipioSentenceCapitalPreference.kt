package tribixbite.cleverkeys

import android.content.SharedPreferences
import android.text.InputType
import android.text.TextUtils
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/** Active Viipio preference, deliberately separate from dormant CleverKeys autocapitalisation. */
internal object ViipioSentenceCapitalPreference {
    const val KEY = "viipio_capitalize_sentence_starts"
    const val DEFAULT = false

    fun enabled(prefs: SharedPreferences): Boolean = try {
        prefs.getBoolean(KEY, DEFAULT)
    } catch (_: ClassCastException) { DEFAULT }

    fun allowed(info: EditorInfo): Boolean {
        val type = info.inputType
        val variation = type and InputType.TYPE_MASK_VARIATION
        return type and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            variation in setOf(InputType.TYPE_TEXT_VARIATION_NORMAL,
                InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
                InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE)
    }

    /** Some editors report word caps even when sentence caps were requested. Only use a
     * bounded, transient prefix for the opted-in ordinary field; never store or log it. */
    fun shouldCapitalize(prefs: SharedPreferences, info: EditorInfo, connection: InputConnection): Boolean {
        if (!enabled(prefs) || !allowed(info)) return false
        val flag = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        val reported = try { connection.getCursorCapsMode(flag) } catch (_: Exception) { 0 }
        val before = try { connection.getTextBeforeCursor(32, 0)?.toString()?.takeLast(32) }
            catch (_: Exception) { null }
        if (before != null) {
            // An all-whitespace truncated window cannot prove the earlier sentence boundary.
            if (before.length == 32 && before.all(Char::isWhitespace)) return false
            // Android reports CAP_WORDS at a paragraph start even for a sentence-only request;
            // after ordinary spaces it reports zero, and after punctuation CAP_SENTENCES.
            return TextUtils.getCapsMode(before, before.length, flag) and
                (flag or InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0
        }
        return reported and flag != 0
    }
}
