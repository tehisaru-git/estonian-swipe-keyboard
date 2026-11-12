"""The opt-in diagnostics screen must be reachable in the branded launcher."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
LAUNCHER = ROOT / 'src/main/kotlin/tribixbite/cleverkeys/activities/LauncherActivity.kt'
STRINGS = ROOT / 'res/values/strings.xml'


class DiagnosticsEntryTest(unittest.TestCase):
    def test_launcher_has_visible_direct_route_without_starting_collection(self):
        source = LAUNCHER.read_text()
        self.assertTrue('TelemetryActivity::class.java' in source,
                        'v0.3.0 removed the only launcher route to opt-in diagnostics')
        self.assertTrue('onClick = onOpenDiagnostics' in source,
                        'the destination must have a visible user-operated control')
        self.assertNotIn('TelemetryRuntime.enable(', source,
                         'opening the screen must not turn collection on')
        values = {s.get('name'): s.text for s in ET.parse(STRINGS).getroot().findall('string')}
        self.assertIn('telemetry_title', values)
        self.assertIn('swipe_setup_diagnostics_detail', values)
        self.assertIn('off', (values.get('swipe_setup_diagnostics_detail') or '').lower())


if __name__ == '__main__':
    unittest.main()
