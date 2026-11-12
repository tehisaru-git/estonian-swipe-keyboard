# Viipio keyboard · identity and UI guide

**Displayed name:** `Viipio keyboard` (preserve this capitalization in the launcher, Android IME picker and setup). **Repository:** `tehisaru-git/viipio-keyboard`. **Android package ID:** `io.github.tehisaru.estonianswipe` — retain it and the original release certificate for in-place updates. Class and theme names inherited from the source are implementation details, not public identity. The name is user-selected; preliminary brand searches are not legal clearance.

## Mark

The original symbol has three light rounded keycaps and one continuous pale-mint gesture with a terminal dot. It conveys a keyboard and a movement without using a letterform, mascot, Gboard glyph or borrowed asset. The gesture follows a shallow V path, not a copied swipe trail. Use the vector `res/drawable/ic_launcher_foreground.xml` on Android 26+ with `ic_launcher_background.xml`, its single-colour `ic_launcher_monochrome.xml` on themed launchers, and the generated PNGs for legacy densities. `scripts/generate_estonian_swipe_icon.py` is the reproducible PNG source; its historical filename does not change the product name. Review the actual 48 px and 192 px rasterizations whenever geometry changes. Do not put words inside the icon: small labels become illegible.

## Visual tokens

- Icon background: deep navy `#22364C`; keycaps: soft white `#EDF3F7`; gesture accent: mint `#A5E4D9`.
- Setup light surface `#F6F8FB`, ink `#1F2C39`; setup dark surface `#151D28`, ink `#E5EAF1`. Source of truth for app colors remains `theme/EstonianSwipeTheme.kt`.
- Active keyboard keys keep their independently drawn slate/white palette (`estonianui/KeyboardSurfaceView.kt`) and clear blue action key. The icon accent need not tint every key; input legibility takes priority over decoration.
- Avoid animation on the keyboard itself, text in the launcher mark, crowded toolbars, decorative gradients and low-contrast selection states. Follow system light/dark mode and ensure TalkBack describes the full candidate word even when visually shortened.

## Copy and attribution

Use short English labels for user-facing setup and controls; Estonian and English are *input languages*, not separate launcher locales. Mention the former Estonian Swipe name only in migration notes and historical release records. Keep GPLv3/CleverKeys attribution in `NOTICE` and source documentation. Never imply Gboard endorsement or copy its icons, code, models or word lists.

## Release checks

Check both Android application and IME picker labels, all five density-specific legacy PNGs, adaptive/themed icon, the launcher at system light/dark settings, GitHub source link, Obtainium update from the old repository URL, package ID, versionCode and signing-certificate continuity. A source screenshot alone does not verify the installed icon or editor behavior. Human touch and accessibility on the Pixel 8 remain separate checks.
