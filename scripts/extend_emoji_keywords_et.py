#!/usr/bin/env python3
"""Merge Unicode CLDR Estonian emoji annotations into the existing offline TSV.

Pinned source: https://github.com/unicode-org/cldr/blob/f69257926c43f1cb22cf3fcd93e709a2ca994bb7/common/annotations/et.xml
SHA-256: 391ac3ea184e6425eaef80e2c9358bd55265e91a07054c296f2dbb449a700f64
License: Unicode-3.0 (the repository's NOTICE includes its copyright/terms).
Only glyphs already present in res/raw/emojis.txt are indexed.
"""

import argparse
import hashlib
import unicodedata
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

PINNED_SHA256 = "391ac3ea184e6425eaef80e2c9358bd55265e91a07054c296f2dbb449a700f64"

# Project-authored ranking choices for common queries (not copied from Gboard).
PREFERRED_ET = {
    "süda": "❤️", "õun": "🍎", "päike": "☀️", "koer": "🐕",
    "kass": "🐈", "naeratus": "😀", "sünnipäev": "🎂",
    "armastus": "❤️", "tuli": "🔥",
}


def unaccent(text: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFD", text) if not unicodedata.combining(c))


def build_keywords(xml: str, available: set[str]) -> dict[str, list[str]]:
    """Rank label matches first, then exact annotation terms and component words."""
    canonical = {}
    for glyph in available:
        key = glyph.replace("\ufe0f", "")
        if key not in canonical or "\ufe0f" in glyph:
            canonical[key] = glyph
    labels = defaultdict(list)
    tags = defaultdict(list)
    for annotation in ET.fromstring(xml).iter("annotation"):
        raw = annotation.attrib.get("cp", "")
        glyph = raw if raw in available else canonical.get(raw.replace("\ufe0f", ""))
        if not glyph or not annotation.text:
            continue
        is_label = annotation.attrib.get("type") == "tts"
        for phrase in annotation.text.split("|"):
            phrase = phrase.strip().casefold()
            if not phrase or len(phrase) > 60:
                continue
            terms = [phrase]
            terms.extend(word for word in phrase.split() if word != phrase)
            for pos, term in enumerate(terms):
                if not (2 <= len(term) <= 30) or "\t" in term or "\n" in term:
                    continue
                # A translation's full label beats its component words and generic tags.
                priority = (0 if pos == 0 else 1) if is_label else (2 if pos == 0 else 3)
                (labels if priority < 2 else tags)[term].append((priority, glyph))
    ranked = {}
    for term in sorted(labels.keys() | tags.keys()):
        ranked[term] = sorted(labels[term] + tags[term], key=lambda item: item[0])
    output = {}
    for term, values in ranked.items():
        unique = list(dict.fromkeys(glyph for _, glyph in values))[:20]
        for alias in (term, unaccent(term)):
            if alias not in output:
                output[alias] = []
            output[alias] = list(dict.fromkeys(output[alias] + unique))[:20]
    for term, preferred in PREFERRED_ET.items():
        for alias in (term, unaccent(term)):
            glyphs = output.get(alias)
            if glyphs and preferred in glyphs:
                output[alias] = [preferred] + [glyph for glyph in glyphs if glyph != preferred]
    return output


def merge_keywords(original: str, localized: dict[str, list[str]]) -> str:
    """Keep English ranking on existing keys; add Estonian terms and aliases."""
    merged = {}
    for line in original.splitlines():
        keyword, sep, glyphs = line.partition("\t")
        if sep:
            merged[keyword] = list(dict.fromkeys(merged.get(keyword, []) + glyphs.split(",")))
    for keyword, glyphs in localized.items():
        merged[keyword] = list(dict.fromkeys(merged.get(keyword, []) + glyphs))
    return "\n".join(f"{keyword}\t{','.join(merged[keyword])}" for keyword in sorted(merged)) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cldr", type=Path, required=True, help="Pinned common/annotations/et.xml")
    parser.add_argument("--emoji-list", type=Path, required=True)
    parser.add_argument("--index", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    source = args.cldr.read_bytes()
    digest = hashlib.sha256(source).hexdigest()
    if digest != PINNED_SHA256:
        parser.error(f"unexpected CLDR source SHA-256 {digest}")
    valid = set(args.emoji_list.read_text(encoding="utf-8").split("\n\n", 1)[0].splitlines())
    localized = build_keywords(source.decode("utf-8"), valid)
    result = merge_keywords(args.index.read_text(encoding="utf-8"), localized)
    args.out.write_text(result, encoding="utf-8")
    print(f"Merged {len(localized)} Estonian keywords/aliases; output {len(result.splitlines())} entries")


if __name__ == "__main__":
    main()
