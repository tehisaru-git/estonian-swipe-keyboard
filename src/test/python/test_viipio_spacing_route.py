"""The active Viipio IME, not a dormant legacy setting, owns swipe-to-tap spacing."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
SERVICE = (ROOT / 'src/main/kotlin/tribixbite/cleverkeys/EstonianSwipeService.kt').read_text()
SCREEN = (ROOT / 'src/main/kotlin/tribixbite/cleverkeys/ui/settings/SettingsScreen.kt').read_text()
ACTIVITY = (ROOT / 'src/main/kotlin/tribixbite/cleverkeys/activities/SettingsActivity.kt').read_text()
VALUES = {node.get('name'): node.text for node in ET.parse(ROOT / 'res/values/strings.xml').getroot().findall('string')}


class SpacingRouteTest(unittest.TestCase):
    def test_visible_toggle_controls_active_tap_commit(self):
        self.assertIn('SettingsScreen()', ACTIVITY)
        self.assertIn('SettingsSwitch(', SCREEN)
        self.assertIn('viipio_auto_space_after_swipe_title', SCREEN)
        self.assertIn('saveSetting(ViipioSpacingPreference.KEY', SCREEN)
        self.assertIn('ViipioSpacingPreference.enabled(', SERVICE)
        self.assertIn('viipio_auto_space_after_swipe_title', VALUES)
        self.assertIn('viipio_auto_space_after_swipe_desc', VALUES)


if __name__ == '__main__':
    unittest.main()
