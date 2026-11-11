package tribixbite.cleverkeys.estonianui

import org.junit.Assert.*
import org.junit.Test

class LongPressOptionsTest {
    private fun key(label: String, language: KeyboardLanguage = KeyboardLanguage.ESTONIAN) =
        KeyboardGeometry(1080, 700, language).keys.first { it.label == label }

    @Test fun picturedGKeyOffersTwoAccentsAndAmpersandByDefault() {
        val options = LongPressOptions.forKey(key("g"), KeyboardLanguage.ESTONIAN, false)!!
        assertEquals(listOf("ğ", "&", "ǵ"), options.values)
        assertEquals("&", options.defaultValue)
    }

    @Test fun picturedMKeyInsertsQuestionMarkOnHold() {
        val options = LongPressOptions.forKey(key("m"), KeyboardLanguage.ESTONIAN, false)!!
        assertEquals(listOf("?"), options.values)
        assertEquals("?", options.defaultValue)
    }

    @Test fun everyVisibleCornerSymbolCanBeChosenByHoldingItsLetter() {
        for (language in KeyboardLanguage.values()) {
            val geometry = KeyboardGeometry(1080, 700, language)
            for (key in geometry.keys.filter { it.kind == KeyKind.LETTER }) {
                val hint = key.secondarySymbol ?: continue
                val options = LongPressOptions.forKey(key, language, false)
                assertNotNull(key.label, options)
                assertTrue(key.label, hint in options!!.values)
            }
        }
    }

    @Test fun estonianSAndZKeepExistingAccentsWhileTheirSymbolsRemainAvailable() {
        assertEquals("š", LongPressOptions.forKey(key("s"), KeyboardLanguage.ESTONIAN, false)!!.defaultValue)
        assertEquals("ž", LongPressOptions.forKey(key("z"), KeyboardLanguage.ESTONIAN, false)!!.defaultValue)
        assertTrue("#" in LongPressOptions.forKey(key("s"), KeyboardLanguage.ESTONIAN, false)!!.values)
        assertTrue("*" in LongPressOptions.forKey(key("z"), KeyboardLanguage.ESTONIAN, false)!!.values)
    }

    @Test fun symbolsPageDoesNotInventLetterLongPressOptions() {
        val symbol = KeyboardGeometry(1080, 700, KeyboardLanguage.ESTONIAN, true).keys.first { it.label == "&" }
        assertNull(LongPressOptions.forKey(symbol, KeyboardLanguage.ESTONIAN, true))
    }
}
