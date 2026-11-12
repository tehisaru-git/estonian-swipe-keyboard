package tribixbite.cleverkeys.estonianui

/** Explicit alternatives for the drawn Estonian/English layout; no Gboard assets or tables. */
data class LongPressOptions(val values: List<String>, val defaultIndex: Int) {
    init { require(values.isNotEmpty() && defaultIndex in values.indices) }
    val defaultValue: String get() = values[defaultIndex]

    companion object {
        fun forKey(key: SurfaceKey, language: KeyboardLanguage, symbolMode: Boolean,
                   includeSymbols: Boolean = true): LongPressOptions? {
            if (symbolMode || key.kind != KeyKind.LETTER) return null
            val hint = key.secondarySymbol
            return when {
                key.label == "g" -> if (includeSymbols && hint != null)
                    LongPressOptions(listOf("ğ", hint, "ǵ"), 1)
                    else LongPressOptions(listOf("ğ", "ǵ"), 0)
                language == KeyboardLanguage.ESTONIAN && key.label == "s" ->
                    LongPressOptions(listOfNotNull("š", hint?.takeIf { includeSymbols }), 0)
                language == KeyboardLanguage.ESTONIAN && key.label == "z" ->
                    LongPressOptions(listOfNotNull("ž", hint?.takeIf { includeSymbols }), 0)
                includeSymbols && hint != null -> LongPressOptions(listOf(hint), 0)
                else -> null
            }
        }
    }
}
