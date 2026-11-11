# Estonian Swipe: independent keyboard surface

## Product boundary

Build a clean, independently drawn Android keyboard that follows the familiar
Estonian Gboard **layout and interaction pattern**, without copying Google's
code, artwork, font, model, or branding. Keep the settings UI in English.
Offer Estonian and English as separate selected input languages. Support
fully offline emoji search in both languages. No microphone, GIF search or
network access is required for the first version.

**Dependency rule:** reuse GPLv3 CleverKeys-derived CTC/geometric swipe decoding,
installed language lexicons and their required model/data plumbing; do not use
`Keyboard2View`, `Pointers`, `PredictionViewSetup`, their suggestion UI,
`EmojiGridView`, or the old keyboard toolbar in the final active IME path.
Retain GPL attribution and source for reused components. The old CleverKeys
classes may remain as inactive migration/build-time source during extraction,
but must not render the shipped keyboard or handle its input.

## Required surface

- Letter rows for Estonian: `QWERTYUIOPÜ`, `ASDFGHJKLÖÄ`, Shift +
  `ZXCVBNMÕ` + Backspace; English has independent QWERTY and dictionary.
- A slim `1–0` number row, three-slot prediction strip, restrained key labels,
  light/dark rounded key faces, and a short bottom row with `?123`, comma,
  emoji, space/language switch, period, and Enter. Include the symbol layer,
  long-press characters and word-delete/cursor gestures without crowding faces.
  In the active Estonian Swipe surface, holding an alphabetic key presents its
  visible corner symbol while the finger remains down; sliding across a
  multi-choice popup selects an accent, releasing inserts exactly one choice,
  and a deliberate swipe must cancel the hold. The pictured `m` offers `?`,
  `g` offers `ğ`, `&`, `ǵ`, and Estonian `s`/`z` retain `š`/`ž` alongside
  `#`/`*`. Use original drawn geometry, not Gboard artwork. Keep three long
  candidate words in the same fixed-height row: adapt type size, never wrap
  onto the number row, ellipsize only if needed, and expose the full text to
  accessibility while preserving all three tap targets.
- Dark reference supplied by the user: a flat near-charcoal keyboard surface,
  slate rounded number/letter keys, slightly lighter Shift/Backspace/punctuation,
  high-contrast off-white labels, and one pale-blue Enter key. Use an original
  palette and vector glyphs rather than extracting/copying Gboard assets.
  The screenshot's Discord conversation, message composer, status/navigation bars,
  GIF button and microphone do **not** belong to the required keyboard UI.
  Keep a compact suggestion/emoji/language access area instead of recreating
  that crowded feature toolbar. Follow system light/dark mode and inspect the
  actual installed IME in both modes, not just a design mockup.
- The same measured key geometry must feed drawing, pointer hit-testing,
  accessibility and CTC/geometric decoding. The swipe path records raw points,
  monotonic timestamps, the lift sample, and the active layout/language.
- A small engine adapter owns CTC availability, eligible imported Estonian pack,
  missing-pack/geometric fallback, cancellation and cleanup. Do not commit a
  result after the editor, composing span or language changes. No learning on
  password fields; allow erasing learned data.
- An independent local emoji panel inserts through a guarded InputConnection;
  search uses the bundled bilingual emoji index. Keep English query priority
  intact and the typo fallback bounded.
- Launcher and settings: original icon and calm English UI, easy two-step enable/
  select flow, separate language/layout and dictionary controls; no mascot,
  animation, GIF, microphone or power-user gestures presented as required setup.

## Quality priority: Estonian typing first

The primary acceptance gate is everyday Estonian text entry, not merely a clean
screenshot or a large word count. The 95,000-form `langpack-et.zip` is an
**experimental lexicon**, not measured swipe accuracy. Keep Estonian and English
selection separate. Make the Estonian dictionary available without a hidden
setup step (bundle the licensed pack in a tested APK or guide/import it clearly),
then verify that the selected layout really changes the active lexicon and that
Estonian words do not rank behind unrelated English words.

