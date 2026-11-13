package tribixbite.cleverkeys

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ViipioHoldDurationPreferenceTest {
    @Test fun defaultMatchesObservedGboardAndSavedValueIsBounded() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("viipio-hold-delay-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        assertEquals(300, ViipioHoldDurationPreference.read(prefs))
        prefs.edit().putInt(ViipioHoldDurationPreference.KEY, 500).commit()
        assertEquals(500, ViipioHoldDurationPreference.read(prefs))
        prefs.edit().putInt(ViipioHoldDurationPreference.KEY, -1).commit()
        assertEquals(300, ViipioHoldDurationPreference.read(prefs))
        prefs.edit().putInt(ViipioHoldDurationPreference.KEY, 99_999).commit()
        assertEquals(300, ViipioHoldDurationPreference.read(prefs))
        prefs.edit().putString(ViipioHoldDurationPreference.KEY, "bad").commit()
        assertEquals(300, ViipioHoldDurationPreference.read(prefs))
    }
}
