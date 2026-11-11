# Estonian typing: lexicon-only baseline

Run without network or writes: `PYTHONDONTWRITEBYTECODE=1 python scripts/estonian/audit_langpack.py build/tooling/release-assets/langpack-et.zip`. Tests: `PYTHONDONTWRITEBYTECODE=1 python -m unittest discover -s scripts/estonian/tests -v`.

Input SHA-256: `701f9bfa93992f3d6e11e0fb50689c42151a730062228895298674b0043232b6`. The manifest attributes the CC BY 4.0 wordlist to Leipzig Corpora Collection / HeliBoard aosp-dictionaries; this audit does not modify or redistribute it. The parser checks ZIP members, CKDT V2 language/header, 48-byte header offsets, canonical record boundaries, strict UTF-8, duplicate words, maximum size/count, and agreement with manifest wordCount; it reads canonical entries only, not the normalized/accent-map sections. A successful parse is **not** validation of the other sections or of runtime pack installation.

## Results for this exact archive

- Manifest / parsed canonical entries: **95,000 / 95,000**; no corrupt canonical lengths or count mismatch detected.
- Quantized rank-byte histogram (0 = highest frequency, **not** corpus token counts): 0–31: 3,314; 32–63: 59,108; 64–95: 32,578; 96–255: 0. Do not interpret ranks as token frequency or an accuracy score.
- Canonical entries containing each Estonian diacritic (counts may overlap): õ 8,645; ä 8,745; ö 1,922; ü 5,737; š 108; ž 105.
- Lowercase NFD diacritic folding yields **587 collision keys**, containing **1,175 distinct canonical words**; maximum 3 forms/key. This estimates ambiguous lookup surfaces, not actual swipe errors. Folding here mirrors the Estonian-relevant portion of `AccentNormalizer.normalize`; it does not execute the Android decoder or normalize the stored CKDT sections.
- Fixed hand-picked membership probes (not sampled or representative): everyday **12/12**; noun case forms **14/15** (missing `majalt`); verb forms **15/15**; words with diacritics **13/13**. Probe lists live in the script and contain no user text. Counts measure exact canonical membership, not usable swipe suggestions. The case probes include forms of `maja` and `käsi`; verb probes include `olema`, `tegema`, `minema` inflections.

## Missing evaluation for real typing quality

**Swipe top-1/top-3, latency and smoothness have not been measured.** Lexicon membership cannot establish any of them. On a physical Android device with this exact installed pack, a fixed keyboard layout and recorded build/settings, collect consented, non-personal Estonian test prompts covering ordinary sentences, cases, verb inflections and õ/ä/ö/ü; use only scripted prompts and never free-form chats, names, passwords or typed private material. Keep the prompts and split fixed before tuning. For each prompt word, record ground truth, actual physical swipe trace (touch coordinates/timing) and displayed top-3 candidates; calculate top-1 and top-3 exact-match rates with numerator/denominator and separate breakdowns for diacritic/case/verb words, plus per-gesture time and observed UI stalls. Compare same prompts/device/layout/settings across builds and disclose excluded traces. No synthetic gestures, dictionary membership, or simulator predictions should be presented as real-device swipe accuracy. Keep raw traces private and delete after aggregate analysis.
