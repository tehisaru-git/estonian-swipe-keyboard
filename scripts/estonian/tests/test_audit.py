"""Synthetic CKDT fixtures: no private text or external packages."""
import json
import struct
import sys
import unittest
import warnings
import zipfile
from io import BytesIO
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from audit_langpack import audit, fold, parse_ckdt, AuditError, PROBES  # noqa: E402


def ckdt(entries, *, count=None, offset=48, normalized_offset=None):
    body = b''.join(struct.pack('<H', len(w.encode())) + w.encode() + bytes([r]) for w, r in entries)
    header = struct.pack('<4sI4sIII', b'CKDT', 2, b'et\0\0',
                         len(entries) if count is None else count, offset,
                         normalized_offset if normalized_offset is not None else offset + len(body))
    header += struct.pack('<I', offset + len(body)) + bytes(20)
    return header + bytes(max(0, offset - 48)) + body


def pack(entries, manifest_count=None):
    raw = ckdt(entries)
    out = BytesIO()
    with zipfile.ZipFile(out, 'w') as z:
        z.writestr('manifest.json', json.dumps({'code': 'et', 'wordCount':
                   len(entries) if manifest_count is None else manifest_count}))
        z.writestr('dictionary.bin', raw)
    return out.getvalue()


class AuditTests(unittest.TestCase):
    def test_rank_and_manifest_and_probe_coverage(self):
        entries = [('käsi', 1), ('kasi', 200), ('maja', 0), ('majja', 64)]
        result = audit(pack(entries))
        self.assertEqual(result['word_count'], 4)
        self.assertEqual(result['rank_histogram']['0-31'], 2)
        self.assertEqual(result['rank_histogram']['64-95'], 1)
        self.assertEqual(result['fold_collisions']['keys'], 1)
        self.assertEqual(result['fold_collisions']['words_in_collisions'], 2)
        self.assertEqual(result['diacritics']['ä'], 1)
        self.assertEqual(result['probe_groups']['case_forms']['present'], 3)
        self.assertEqual(result['probe_groups']['case_forms']['total'], len(PROBES['case_forms']))

    def test_folding_distinguishes_o_and_õ_but_collides_when_stripped(self):
        self.assertEqual(fold('õun'), 'oun')
        self.assertEqual(fold('ÄÖÜŠŽ'), 'aousz')

    def test_bad_header_and_count_rejected(self):
        for raw in (b'', b'NOPE' + ckdt([])[4:], ckdt([], count=100001),
                    ckdt([], offset=47), ckdt([], count=1)):
            with self.subTest(raw=raw[:24]), self.assertRaises(AuditError):
                parse_ckdt(raw)

    def test_truncated_length_invalid_utf8_and_section_overlap_rejected(self):
        for raw in (ckdt([('käsi', 2)])[:-1],
                    ckdt([('käsi', 2)], normalized_offset=50),
                    ckdt([('a', 2)])[:50] + b'\xff' + ckdt([('a', 2)])[51:]):
            with self.subTest(raw=raw[:24]), self.assertRaises(AuditError):
                parse_ckdt(raw)

    def test_manifest_mismatch_and_duplicate_entries_rejected(self):
        with self.assertRaises(AuditError):
            audit(pack([('maja', 1)], manifest_count=2))
        with self.assertRaises(AuditError):
            audit(pack([('maja', 1), ('maja', 2)]))

    def test_duplicate_zip_members_and_zip_bomb_bound_rejected(self):
        out = BytesIO()
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(out, 'w') as z:
                z.writestr('manifest.json', '{}')
                z.writestr('manifest.json', '{}')
                z.writestr('dictionary.bin', ckdt([]))
        with self.assertRaises(AuditError):
            audit(out.getvalue())
        with self.assertRaises(AuditError):
            parse_ckdt(ckdt([]) + bytes(16 * 1024 * 1024))


if __name__ == '__main__':
    unittest.main()
