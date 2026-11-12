"""Personal words are local, language-scoped and reach both active suggestion paths."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
SCREEN = (ROOT / 'src/main/kotlin/tribixbite/cleverkeys/ui/settings/SettingsScreen.kt').read_text()
MANUAL = (ROOT / 'src/main/kotlin/tribixbite/cleverkeys/swipeui/ManualCompletions.kt').read_text()
CTC = (ROOT / 'src/main/kotlin/tribixbite/cleverkeys/swipe/CtcEngineAdapter.kt').read_text()
GEO = (ROOT / 'src/main/kotlin/tribixbite/cleverkeys/swipe/GeometricEngineAdapter.kt').read_text()


class PersonalWordsRouteTest(unittest.TestCase):
    def test_visible_add_delete_and_language_scope_are_used_by_active_ime(self):
        self.assertIn('viipio_personal_words_title', SCREEN)
        self.assertIn('ViipioPersonalWords(', SCREEN)
        self.assertIn('FilterChip(', SCREEN, 'active language needs an affirmative selected state')
        self.assertNotIn('enabled = language !=', SCREEN, 'disabled buttons look like the wrong language is active')
        self.assertIn('ViipioPersonalWords(', MANUAL)
        self.assertTrue('ViipioPersonalWords(context).json(lang)' in CTC)
        self.assertTrue('ViipioPersonalWords(context).json(lang)' in GEO)


if __name__ == '__main__':
    unittest.main()
