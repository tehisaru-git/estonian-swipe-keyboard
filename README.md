# Estonian Swipe (Test)

An **experimental, offline Android keyboard** with an independently drawn Estonian/English interface and CleverKeys-derived CTC/geometric swipe recognition. Licensed under GPL-3.0; [CleverKeys](https://github.com/tribixbite/CleverKeys) is credited in [NOTICE](NOTICE) and its original README is [archived here](UPSTREAM_README.md). No Gboard code, model, artwork or word list is included.

**v0.2.1 is an experimental Pixel 8 / GrapheneOS update, not a dependable daily-keyboard claim.** Its independent light keyboard now follows the user-supplied Gboard layout reference more closely: a taller five-row key surface, fixed toolbar/candidate strip, wider spacebar, smaller corner symbols, original drawn control icons and matching light navigation background. The minified release still needs a real Pixel 8 screenshot/typing check after installation. Human-finger Estonian top-1/top-3 accuracy, latency, ergonomics and battery use on a physical Pixel 8 have **not** been measured. Keep another keyboard installed; check important text before sending.

## Install or update on Pixel 8

1. Download **`EstonianSwipe-v0.2.1-arm64-v8a.apk`** from the [v0.2.1 GitHub Release](https://github.com/tehisaru-git/estonian-swipe-keyboard/releases/tag/v0.2.1), or add `https://github.com/tehisaru-git/estonian-swipe-keyboard` in Obtainium's **Add App** screen and install the current release. The normal release contains one APK. Do not choose a debug APK or upstream CleverKeys APK.
2. Android may ask whether the app used for the download is allowed to install packages. Open **Estonian Swipe (Test)**, choose **Enable keyboard** and **Select keyboard**. Keep Gboard or your previous keyboard installed for a quick switch back.
3. Use **ET/EN in the toolbar** for separate Estonian and English recognition. The Estonian word list is bundled into this APK; the old v0.1.0 one-time `langpack-et.zip` import is **not required**. Use `?123` for symbols, hold `s`/`z` for `š`/`ž` in Estonian, and tap the emoji key to search offline in either language. Backspace hold repeats; a short leftward Backspace swipe deletes the preceding word, and one Backspace tap immediately after a swipe deletes the word just entered. To provide a limited, opt-in trajectory export, open **Swipe study** from the app, tap **Start**, then **Export JSON** to a local file; [details and limits](docs/eval/pixel8-swipe-study.md).

The app ID is `io.github.tehisaru.estonianswipe`; v0.2.1 ARM64 has versionCode `2012` (v0.2.0 ARM64: `2002`, v0.1.0 ARM64: `1002`). The developer-signed release uses the same private update key as v0.1.0. Android can update earlier versions in place if the APK's signing certificate matches; do not mix differently signed builds. The private key is outside Git.

## Scope and known limits

- The active IME is `EstonianSwipeService`, with its own measured key surface, touch controller, suggestions and emoji panel. Legacy CleverKeys keyboard/view/settings code remains in the source during migration but is not the visible keyboard.
- Swipe, dictionaries and emoji search run locally. The release APK requests **no Internet or microphone permission**. GIF search and voice input are not part of this preview. It does not learn typed words or save ordinary typing history. Password fields do not submit swipe traces for prediction.
- An experimental 95,000-form Estonian lexicon is bundled and attributed to the [HeliBoard/Leipzig CC BY 4.0 source](docs/specs/estonian-offline-dictionary.md). Word-form coverage is **not** evidence of swipe accuracy. Updated language packs can still be imported through Settings.
- The broad inherited CleverKeys test suite was interrupted after memory exhaustion and some legacy failures; a bounded suite for the independent IME and a minified emulator smoke test passed. Device-specific typing and performance tests remain open. See [human-swipe research](docs/eval/human-swipe-research.md), [emulator limits](docs/eval/emulator-test-reliability.md) and the [UI specification](docs/specs/estonian-swipe-ui.md).

## Source and reproducibility

This repository is a GPLv3 **source snapshot**, based on CleverKeys commit `8335bd0`, not the upstream project's release history or F-Droid channel. The inherited `fastlane/` and `metadata/fdroid/` directories describe CleverKeys, not an Estonian Swipe listing. Third-party licenses and the offline emoji source are listed in [NOTICE](NOTICE). The upstream debug signing key and release/deployment workflows were intentionally excluded from this fork.

Build with Java 17 and Android SDK platform 34: `bash scripts/gradle-guard.sh assembleDebug`. A developer-signed release requires `RELEASE_KEYSTORE`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD` outside Git; otherwise `assembleRelease` does not produce a publishable signed update. Do not publish an upstream or debug-key-signed release.
