package tribixbite.cleverkeys.swipe

import android.content.SharedPreferences
import org.json.JSONObject
import tribixbite.cleverkeys.LanguagePreferenceKeys

/**
 * The personal-dictionary preference values one language's merged swipe lexicon is derived
 * from, read raw so [LexiconContentVersion] hashes see
 * exactly what the build consumes. Both engines read them through [read] (audit L7, 2026-10-20:
 * `CtcEngineAdapter.lexiconFor` and `GeometricEngineAdapter.dictionaryFor` each restated the
 * three reads).
 *
 * [customJson] carries each word's stored FREQUENCY as well as its membership, so an Edit that
 * only changes a frequency changes the version and the very next decode rebuilds (pinned with the
 * Dictionary Manager's own writer by `CustomWordEditLexiconInputTest`, 2026-10-10 device report).
 * [priorityJson] is user swipe priority (2026-10-08): raising or lowering a word rebuilds exactly
 * like adding one.
 *
 * @property customJson `custom_words_<lang>` JSON (`{}` when absent).
 * @property disabled `disabled_words_<lang>`.
 * @property priorityJson `swipe_priority_<lang>` JSON (empty when absent).
 */
internal data class UserLexiconInputs(
    val customJson: String,
    val disabled: Set<String>,
    val priorityJson: String,
) {
    companion object {
        /**
         * Read the inputs for [lang] from [prefs]. The caller passes the language in the form it
         * keys its lexicon by; [LanguagePreferenceKeys] lower-cases it.
         */
        fun read(prefs: SharedPreferences, lang: String, personalJson: String = "{}"): UserLexiconInputs = UserLexiconInputs(
            customJson = mergeCustomWords(prefs.getString(LanguagePreferenceKeys.customWordsKey(lang), "{}") ?: "{}",
                personalJson),
            disabled = prefs.getStringSet(LanguagePreferenceKeys.disabledWordsKey(lang), emptySet())
                ?: emptySet(),
            priorityJson = prefs.getString(LanguagePreferenceKeys.swipePriorityKey(lang), "") ?: "",
        )

        private fun mergeCustomWords(legacy: String, personal: String): String {
            if (personal == "{}") return legacy
            return try {
                val extra = JSONObject(personal)
                val merged = try { JSONObject(legacy) } catch (_: Exception) { JSONObject() }
                extra.keys().forEach { word -> merged.put(word, extra.getInt(word)) }
                merged.toString()
            } catch (_: Exception) { legacy } // Preserve old input, never write back a failed merge.
        }
    }
}
