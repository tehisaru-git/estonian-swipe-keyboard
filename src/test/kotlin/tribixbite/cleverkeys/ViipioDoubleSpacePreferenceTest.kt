package tribixbite.cleverkeys

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ViipioDoubleSpacePreferenceTest {
    @Test fun conversionIsOnByDefaultAndCanBeTurnedOff() {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("viipio-double-space-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        assertTrue(ViipioDoubleSpacePreference.enabled(prefs))
        prefs.edit().putBoolean(ViipioDoubleSpacePreference.KEY, false).commit()
        assertFalse(ViipioDoubleSpacePreference.enabled(prefs))
    }

    @Test fun corruptPreferenceReturnsToItsKnownDefault() {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("viipio-double-space-bad", Context.MODE_PRIVATE)
        prefs.edit().putString(ViipioDoubleSpacePreference.KEY, "wrong type").commit()
        assertTrue(ViipioDoubleSpacePreference.enabled(prefs))
    }

    @Test fun periodShortcutIsOnlyEligibleInOrdinarySuggestibleTextFields() {
        fun info(type: Int, options: Int = 0) = EditorInfo().apply {
            inputType = type; imeOptions = options
        }
        assertTrue(ViipioDoubleSpacePreference.allowed(info(InputType.TYPE_CLASS_TEXT)))
        for (type in listOf(InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)) {
            assertFalse("field type=$type", ViipioDoubleSpacePreference.allowed(info(type)))
        }
        assertFalse(ViipioDoubleSpacePreference.allowed(info(InputType.TYPE_CLASS_TEXT,
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)))
    }
}
