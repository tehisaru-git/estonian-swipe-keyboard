package tribixbite.cleverkeys

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ViipioSpacingPreferenceTest {
    @Test fun swipeToTapSpaceIsOnByDefaultAndUserCanDisableIt() {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("viipio-spacing-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        assertTrue(ViipioSpacingPreference.enabled(prefs))
        prefs.edit().putBoolean(ViipioSpacingPreference.KEY, false).commit()
        assertFalse(ViipioSpacingPreference.enabled(prefs))
    }

    @Test fun aCorruptSettingFallsBackToTheSafeDefault() {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("viipio-spacing-test-corrupt", Context.MODE_PRIVATE)
        prefs.edit().putString(ViipioSpacingPreference.KEY, "not a boolean").commit()
        assertTrue(ViipioSpacingPreference.enabled(prefs))
    }
}
