package tribixbite.cleverkeys

import android.content.SharedPreferences
import android.text.InputType
import android.view.inputmethod.EditorInfo

/** Active IME preference; never use the dormant CleverKeys double-space control. */
internal object ViipioDoubleSpacePreference {
    const val KEY = "viipio_double_space_period"
    const val DEFAULT = true

    fun enabled(prefs: SharedPreferences): Boolean = try {
        prefs.getBoolean(KEY, DEFAULT)
    } catch (_: ClassCastException) {
        DEFAULT
    }

    fun allowed(info: EditorInfo): Boolean {
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
}
