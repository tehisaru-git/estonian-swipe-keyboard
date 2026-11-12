"""Update compatibility and public artifact identity for the Viipio release."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
GRADLE = (ROOT / "build.gradle").read_text()
README = (ROOT / "README.md").read_text()


class ViipioReleaseMetadataTest(unittest.TestCase):
    def test_version_and_apk_name_are_branded(self):
        versions = dict(re.findall(r"ext\.(VERSION_MAJOR|VERSION_MINOR|VERSION_PATCH)\s*=\s*(\d+)", GRADLE))
        self.assertEqual(versions, {"VERSION_MAJOR": "0", "VERSION_MINOR": "4", "VERSION_PATCH": "0"})
        self.assertIn('ViipioKeyboard-v${variant.versionName}-${abi}.apk', GRADLE)
        self.assertIn('applicationId "io.github.tehisaru.estonianswipe"', GRADLE)

    def test_build_variants_do_not_override_branded_app_label(self):
        self.assertNotIn('Estonian Swipe (Test)', GRADLE)
        self.assertNotIn('Estonian Swipe (Debug)', GRADLE)
        self.assertIn('resValue "string", "app_name", "Viipio keyboard (Debug)"', GRADLE)

    def test_english_dictionary_is_reproduced_from_tracked_ckdt_source(self):
        source = ROOT / 'scripts/dictionaries/en/en_enhanced.bin'
        self.assertTrue(source.is_file())
        with source.open('rb') as dictionary:
            self.assertEqual(dictionary.read(4), b'CKDT')
        self.assertTrue('scripts/dictionaries/en/en_enhanced.bin' in GRADLE,
                        'build must copy the tracked CKDT dictionary')
        self.assertTrue('en_enhanced.bin' in GRADLE, 'English asset must be packaged')

    def test_public_links_and_previous_name_migration_are_documented(self):
        self.assertIn("https://github.com/tehisaru-git/viipio-keyboard", README)
        self.assertIn("ViipioKeyboard-v0.4.0-arm64-v8a.apk", README)
        self.assertIn("previously called **Estonian Swipe**", README)
        self.assertIn("io.github.tehisaru.estonianswipe", README)


if __name__ == "__main__":
    unittest.main()
