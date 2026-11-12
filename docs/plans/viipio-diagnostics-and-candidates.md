# Viipio keyboard: verified corrections, swipe→tap spacing, long candidates

This is an implementation contract, **not** a claim that every case ships. It applies only to the active `EstonianSwipeService` and its explicit local diagnostics consent. The Gboard comparison below is behavioral; copy no Google code, assets, models or dictionaries.

## Current behavior and failure boundary

- A swipe trace records ET candidates, chosen word, geometry and sampled path in the encrypted local store if and only if collection is explicitly enabled and `TelemetryPolicy` accepts the field. Immediate verified candidate replacement and Backspace-undo followed by a verified retyped word can be linked to that trace (`EstonianSwipeService.kt`, `SwipeCorrectionTracker.kt`, `TelemetryController.kt`).
- The service remembers one active `loggedSwipe`, finalizes it before the next swipe, and forgets it on external cursor motion. A later edit to the prior or penultimate word **is not attributable today**. A matching spelling at another position does not prove identity; never infer a correction from it.
- `ManualTypingPolicy.commitLetterAfterSwipe` already tries to add a space with the first tapped letter when the collapsed caret still follows the exact swipe word. An editor that rejects the combined write gets the letter only. The in-progress branch adds an enabled-by-default switch for the active IME; do not force a separator in password/URL fields, a moved cursor, after existing whitespace/punctuation, or when the user has selected text.

## Diagnostic attribution contract

- **Owner decision:** record only corrections that can be firmly attributed to the original swipe. A later cursor move to an older word followed by a same-spelling range match does not prove provenance: an app can change and restore text without notifying the IME. Do **not** passively store such a word or rewrite the original trace’s `finalWord` on that evidence. The attempted two-receipt passive tracker was removed after the fail-closed review, not shipped.
- A future explicit **Mark correction** action may let the user deliberately associate a selected earlier word with an already collected swipe. It requires its own user gesture, a current eligible field, a bounded choice of recent swipe IDs/words, a confirmation that the linkage is user-declared (not automatically verified), and renewed privacy-consent copy before it collects additional text. Design and test that separately; it is **not** part of the current patch.
- Keep the existing immediate, verified candidate replacement and Backspace-undo/retype chain. Preserve the AES-GCM per-event store, 30-day logical export window and SAF user-selected plaintext export. Password, number, email, URL, incognito/no-learning and non-ET fields remain excluded at both IME and storage boundaries. Old encrypted segments are not necessarily physically removed by logical pruning; **Erase local data** remains the physical deletion route.
- Regression tests for the future explicit action must include two identical swiped words, a changed/restored editor buffer at the same offsets, a field switch, selection of unrelated text, missing readback and data collection OFF. Never silently infer an older-word correction from these cases or put personally entered text in fixtures.

## Spacing control

- Persist a Viipio-specific **Add space after swipe when typing** Boolean, default ON, in direct-boot-aware app preferences. Read at the first manual letter after a swipe; OFF commits only that letter. ON commits `" " + letter` in one verified write, or only the letter if ownership/readback/write fails. The user's explicit Space remains explicit. Expose the switch in a real reachable English settings control and test toggling with an installed editor; never silently change existing user data.

## Candidate readability

Google documents tapping a suggestion to use it and long-press-and-drag to Trash to remove it, not a long-press full-word preview.[1] The inspected official help does not establish horizontal scrolling of the suggestion strip. Viipio therefore uses an **original** horizontal-scroll candidate strip because the user explicitly wants it, not because it is proven Gboard behavior: render each complete word on one line at a readable size, allow one candidate to use the available width, and let a finger drag the row left/right **only when** the combined natural widths exceed the available space; when they fit, the row must stay still and show no scroll affordance. Make overflow discoverable without hiding the word ending behind `…`; a drag must never select a candidate or commit editor text, while a tap still passes the current editor/offer ownership check. TalkBack reads the full word. Keep toolbar height stable and the app editor visible. Android recommends at least 48×48 dp touch targets.[2] Test one very long ET inflection, three long alternatives, drag-versus-tap, light/dark, TalkBack, and unchanged editor text after scrolling.

## Sources

[1] https://support.google.com/gboard/answer/7068415?hl=en&co=GENIE.Platform%3DAndroid
[2] https://developer.android.com/guide/topics/ui/accessibility/apps
