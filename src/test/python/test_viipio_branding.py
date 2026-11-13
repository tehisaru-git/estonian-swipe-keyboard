"""Standalone branding/resource contract: python3 -m unittest discover -s src/test/python."""
import pathlib
import importlib.util
import unittest
import xml.etree.ElementTree as ET
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[3]
ANDROID = '{http://schemas.android.com/apk/res/android}'


class ViipioBrandingTest(unittest.TestCase):
    def test_product_name_and_english_onboarding(self):
        strings = ET.parse(ROOT / 'res/values/strings.xml').getroot()
        by_name = {item.get('name'): item for item in strings.findall('string')}
        self.assertEqual(by_name['app_name'].text, 'Viipio keyboard')
        for name in ('app_name', 'estonian_swipe_ime_label'):
            self.assertEqual(by_name[name].get('translatable'), 'false')
            self.assertEqual(by_name[name].text, 'Viipio keyboard')
        for name in ('swipe_setup_enable_detail', 'swipe_setup_select_detail'):
            self.assertIn('Viipio keyboard', by_name[name].text)
            self.assertEqual(by_name[name].get('translatable'), 'false')
        for locale in (ROOT / 'res').glob('values-*/strings.xml'):
            with self.subTest(locale=locale.parent.name):
                localized = ET.parse(locale).getroot()
                for item in localized.findall('string'):
                    self.assertNotIn(item.get('name'), ('settings_title', 'manifest_ime_service_label', 'launcher_logo_desc'))

    def test_product_intro_is_language_neutral_but_current_support_is_explicit(self):
        strings = ET.parse(ROOT / 'res/values/strings.xml').getroot()
        by_name = {item.get('name'): (item.text or '') for item in strings.findall('string')}
        for name in ('swipe_setup_intro', 'swipe_preview_intro'):
            self.assertNotIn('Estonian and English', by_name[name])
            self.assertIn('keyboard', by_name[name])
        self.assertIn('Estonian', by_name['swipe_preview_languages_detail'])
        self.assertIn('English', by_name['swipe_preview_dictionaries_detail'])

    def test_manifest_keeps_ime_and_installed_identity(self):
        root = ET.parse(ROOT / 'AndroidManifest.xml').getroot()
        app = root.find('application')
        self.assertEqual(app.get(ANDROID + 'label'), '@string/app_name')
        self.assertEqual(app.get(ANDROID + 'icon'), '@mipmap/ic_launcher')
        service = next(s for s in app.findall('service') if s.get(ANDROID + 'name') == 'tribixbite.cleverkeys.EstonianSwipeService')
        self.assertEqual(service.get(ANDROID + 'label'), '@string/estonian_swipe_ime_label')
        build = (ROOT / 'build.gradle').read_text()
        self.assertIn('applicationId "io.github.tehisaru.estonianswipe"', build)

    def test_icon_is_adaptive_and_has_monochrome_mask(self):
        icon = ET.parse(ROOT / 'res/mipmap-anydpi-v26/ic_launcher.xml').getroot()
        self.assertEqual(icon.find('background').get(ANDROID + 'drawable'), '@drawable/ic_launcher_background')
        self.assertEqual(icon.find('foreground').get(ANDROID + 'drawable'), '@drawable/ic_launcher_foreground')
        self.assertEqual(icon.find('monochrome').get(ANDROID + 'drawable'), '@drawable/ic_launcher_monochrome')
        for name in ('foreground', 'monochrome'):
            vector = ET.parse(ROOT / f'res/drawable/ic_launcher_{name}.xml').getroot()
            self.assertEqual(vector.tag, 'vector')
            self.assertGreaterEqual(len(vector.findall('path')), 3)
        spec = importlib.util.spec_from_file_location('viipio_icon', ROOT / 'scripts/generate_estonian_swipe_icon.py')
        assert spec and spec.loader
        generator = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(generator)
        for density, size in generator.SIZES.items():
            with self.subTest(density=density):
                with Image.open(ROOT / f'res/mipmap-{density}/ic_launcher.png') as png:
                    self.assertEqual(png.size, (size, size))
                    self.assertEqual(png.convert('RGBA').tobytes(), generator.draw_mark(size).tobytes())
        self.assertFalse(list((ROOT / 'res').glob('drawable-*/ic_launcher_foreground.*')))
        self.assertFalse(list((ROOT / 'res').glob('mipmap-*/ic_launcher_foreground.*')))

    def test_launcher_has_honest_two_step_setup_and_working_routes(self):
        source = (ROOT / 'src/main/kotlin/tribixbite/cleverkeys/activities/LauncherActivity.kt').read_text()
        self.assertIn('painterResource(R.drawable.ic_launcher_foreground)', source)
        self.assertNotIn('painterResource(R.mipmap.ic_launcher)', source)
        self.assertIn('Settings.ACTION_INPUT_METHOD_SETTINGS', source)
        self.assertIn('showInputMethodPicker()', source)
        self.assertTrue('R.string.swipe_preview_languages_detail' in source, 'show actual ET/EN switch guidance')
        self.assertTrue('R.string.swipe_preview_dictionaries_detail' in source, 'describe bundled dictionaries')
        self.assertFalse('DictionaryManagerActivity::class.java' in source, 'do not link dormant dictionary manager')
        self.assertFalse('LayoutManagerActivity::class.java' in source, 'do not link dormant layout manager')
        self.assertFalse('SettingsActivity::class.java' in source, 'setup is a destination, not another settings home')
        settings = (ROOT / 'src/main/kotlin/tribixbite/cleverkeys/ui/settings/SettingsScreen.kt').read_text()
        self.assertIn('TelemetryActivity::class.java', settings, 'diagnostics remains a reachable opt-in screen')
        self.assertIn('LauncherActivity::class.java', settings, 'setup remains reachable from the settings home')
        self.assertNotIn('onOpenStudy', source)


if __name__ == '__main__':
    unittest.main()
