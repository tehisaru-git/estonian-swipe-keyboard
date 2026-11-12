package tribixbite.cleverkeys.swipeui

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import tribixbite.cleverkeys.estonianui.KeyboardLanguage
import tribixbite.cleverkeys.swipe.geometric.CkdtDictionaryReader
import android.content.Context
import tribixbite.cleverkeys.ViipioPersonalWords
import java.util.Locale

/** Read-only, bounded lookup of the bundled tap dictionaries; no learning or autocorrect. */
internal class ManualCompletions private constructor(
    private val indexes: Map<KeyboardLanguage, PrefixIndex>,
    private val personalWords: (KeyboardLanguage) -> List<String>,
) {
    private data class Offer(val connection: InputConnection, val caret: Int, val before: String,
                             val after: String, val word: String, val words: List<String>)
    private var offer: Offer? = null
    var words: List<String> = emptyList()
        private set

    fun clear() { offer = null; words = emptyList() }

    fun update(connection: InputConnection, info: EditorInfo, language: KeyboardLanguage): List<String> {
        clear()
        if (!allowed(info)) return words
        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0) ?: return words
        val caretLong = extracted.startOffset.toLong() + extracted.selectionEnd
        if (extracted.startOffset < 0 || extracted.selectionStart != extracted.selectionEnd ||
            caretLong !in 0..Int.MAX_VALUE.toLong()) return words
        val caret = caretLong.toInt()
        val before = connection.getTextBeforeCursor(256, 0)?.toString() ?: return words
        val after = connection.getTextAfterCursor(64, 0)?.toString() ?: return words
        // Android returns null, not an empty string, for a collapsed selection in many editors.
        if (connection.getSelectedText(0)?.isNotEmpty() == true) return words
        var start = before.length
        while (start > 0 && (before[start - 1].isLetter() || before[start - 1] == '\'' || before[start - 1] == '-')) start--
        if (start == before.length || (start == 0 && caret > before.length)) return words
        // Do not complete an interior caret position: the suffix belongs to the editor, not us.
        if (after.firstOrNull()?.isLetter() == true) return words
        val word = before.substring(start)
        if (word.length > 48 || word.any { !it.isLetter() }) return words
        val prefix = word.lowercase(Locale.ROOT)
        val personal = try { personalWords(language) } catch (_: Exception) { emptyList() }
        val matches = (personal.asSequence().filter { candidate ->
            ViipioPersonalWords.validWord(candidate) && candidate.startsWith(prefix, ignoreCase = true)
        }.take(3).toList() + indexes[language]?.find(prefix).orEmpty())
            .distinctBy { it.lowercase(Locale.ROOT) }.take(3)
        if (matches.isEmpty()) return words
        words = when {
            word.length > 1 && word.all(Char::isUpperCase) ->
                matches.map { it.uppercase(Locale.ROOT) }
            word.first().isUpperCase() ->
                matches.map { it.replaceFirstChar { letter -> letter.titlecase(Locale.ROOT) } }
            else -> matches
        }
        offer = Offer(connection, caret, before, after, word, words)
        return words
    }

    fun choose(connection: InputConnection, index: Int): Boolean {
        val current = offer ?: return false
        val chosen = current.words.getOrNull(index) ?: return false
        clear() // A failed write must never leave an actionable stale candidate.
        if (connection !== current.connection) return false
        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0) ?: return false
        val caret = extracted.startOffset.toLong() + extracted.selectionEnd
        if (extracted.startOffset < 0 || extracted.selectionStart != extracted.selectionEnd || caret != current.caret.toLong() ||
            connection.getSelectedText(0)?.isNotEmpty() == true ||
            connection.getTextBeforeCursor(256, 0)?.toString() != current.before ||
            connection.getTextAfterCursor(64, 0)?.toString() != current.after) return false
        val start = current.caret - current.word.length
        if (start < 0 || !connection.setSelection(start, current.caret)) return false
        if (connection.getSelectedText(0)?.toString() != current.word) {
            connection.setSelection(current.caret, current.caret)
            return false
        }
        if (!connection.commitText(chosen, 1)) {
            connection.setSelection(current.caret, current.caret)
            return false
        }
        return true
    }

    private fun allowed(info: EditorInfo): Boolean {
        if (info.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT ||
            info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0 ||
            info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return false
        return when (info.inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI -> false
            else -> true
        }
    }

    private class PrefixIndex(entries: List<Pair<String, Int>>) {
        private val ranked = entries.asSequence().filter { (word, _) ->
            word.length in 2..48 && word.all(Char::isLetter)
        }.sortedWith(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first })
            .map { it.first }.distinct().toList()
        // Only the short prefixes need a precomputed index; longer queries use a
        // lexicographic range and keep just three ranked entries while scanning it.
        private val top = HashMap<String, MutableList<String>>()
        private val alphabetic = ranked.withIndex().sortedBy { it.value }
        init {
            for (word in ranked) for (length in 1..minOf(3, word.length)) {
                val bucket = top.getOrPut(word.substring(0, length)) { ArrayList(3) }
                if (bucket.size < 3) bucket.add(word)
            }
        }
        fun find(prefix: String): List<String> {
            if (prefix.length <= 3) return top[prefix]?.toList().orEmpty()
            var low = 0
            var high = alphabetic.size
            while (low < high) {
                val middle = (low + high) ushr 1
                if (alphabetic[middle].value < prefix) low = middle + 1 else high = middle
            }
            val best = ArrayList<IndexedValue<String>>(3)
            while (low < alphabetic.size && alphabetic[low].value.startsWith(prefix)) {
                val entry = alphabetic[low++]
                if (best.size < 3 || entry.index < best.last().index) {
                    best.add(entry)
                    best.sortBy { it.index }
                    if (best.size > 3) best.removeAt(3)
                }
            }
            return best.map { it.value }
        }
    }

    companion object {
        fun fromEntries(et: List<Pair<String, Int>>, en: List<Pair<String, Int>>,
                        personalWords: (KeyboardLanguage) -> List<String> = { emptyList() }) =
            ManualCompletions(mapOf(KeyboardLanguage.ESTONIAN to PrefixIndex(et),
                KeyboardLanguage.ENGLISH to PrefixIndex(en)), personalWords)

        fun fromBundledAssets(context: Context): ManualCompletions {
            val personal = ViipioPersonalWords(context)
            return fromEntries(
            context.assets.open("dictionaries/et_enhanced.bin").use { input ->
                CkdtDictionaryReader.readEntries(input).map { it.word to it.rank }
            },
            context.assets.open("dictionaries/en_enhanced.bin").use { input ->
                CkdtDictionaryReader.readEntries(input).map { it.word to it.rank }
            }) { language -> personal.words(if (language == KeyboardLanguage.ESTONIAN) "et" else "en") }
        }
    }
}
