# Pixel 8 light-keyboard comparison (user-supplied screenshots)

Reference images (1080×2400): first/third show Gboard in the Discord composer, second shows Estonian Swipe v0.2.0 in the same app. These are visual/behavioral measurements, **not** permission to copy Google's code, fonts, icons, bitmaps, or models. The app's own message composer and navigation controls are not part of the keyboard.

| Observed surface | Gboard reference | Estonian Swipe v0.2.0 |
|---|---|---|
| Keyboard surface top | y≈1394, includes a toolbar/candidate slot | y≈1644, no toolbar in released version |
| Key rows | Number row y≈1531–1648, letters from ≈1669, bottom keys y≈2112–2243 | Number row y≈1652–1762; letters lower and compressed into ≈630 px |
| Background | pale blue RGB(238,243,251), including the navigation area | light gray RGB(240,241,244), black navigation area |
| Keys | white rounded cells; medium-gray modifiers; round blue Enter with white symbol | white cells; square-ish gray modifiers; pale-blue Enter with dark symbol |
| Bottom row | ?123, comma, emoji, long space labelled Eesti, period, Enter | ?123, comma, emoji, extra ET key, shorter space, period, Enter |
| Label details | small secondary symbols in top-right of letter cells; bold, originally drawn control icons | no secondary symbols; text glyphs for control keys |
| Upper strip | always-present toolbar before typing; three inline candidates with separators after typing | absent in v0.2.0; subsequent local candidate strip was plain boxed text |

Implementation targets: total IME height about 330 dp on this Pixel 8 density, including a fixed 44 dp strip and ≈286–290 dp key surface. Keep the strip allocated while toolbar/candidates swap to avoid moving the editor; put an operable language switch in the strip after removing the bottom-row ET cell. An inline emoji panel must **trade height with the key surface** rather than add to the IME: the first taller-keyboard experiment hid the editor completely in the emulator when the old 194 dp panel appeared. The compact 100 dp scrolling emoji panel and 190 dp reduced-height keys keep the same 334 dp envelope. Match colors, spacing, corner radii, secondary labels and navigation region with original drawing. Verify by a real Pixel 8 screenshot after distribution; the 720×1600 emulator is a geometry smoke test, not a pixel-for-pixel Pixel 8 rendering test.
