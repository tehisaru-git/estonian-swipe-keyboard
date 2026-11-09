"""Regression tests for the licensed Estonian AOSP wordlist converter."""
import tempfile
import unittest
from pathlib import Path

from estonian_wordlist import convert


class EstonianWordlistTest(unittest.TestCase):
    def test_preserves_diacritics_and_orders_by_frequency(self):
        source = ("dictionary=main:et,locale=et\n"
                  " word=ja,f=254\n  bigram=tema,f=1\n"
                  " word=õun,f=99\n word=õuna,f=104\n"
                  " word=üle,f=200\n word=bad42,f=250\n")
        with tempfile.TemporaryDirectory() as d:
            source_path = Path(d) / "source.combined"
            output_path = Path(d) / "words.txt"
            source_path.write_text(source, encoding="utf-8")
            convert(source_path, output_path, limit=4)
            self.assertEqual(output_path.read_text(encoding="utf-8").splitlines(),
                             ["ja\t254", "üle\t200", "õuna\t104", "õun\t99"])


if __name__ == "__main__":
    unittest.main()
