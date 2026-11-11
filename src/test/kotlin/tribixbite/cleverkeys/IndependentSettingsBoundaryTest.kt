package tribixbite.cleverkeys

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Old CleverKeys preference toggles do not control the standalone IME. */
class IndependentSettingsBoundaryTest {
    @Test fun settingsShowOnlyControlsThatAffectTheIndependentKeyboard() {
        val source = File("src/main/kotlin/tribixbite/cleverkeys/ui/settings/SettingsScreen.kt")
            .readText()
        assertTrue(source.contains("TestKeyboardSection()"))
        assertTrue(source.contains("importLanguagePack()"))
        for (obsolete in listOf("SwipeTypingSection()", "AppearanceSection()", "AutoCorrectionSection()",
            "GestureTuningSection()", "MultiLanguageSection()", "GifPanelSection()",
            "VoiceInputSection()", "ActivitiesSection()", "getFilteredSettings(settingsSearchQuery)")) {
            assertFalse("Settings must not expose inactive legacy control $obsolete", source.contains(obsolete))
        }
    }
}
