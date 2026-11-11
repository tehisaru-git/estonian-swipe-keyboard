package tribixbite.cleverkeys.estonianui

/** Explicit alternatives for the drawn Estonian/English layout; no Gboard assets or tables. */
data class LongPressOptions(val values: List<String>, val defaultIndex: Int) {
    init { require(values.isNotEmpty() && defaultIndex in values.indices) }
    val defaultValue: String get() = values[defaultIndex]

    companion object {
        fun forKey(key: SurfaceKey, language: KeyboardLanguage, symbolMode: Boolean): LongPressOptions? {
            if (symbolMode || key.kind != KeyKind.LETTER) return null
            val hint = key.secondarySymbol ?: return null
            return when {
                key.label == "g" -> LongPressOptions(listOf("ğ", hint, "ǵ"), 1)
                language == KeyboardLanguage.ESTONIAN && key.label == "s" ->
                    LongPressOptions(listOf("š", hint), 0)
                language == KeyboardLanguage.ESTONIAN && key.label == "z" ->
                    LongPressOptions(listOf("ž", hint), 0)
                else -> LongPressOptions(listOf(hint), 0)
            }
        }
    }
}
