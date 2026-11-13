package tribixbite.cleverkeys

import android.content.SharedPreferences

/** Configurable letter-hold delay; the observed Gboard setting on the test device is 300 ms. */
internal object ViipioHoldDurationPreference {
    const val KEY = "viipio_touch_hold_duration_ms"
    const val DEFAULT_MS = 300
    const val MIN_MS = 100
    const val MAX_MS = 700
    const val STEP_MS = 50

    fun read(prefs: SharedPreferences): Int = try {
        prefs.getInt(KEY, DEFAULT_MS).takeIf { it in MIN_MS..MAX_MS } ?: DEFAULT_MS
    } catch (_: ClassCastException) { DEFAULT_MS }
}
