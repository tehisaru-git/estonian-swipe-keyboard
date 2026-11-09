#!/usr/bin/env python3
"""Create a bounded Estonian frequency list from AOSP .combined text.

Input attribution: HeliBoard aosp-dictionaries `main_et__experimental`, derived
from Leipzig Corpora word lists under CC BY 4.0. This converter is not a source
of new words and never reads Google keyboard data.
"""

import argparse
import re
from pathlib import Path

_WORD = re.compile(r"^ word=([^,]+),f=(\d+)(?:,|$)")
_LETTERS = frozenset("abcdefghijklmnopqrstuvwxyzõäöüšž")


def convert(source: Path, output: Path, limit: int = 95_000) -> int:
    """Write descending frequency order; keep original Estonian spelling."""
    if not 1 <= limit <= 100_000:
        raise ValueError("limit must be between 1 and 100000")
    words: dict[str, int] = {}
    with source.open(encoding="utf-8") as stream:
        for line in stream:
            match = _WORD.match(line)
            if match is None:
                continue
            word, raw_score = match.groups()
            score = int(raw_score)
            if not 0 <= score <= 255 or not word or len(word) > 30:
                continue
            if any(char not in _LETTERS for char in word):
                continue
            words[word] = max(score, words.get(word, -1))
    selected = sorted(words.items(), key=lambda item: (-item[1], item[0]))[:limit]
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as stream:
        for word, score in selected:
            stream.write(f"{word}\t{score}\n")
    return len(selected)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--limit", type=int, default=95_000)
    options = parser.parse_args()
    print(f"Wrote {convert(options.source, options.output, options.limit)} Estonian words")
