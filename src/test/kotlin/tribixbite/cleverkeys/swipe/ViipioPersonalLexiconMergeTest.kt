package tribixbite.cleverkeys.swipe

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ViipioPersonalLexiconMergeTest {
    @Test fun oldCustomWordsAndCredentialWordsBothFeedEstonianSwipeWithoutEnglishLeakage() {
        val context: Context = RuntimeEnvironment.getApplication()
        val legacy = context.getSharedPreferences("legacy-viipio-merge", Context.MODE_PRIVATE)
        legacy.edit().clear().putString("custom_words_et", "{\"tervist\":17}").commit()
        val et = UserLexiconInputs.read(legacy, "et", "{\"mhm\":255}")
        val words = JSONObject(et.customJson)
        assertEquals(17, words.getInt("tervist"))
        assertEquals(255, words.getInt("mhm"))
        assertEquals("{}", UserLexiconInputs.read(legacy, "en", "{}").customJson)
        assertFalse(UserLexiconInputs.read(legacy, "et", "{}").customJson.contains("mhm"))
    }

    @Test fun malformedOldInputDoesNotOverwriteItOrHideNewPersonalWords() {
        val context: Context = RuntimeEnvironment.getApplication()
        val legacy = context.getSharedPreferences("legacy-viipio-bad", Context.MODE_PRIVATE)
        legacy.edit().clear().putString("custom_words_et", "invalid").commit()
        assertEquals(255, JSONObject(UserLexiconInputs.read(legacy, "et", "{\"mhm\":255}").customJson)
            .getInt("mhm"))
        assertEquals("invalid", legacy.getString("custom_words_et", null))
    }
}
