package tribixbite.cleverkeys

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ViipioPersonalWordsTest {
    private fun store(name: String): Pair<android.content.SharedPreferences, ViipioPersonalWords> {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        return prefs to ViipioPersonalWords(prefs)
    }

    @Test fun mhmIsOnlyInTheExplicitEstonianAppLocalLexiconUntilDeleted() {
        val (prefs, words) = store("viipio-own-mhm")
        assertEquals(ViipioPersonalWords.AddResult.ADDED, words.add("et", "mhm"))
        assertEquals(listOf("mhm"), words.words("et"))
        assertTrue(words.words("en").isEmpty())
        assertEquals(255, JSONObject(prefs.getString("custom_words_et", "{}")!!).getInt("mhm"))
        assertFalse(prefs.contains("custom_words_en"))
        assertEquals(ViipioPersonalWords.AddResult.EXISTS, words.add("et", "MHM"))
        assertTrue(words.remove("et", "mhm"))
        assertTrue(words.words("et").isEmpty())
    }

    @Test fun malformedOrNonLatinInputCannotPolluteEitherLanguage() {
        val (prefs, words) = store("viipio-own-validation")
        for (value in listOf("", "a", " mhm", "lo l", "mhm1", "🙂hi", "тест", "a".repeat(49))) {
            assertEquals(value, ViipioPersonalWords.AddResult.INVALID, words.add("et", value))
        }
        assertEquals(ViipioPersonalWords.AddResult.INVALID, words.add("fr", "mhm"))
        assertFalse(prefs.contains("custom_words_et"))
    }

    @Test fun malformedExistingDataIsNeverSilentlyOverwritten() {
        val (prefs, words) = store("viipio-own-malformed")
        prefs.edit().putString("custom_words_et", "not json").commit()
        assertEquals(ViipioPersonalWords.AddResult.UNAVAILABLE, words.add("et", "mhm"))
        assertEquals("not json", prefs.getString("custom_words_et", null))
        assertFalse(words.remove("et", "mhm"))
    }

    @Test fun existingPersonalEntriesStayIntactAndStoreHasABound() {
        val (prefs, words) = store("viipio-own-bound")
        prefs.edit().putString("custom_words_et", "{\"Tere\":12}").commit()
        assertEquals(ViipioPersonalWords.AddResult.ADDED, words.add("et", "mhm"))
        val stored = JSONObject(prefs.getString("custom_words_et", "{}")!!)
        assertEquals(12, stored.getInt("Tere"))
        assertEquals(255, stored.getInt("mhm"))
        val full = JSONObject()
        for (index in 0 until 200) full.put("entry${'a' + index / 26}${'a' + index % 26}", 50)
        prefs.edit().putString("custom_words_et", full.toString()).commit()
        assertEquals(ViipioPersonalWords.AddResult.FULL, words.add("et", "lisasona"))
    }

    @Test fun userAddedWordsLiveInCredentialProtectedStorageAndAreUnavailableBeforeUnlock() {
        val context: Context = RuntimeEnvironment.getApplication()
        val file = "viipio-ce-isolation"
        val credential = context.getSharedPreferences(file, Context.MODE_PRIVATE)
        credential.edit().clear().commit()
        val words = ViipioPersonalWords(context, fileName = file, isUnlocked = { true })
        assertEquals(ViipioPersonalWords.AddResult.ADDED, words.add("et", "mhm"))
        assertEquals(255, JSONObject(credential.getString("custom_words_et", "{}")!!).getInt("mhm"))
        val locked = ViipioPersonalWords(context, fileName = file, isUnlocked = { false })
        assertTrue(locked.words("et").isEmpty())
        assertEquals(ViipioPersonalWords.AddResult.UNAVAILABLE, locked.add("et", "tere"))
        assertFalse(locked.remove("et", "mhm"))
        assertEquals(listOf("mhm"), words.words("et"))
    }

    @Test fun storageWriteExceptionsLeaveExistingWordsIntactAndReturnUnavailable() {
        val (base, _) = store("viipio-write-failure")
        base.edit().putString("custom_words_et", "{\"tere\":12}").commit()
        for (stage in listOf("edit", "putString", "commit")) {
            val failing = object : SharedPreferences by base {
                override fun edit(): SharedPreferences.Editor {
                    if (stage == "edit") throw IllegalStateException("no editor")
                    return object : SharedPreferences.Editor by base.edit() {
                        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                            if (stage == "putString") throw IllegalStateException("no write")
                            return this
                        }
                        override fun commit(): Boolean {
                            if (stage == "commit") throw IllegalStateException("no commit")
                            return false
                        }
                    }
                }
            }
            val words = ViipioPersonalWords(failing)
            assertEquals(stage, ViipioPersonalWords.AddResult.UNAVAILABLE, words.add("et", "mhm"))
            assertFalse(stage, words.remove("et", "tere"))
            assertEquals(stage, "{\"tere\":12}", base.getString("custom_words_et", null))
        }
    }

    @Test fun mixedInvalidStoredValuesFailClosedInsteadOfHidingGoodSwipeWords() {
        val (prefs, words) = store("viipio-mixed-invalid")
        prefs.edit().putString("custom_words_et", "{\"tere\":12,\"mhm\":\"bad\"}").commit()
        assertTrue(words.words("et").isEmpty())
        assertEquals("{}", words.json("et"))
        assertEquals(ViipioPersonalWords.AddResult.UNAVAILABLE, words.add("et", "tore"))
        assertEquals("{\"tere\":12,\"mhm\":\"bad\"}", prefs.getString("custom_words_et", null))
    }
}
