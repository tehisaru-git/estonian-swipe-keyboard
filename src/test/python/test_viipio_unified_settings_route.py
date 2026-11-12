"""Only one reachable Viipio Settings home; privacy stays behind consent."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
NS = {'a': 'http://schemas.android.com/apk/res/android'}
MANIFEST = ET.parse(ROOT / 'AndroidManifest.xml').getroot()
SCREEN = (ROOT / 'src/main/kotlin/tribixbite/cleverkeys/ui/settings/SettingsScreen.kt').read_text()
ACTIVITY = (ROOT / 'src/main/kotlin/tribixbite/cleverkeys/activities/SettingsActivity.kt').read_text()
SERVICE = (ROOT / 'src/main/kotlin/tribixbite/cleverkeys/EstonianSwipeService.kt').read_text()


class UnifiedSettingsRouteTest(unittest.TestCase):
    def test_icon_keyboard_gear_and_system_settings_share_one_home(self):
        launcher = []
        for activity in MANIFEST.findall('.//activity'):
            for intent in activity.findall('intent-filter'):
                cats = [x.get('{%s}name' % NS['a']) for x in intent.findall('category')]
                if 'android.intent.category.LAUNCHER' in cats:
                    launcher.append(activity.get('{%s}name' % NS['a']))
        self.assertEqual(['tribixbite.cleverkeys.SettingsActivity'], launcher)
        method = ET.parse(ROOT / 'res/xml/method.xml').getroot()
        self.assertEqual('tribixbite.cleverkeys.SettingsActivity',
                         method.get('{%s}settingsActivity' % NS['a']))
        self.assertIn('SettingsActivity::class.java', SERVICE)

    def test_home_exposes_setup_and_diagnostics_without_starting_collection(self):
        self.assertIn('LauncherActivity::class.java', SCREEN)
        self.assertIn('TelemetryActivity::class.java', SCREEN)
        self.assertIn('viipio_auto_space_after_swipe_title', SCREEN)
        self.assertNotIn('TelemetryRuntime.enable(', SCREEN)

    def test_home_categories_have_back_navigation_and_no_dormant_settings(self):
        for name in ('viipio_settings_preferences', 'viipio_settings_corrections',
                     'viipio_settings_glide', 'viipio_settings_dictionary',
                     'viipio_settings_privacy'):
            self.assertIn(name, SCREEN)
        self.assertIn('BackHandler(', SCREEN)
        self.assertIn('when (page)', SCREEN)
        self.assertNotIn('AutoCorrectionSection()', SCREEN)

    def test_diagnostics_home_state_is_loaded_not_guessed_from_default(self):
        self.assertIn('TelemetryRuntime.stats(this)', ACTIVITY)
        self.assertIn('diagnosticsEnabled', ACTIVITY)
        self.assertIn('diagnosticsEnabled', SCREEN)
        self.assertIn('viipio_diagnostics_checking', SCREEN)


if __name__ == '__main__':
    unittest.main()
