#!/usr/bin/env python3
"""Read-only CKDT V2 Estonian langpack lexicon audit (not a swipe accuracy test)."""
import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path
import struct
import unicodedata
import zipfile
from io import BytesIO

MAX_DICT = 16 * 1024 * 1024  # Match CkdtDictionaryReader limit.
MAX_WORDS = 100_000
MAX_MANIFEST = 64 * 1024
# Fixed, public, hand-picked probes; not corpus-derived or user text. Not a representative sample.
PROBES = {
    'everyday': ('tere', 'aitäh', 'palun', 'mina', 'sina', 'meie', 'tema', 'ja', 'on', 'ei', 'kodu', 'töö'),
    'case_forms': ('maja', 'majja', 'majas', 'majast', 'majal', 'majale', 'majalt',
                   'majad', 'majade', 'majades', 'käsi', 'käe', 'kätte', 'käes', 'käest'),
    'verb_forms': ('olema', 'olen', 'oled', 'oleme', 'olid', 'olnud', 'tegema', 'teen',
                   'teed', 'tegi', 'teeme', 'tehtud', 'minema', 'lähen', 'läksin'),
    'diacritic_words': ('õun', 'õige', 'õhtu', 'õpetaja', 'äri', 'käsi', 'päev', 'öö',
                        'töö', 'süda', 'üks', 'šokolaad', 'žanr'),
}


class AuditError(ValueError):
    """Invalid or unsupported pack; do not produce a partial baseline."""


def fold(word):
    """Estonian-relevant NFD diacritic fold used for potential collisions only."""
    return ''.join(c for c in unicodedata.normalize('NFD', word.lower())
                   if not ('\u0300' <= c <= '\u036f'))


def parse_ckdt(data):
    """Return canonical (word, uint8 rank) pairs, bounded by the next section."""
    if len(data) > MAX_DICT or len(data) < 48:
        raise AuditError('CKDT dictionary size outside supported bounds')
    magic, version, lang, count, start, normalized, accent = struct.unpack_from('<4sI4sIIII', data)
    if magic != b'CKDT' or version != 2 or lang != b'et\0\0':
        raise AuditError('expected Estonian CKDT V2 header')
    if count > MAX_WORDS or not 48 <= start <= normalized <= accent <= len(data):
        raise AuditError('invalid CKDT count or section offsets')
    if count > (normalized - start) // 3:
        raise AuditError('canonical count exceeds available section bytes')
    pos = start
    entries = []
    seen = set()
    for _ in range(count):
        if pos + 2 > normalized:
            raise AuditError('truncated canonical length')
        size = struct.unpack_from('<H', data, pos)[0]
        pos += 2
        if not size or pos + size + 1 > normalized:
            raise AuditError('invalid canonical word length or missing rank')
        try:
            word = data[pos:pos + size].decode('utf-8', errors='strict')
        except UnicodeDecodeError as exc:
            raise AuditError('invalid canonical UTF-8') from exc
        pos += size
        rank = data[pos]
        pos += 1
        if not word or word in seen:
            raise AuditError('empty or duplicate canonical word')
        seen.add(word)
        entries.append((word, rank))
    if pos != normalized:
        raise AuditError('canonical count/section length mismatch')
    return entries


def audit(raw_zip):
    """Validate zip and manifest, then return aggregate dictionary-only diagnostics."""
    try:
        with zipfile.ZipFile(BytesIO(raw_zip)) as z:
            names = [i.filename for i in z.infolist()]
            if names.count('manifest.json') != 1 or names.count('dictionary.bin') != 1:
                raise AuditError('pack requires exactly one manifest.json and dictionary.bin')
            manifest_info = z.getinfo('manifest.json')
            dict_info = z.getinfo('dictionary.bin')
            if manifest_info.file_size > MAX_MANIFEST or dict_info.file_size > MAX_DICT:
                raise AuditError('pack member exceeds supported size')
            manifest = json.loads(z.read(manifest_info))
            entries = parse_ckdt(z.read(dict_info))
    except (zipfile.BadZipFile, UnicodeDecodeError, json.JSONDecodeError, OSError) as exc:
        raise AuditError('invalid ZIP or manifest') from exc
    if not isinstance(manifest, dict) or manifest.get('code') != 'et' or type(manifest.get('wordCount')) is not int or manifest['wordCount'] != len(entries):
        raise AuditError('manifest language or wordCount mismatch')
    words = {w for w, _ in entries}
    hist = {f'{n}-{n + 31}': 0 for n in range(0, 256, 32)}
    marks = Counter()
    groups = defaultdict(list)
    for word, rank in entries:
        hist[f'{rank // 32 * 32}-{rank // 32 * 32 + 31}'] += 1
        for mark in 'õäöüšž':
            if mark in word.lower():
                marks[mark] += 1
        groups[fold(word)].append(word)
    collisions = [forms for forms in groups.values() if len(forms) > 1]
    probes = {name: {'present': sum(w in words for w in forms),
                     'total': len(forms), 'missing': [w for w in forms if w not in words]}
              for name, forms in PROBES.items()}
    return {
        'language': 'et', 'word_count': len(entries), 'manifest_word_count': manifest['wordCount'],
        'rank_scale': 'uint8, 0 most frequent, 255 least; quantized, not token frequency',
        'rank_histogram': hist,
        'diacritics': {mark: marks[mark] for mark in 'õäöüšž'},
        'fold_collisions': {'keys': len(collisions),
                            'words_in_collisions': sum(map(len, collisions)),
                            'max_forms_per_key': max(map(len, collisions), default=0)},
        'probe_groups': probes,
        'interpretation': 'Lexicon membership only; no swipe top-1/top-3 accuracy or user traces measured.',
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('pack', type=Path, help='existing langpack-et.zip (read-only)')
    args = parser.parse_args()
    try:
        print(json.dumps(audit(args.pack.read_bytes()), ensure_ascii=False, indent=2))
    except (AuditError, OSError) as exc:
        parser.exit(1, f'audit failed: {exc}\n')


if __name__ == '__main__':
    main()