Before replacing the active IME UI, establish a reproducible baseline on the
current engine and retain a held-out test set. Use genuine, consented Pixel 8
swipes rather than short ADB lines: frequent conversational words and sentences,
õ/ä/ö/ü, inflected forms, compounds, short ambiguous words, punctuation,
capitalization, corrections and word deletion. Record top-1 and top-3 accuracy,
wrong-language suggestions, p50/p95 decode-to-visible-suggestion latency,
missed touches, crash rate, memory and edit effort. Test both first-run and
warmed dictionaries; compare against the installed reference keyboard on the
same device and phrase list, with neither keyboard learning from test phrases
between runs. Keep development and final test sets separate; investigate failures
by dictionary coverage, frequency rank, diacritic collisions, gesture geometry,
CTC aliasing and UI/editor handling. Never treat lexicon coverage, synthetic
paths, or a green unit test as proof of human swipe quality. Tune data and
recognition against the development set, then re-evaluate the held-out set before
a release. Keep private text out of repository fixtures.

Initial read-only audit of the existing ZIP: 95,000 unique canonical entries,
23,973 containing at least one of õ/ä/ö/ü, and 579 distinct buckets (1,159
entries) that collide when those four letters are folded to a/o/u. These are
lexicon properties, **not** measured recognition quality; capitalization is
handled separately. Retain attribution to the CC BY 4.0 source and review its
redistribution obligations before embedding the pack.

## Integration checkpoints

1. Extract the decode boundary behind a small `SwipeEngine` interface from
   `swipe/CtcEngineAdapter.kt` / geometric fallback; pure test its ownership,
   geometry, language, failures and cancellation. Do not invent model results.
2. Implement a new keyboard view and gesture controller. Pure test key targets,
   tap/hold/swipe arbitration, shift, case, backspace, space, punctuation, symbols
   and orientation before wiring to an IME service.
3. Wire a fresh input method service and minimal suggestion/emoji controllers;
   test editor actions, stale-result refusal, password fields and accessibility
   on an emulator. Verify the active service does not inflate `Keyboard2View`.
4. Capture Estonian/English light/dark screenshots and verify a minified signed
   ARM64 candidate with the same application ID/certificate and a higher
   `versionCode` than v0.1.0. The user needs this **experimental test APK** to
   make the first real Pixel 8 swipes. Only after that trial compare top-1/top-3,
   latency and editing effort with Gboard on the same phrases; do not claim
   daily-use quality before those measurements.

## Pixel 8 experimental test-candidate gate

A **phone-installable preview**, not a proven daily keyboard, requires:

- The installed IME creates the new keyboard surface (not `Keyboard2View`)
  in light and dark modes; Estonian and English switch separately. Fresh install
  includes an offline Estonian lexicon without a separate download.
- Taps, space, Backspace, Enter, `s/z` holds, symbol/emoji access and
  candidate correction work in a controlled editor. Swipes reach the decoder
  and stay with the same editor/language; password fields never send traces
  to prediction. The minified release passes a bounded emulator smoke test.
- The signed ARM64 APK retains the application ID and certificate and has a
  higher version code than v0.1.0. An in-place signed update is tested, source
  and attribution are published, and release asset hash/permissions are checked.

Before claiming **daily-use quality**, a human Pixel 8 trial must measure
Estonian and English top-1/top-3, latency, correction effort, crashes and
battery impact. Check real app editors, numeric/password fields, word-delete
and cursor gestures, caps/shift, TalkBack, narrow widths and larger fonts.
A passing synthetic emulation path is not a substitute for these checks.

## Current transitional work

The public v0.1.0 APK uses the old CleverKeys renderer; the v0.2.0 preview
switches the active service to the independently drawn keyboard. Legacy source
remains during extraction, but `Keyboard2View` and `Pointers` must not render or
interpret input in the active IME.
