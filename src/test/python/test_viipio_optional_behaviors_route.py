"""Potentially unwanted gestures must have live, discoverable Viipio settings."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
SCREEN = (ROOT / 'src/main/kotlin/tribixbite/cleverkeys/ui/settings/SettingsScreen.kt').read_text()
SERVICE = (ROOT / 'src/main/kotlin/tribixbite/cleverkeys/EstonianSwipeService.kt').read_text()
SURFACE = (ROOT / 'src/main/kotlin/tribixbite/cleverkeys/estonianui/KeyboardSurfaceView.kt').read_text()


class OptionalBehaviorRouteTest(unittest.TestCase):
    def test_live_optional_actions_have_visible_switches(self):
        for name in ('viipio_typed_suggestions_title', 'viipio_backspace_word_undo_title',
                     'viipio_glide_delete_title', 'viipio_corner_symbols_title'):
            self.assertIn(name, SCREEN)
        for name in ('ViipioBehaviorPreference.TYPED_SUGGESTIONS',
                     'ViipioBehaviorPreference.BACKSPACE_WORD_UNDO',
                     'ViipioBehaviorPreference.GLIDE_DELETE'):
            self.assertIn(name, SERVICE)
        self.assertIn('ViipioBehaviorPreference.CORNER_SYMBOLS', SERVICE)
        self.assertIn('showCornerSymbols', SURFACE)


if __name__ == '__main__':
    unittest.main()
