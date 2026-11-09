"""Regression checks for offline Estonian emoji lookup without replacing English."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from extend_emoji_keywords_et import build_keywords, merge_keywords

ROOT = Path(__file__).resolve().parents[1]


class EstonianEmojiKeywordTest(unittest.TestCase):
    def test_localized_names_rank_before_unrelated_tag_matches(self):
        xml = '''<ldml><annotations>
          <annotation cp="😻">kass | armastus | süda</annotation>
          <annotation cp="❤">armastus | süda</annotation>
          <annotation cp="❤" type="tts">punane süda</annotation>
          <annotation cp="🐕" type="tts">koer</annotation>
          <annotation cp="🥳">sünnipäev | pidu</annotation>
          <annotation cp="⛔">süda</annotation>
        </annotations></ldml>'''
        result = build_keywords(xml, {"😻", "❤️", "🐕", "🥳"})
        self.assertEqual(result["süda"][:2], ["❤️", "😻"])
        self.assertEqual(result["suda"][:2], ["❤️", "😻"])
        self.assertEqual(result["koer"][0], "🐕")
        self.assertIn("🥳", result["sünnipäev"])
        self.assertNotIn("⛔", result["süda"])

    def test_common_estonian_terms_put_expected_emoji_first(self):
        rows = dict(line.split("\t", 1) for line in
                    (ROOT / "src/main/assets/emoji_keywords.tsv").read_text(encoding="utf-8").splitlines())
        for query, expected in {
            "süda": "❤️", "suda": "❤️", "õun": "🍎", "oun": "🍎",
            "päike": "☀️", "koer": "🐕", "kass": "🐈",
            "naeratus": "😀", "sünnipäev": "🎂",
        }.items():
            with self.subTest(query=query):
                self.assertEqual(rows[query].split(",")[0], expected)

    def test_existing_english_keyword_keeps_its_first_result(self):
        self.assertEqual(
            merge_keywords("age\t🔞\n", {"age": ["😛", "🔞"]}),
            "age\t🔞,😛\n",
        )

    def test_merging_preserves_english_and_is_idempotent(self):
        original = "heart\t❤️,💙\nheart\t(♡)\n"
        localized = {"süda": ["❤️", "😻"], "suda": ["❤️", "😻"]}
        merged = merge_keywords(original, localized)
        self.assertEqual(merged.splitlines(), [
            "heart\t❤️,💙,(♡)", "suda\t❤️,😻", "süda\t❤️,😻"
        ])
        self.assertEqual(merge_keywords(merged, localized), merged)

    def test_packaged_keywords_cover_both_languages(self):
        rows = {}
        for line in (ROOT / "src/main/assets/emoji_keywords.tsv").read_text(encoding="utf-8").splitlines():
            keyword, glyphs = line.split("\t", 1)
            rows.setdefault(keyword, []).extend(glyphs.split(","))
        for query, expected in {
            "süda": "❤️", "suda": "❤️", "õun": "🍎", "oun": "🍎",
            "päike": "☀️", "paike": "☀️", "koer": "🐕", "kass": "🐈",
            "naeratus": "😀", "sünnipäev": "🎂",
            "heart": "❤️", "smile": "😀", "cat": "🐈"
        }.items():
            with self.subTest(query=query):
                self.assertIn(expected, rows[query][:20])


if __name__ == "__main__":
    unittest.main()
