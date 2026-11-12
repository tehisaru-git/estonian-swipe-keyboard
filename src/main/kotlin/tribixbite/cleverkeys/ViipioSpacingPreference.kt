package tribixbite.cleverkeys

import android.content.SharedPreferences

/** This setting belongs to the active Viipio service, not inherited CleverKeys prediction. */
internal object ViipioSpacingPreference {
    const val KEY = "viipio_auto_space_after_swipe"
    const val DEFAULT = true

    fun enabled(prefs: SharedPreferences): Boolean = try {
        prefs.getBoolean(KEY, DEFAULT)
    } catch (_: ClassCastException) {
        DEFAULT
    }
}
