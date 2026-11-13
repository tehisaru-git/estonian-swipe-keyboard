"""The observed 300 ms hold delay must be adjustable in the active Viipio UI."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
MAIN = ROOT / 'src/main/kotlin/tribixbite/cleverkeys'

class HoldDurationRouteTest(unittest.TestCase):
    def test_slider_is_in_active_preferences_and_ime(self):
        settings = (MAIN / 'ui/settings/SettingsScreen.kt').read_text()
        service = (MAIN / 'EstonianSwipeService.kt').read_text()
        view = (MAIN / 'estonianui/KeyboardSurfaceView.kt').read_text()
        pref = (MAIN / 'ViipioHoldDurationPreference.kt').read_text()
        self.assertTrue('ViipioHoldDurationPreference.KEY' in settings)
        self.assertIn('R.string.viipio_hold_duration_title', settings)
        self.assertTrue('ViipioHoldDurationPreference.read(' in service)
        self.assertIn('holdDurationMs', view)
        self.assertIn('DEFAULT_MS = 300', pref)

if __name__ == '__main__': unittest.main()
