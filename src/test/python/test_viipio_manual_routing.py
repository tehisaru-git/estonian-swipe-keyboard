"""Source-level guards for candidate UI transitions in the active IME."""
from pathlib import Path
import unittest

SOURCE = (Path(__file__).resolve().parents[3] /
          'src/main/kotlin/tribixbite/cleverkeys/EstonianSwipeService.kt').read_text()


class ManualCandidateRoutingTest(unittest.TestCase):
    def test_swipe_start_revokes_manual_candidate_strip(self):
        decode_start = SOURCE.split('private fun decode(gesture:', 1)[1].split('val accept:', 1)[0]
        self.assertTrue('manualCompletions?.clear()' in decode_start,
                        'beginning a swipe must hide the manual offer during decoding')
        self.assertLess(decode_start.index('manualCompletions?.clear()'),
                        decode_start.index('suggestions.begin()'))

    def test_failed_manual_choice_repaints_revoked_candidates(self):
        click_handler = SOURCE.split('val candidateArrow =', 1)[1].split('row.addView(toolbar', 1)[0]
        self.assertTrue('manualAttempted' in click_handler and
                        'if (manualAttempted && !selected)' in click_handler and
                        'renderSuggestions()' in click_handler,
                        'a rejected candidate must disappear rather than remain tappable')


if __name__ == '__main__':
    unittest.main()
