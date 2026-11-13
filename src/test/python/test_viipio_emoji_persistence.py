"""Emoji insertion must keep the active inline picker open for repeated picks."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
SERVICE = (ROOT / 'src/main/kotlin/tribixbite/cleverkeys/EstonianSwipeService.kt').read_text()

class EmojiPickerPersistenceTest(unittest.TestCase):
    def test_verified_pick_keeps_picker_open_until_explicit_close(self):
        callback = SERVICE.split('panel.onEmojiPicked = { emoji ->', 1)[1].split(
            'resizeForEmoji(true, false)', 1)[0]
        self.assertIn('connection.commitText(emoji, 1)', callback)
        self.assertIn('panel.visibility == View.VISIBLE', callback)
        self.assertNotIn('closeEmojiPanel()', callback)
        self.assertIn('panel.onClose = ::closeEmojiPanel', SERVICE)

if __name__ == '__main__':
    unittest.main()
