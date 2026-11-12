"""Opt-in diagnostics has one visible route in the unified settings home."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
LAUNCHER = ROOT / 'src/main/kotlin/tribixbite/cleverkeys/activities/LauncherActivity.kt'
SCREEN = ROOT / 'src/main/kotlin/tribixbite/cleverkeys/ui/settings/SettingsScreen.kt'
STRINGS = ROOT / 'res/values/strings.xml'


class DiagnosticsEntryTest(unittest.TestCase):
    def test_settings_home_has_visible_direct_route_without_starting_collection(self):
        source = SCREEN.read_text()
        self.assertIn('TelemetryActivity::class.java', source)
        self.assertIn('telemetry_title', source)
        self.assertNotIn('TelemetryActivity::class.java', LAUNCHER.read_text(),
                         'diagnostics must not remain on a competing setup screen')
        self.assertNotIn('TelemetryRuntime.enable(', source,
                         'opening the screen must not turn collection on')
        values = {s.get('name'): s.text for s in ET.parse(STRINGS).getroot().findall('string')}
        self.assertIn('telemetry_title', values)
        self.assertIn('swipe_setup_diagnostics_detail', values)
        self.assertIn('off', (values.get('swipe_setup_diagnostics_detail') or '').lower())


if __name__ == '__main__':
    unittest.main()
