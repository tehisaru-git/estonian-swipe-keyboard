package tribixbite.cleverkeys

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ViipioSentenceCapitalPreferenceTest {
    @Test fun inconsistentEditorCapsStillOnlyCapitalizesSentenceStartsWhenOptedIn() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("viipio-cap-context-test", Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean(ViipioSentenceCapitalPreference.KEY, true).commit()
        val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        fun shouldCap(before: String, reported: Int): Boolean {
            val base = BaseInputConnection(View(context), true)
            val connection = object : InputConnectionWrapper(base, false) {
                override fun getCursorCapsMode(reqModes: Int): Int = reported
                override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
                    assertTrue("no unbounded surrounding editor read", n <= 32)
                    return before.takeLast(n)
                }
            }
            return ViipioSentenceCapitalPreference.shouldCapitalize(prefs, info, connection)
        }
        assertTrue(shouldCap("", InputType.TYPE_TEXT_FLAG_CAP_WORDS))
        assertFalse(shouldCap("hello ", InputType.TYPE_TEXT_FLAG_CAP_WORDS))
        assertTrue(shouldCap("hello. ", 0))
        assertFalse(shouldCap("hello. a", 0))
        prefs.edit().putBoolean(ViipioSentenceCapitalPreference.KEY, false).commit()
        assertFalse(shouldCap("", InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
    }
    @Test fun sentenceCapitalizationIsOptInAndMalformedSettingFailsClosed() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("viipio-cap-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        assertFalse(ViipioSentenceCapitalPreference.enabled(prefs))
        prefs.edit().putBoolean(ViipioSentenceCapitalPreference.KEY, true).commit()
        assertTrue(ViipioSentenceCapitalPreference.enabled(prefs))
        prefs.edit().putString(ViipioSentenceCapitalPreference.KEY, "bad").commit()
        assertFalse(ViipioSentenceCapitalPreference.enabled(prefs))
    }

    @Test fun onlyOrdinarySentenceCapitalizedTextFieldsAreEligible() {
        fun info(type: Int) = EditorInfo().apply { inputType = type }
        assertTrue(ViipioSentenceCapitalPreference.allowed(info(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)))
        assertTrue("a keyboard-level opt-in also applies without an editor hint",
            ViipioSentenceCapitalPreference.allowed(info(InputType.TYPE_CLASS_TEXT)))
        for (type in listOf(
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_VARIATION_URI,
        )) assertFalse(ViipioSentenceCapitalPreference.allowed(info(type)))
    }
}
