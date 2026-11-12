"""New user-entered words are not in either automatic Android backup allowlist."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]


class PersonalWordsBackupBoundaryTest(unittest.TestCase):
    def test_credential_file_is_not_whitelisted_for_backup_or_transfer(self):
        for name in ('backup_rules.xml', 'data_extraction_rules.xml'):
            root = ET.parse(ROOT / 'res/xml' / name).getroot()
            includes = [node.attrib.get('path') for node in root.iter('include')
                        if node.attrib.get('domain') == 'sharedpref']
            self.assertTrue(includes, name)  # An include-only allowlist, not default-all.
            self.assertNotIn('viipio_personal_words.xml', includes, name)
            self.assertNotIn('.', includes, name)


if __name__ == '__main__':
    unittest.main()
