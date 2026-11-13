"""Only the active IME and canonical settings home may control automatic capitals."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
MAIN = ROOT / 'src/main/kotlin/tribixbite/cleverkeys'

class SentenceCapitalizationRouteTest(unittest.TestCase):
    def test_opt_in_toggle_is_in_live_settings_and_ime(self):
        preference_path = MAIN / 'ViipioSentenceCapitalPreference.kt'
        self.assertTrue(preference_path.is_file(), 'sentence-capital preference missing')
        pref = preference_path.read_text()
        settings = (MAIN / 'ui/settings/SettingsScreen.kt').read_text()
        service = (MAIN / 'EstonianSwipeService.kt').read_text()
        self.assertIn('const val DEFAULT = false', pref)
        self.assertIn('ViipioSentenceCapitalPreference.KEY', settings)
        self.assertIn('R.string.viipio_sentence_capital_title', settings)
        self.assertIn('ViipioSentenceCapitalPreference.shouldCapitalize', service)
        self.assertIn('getCursorCapsMode(flag)', pref)
        self.assertIn('getTextBeforeCursor(32, 0)', pref)
        self.assertNotIn('ViipioCapsProbe', service)

if __name__ == '__main__': unittest.main()
