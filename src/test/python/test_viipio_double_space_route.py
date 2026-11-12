"""The active IME must expose and obey double-space period in unified settings."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
SCREEN = (ROOT / 'src/main/kotlin/tribixbite/cleverkeys/ui/settings/SettingsScreen.kt').read_text()
SERVICE = (ROOT / 'src/main/kotlin/tribixbite/cleverkeys/EstonianSwipeService.kt').read_text()


class DoubleSpaceRouteTest(unittest.TestCase):
    def test_visible_preference_controls_the_active_space_path(self):
        self.assertIn('viipio_double_space_period_title', SCREEN)
        self.assertIn('saveSetting(ViipioDoubleSpacePreference.KEY', SCREEN)
        self.assertIn('ViipioDoubleSpacePreference.enabled(', SERVICE)
        self.assertIn('manualPolicy.space(connection,', SERVICE)


if __name__ == '__main__':
    unittest.main()
