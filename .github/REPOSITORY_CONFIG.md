# Viipio GitHub repository configuration

This is suggested public-facing copy and a checklist, **not** a claim that GitHub settings or integrations are enabled. Verify repository settings in GitHub before announcing them.

## About

**Description:** Viipio keyboard — experimental offline Android swipe keyboard (GPL-3.0)

**Website:** https://github.com/tehisaru-git/viipio-keyboard

**Suggested topics:** `android-keyboard`, `offline`, `open-source`, `swipe-typing`. State ET/EN in the current-capabilities section, not the product tags.

Viipio is a language-neutral product name; ET and EN describe the **current preview**, not an all-languages or French release. The Android package ID `io.github.tehisaru.estonianswipe` remains unchanged for update compatibility. The GPL-3.0 source snapshot derives from [CleverKeys](https://github.com/tribixbite/CleverKeys); see [NOTICE](../NOTICE) and the [archived upstream README](../UPSTREAM_README.md). Do not present inherited CleverKeys modules or documentation as active Viipio features.

## Social preview (optional)

Use Viipio's own artwork and an experimental/offline keyboard description. If languages are mentioned, say “Currently supports Estonian (ET) and English (EN)”; do not advertise a language count, French support, voice input, online services, measured swipe accuracy or unverified accessibility coverage.

## Repository settings to review

- Enable issues if the [bug and feature templates](ISSUE_TEMPLATE/) are to be used; confirm their labels exist before relying on automatic labeling.
- Review vulnerability reporting, dependency alerts, branch rules and workflow permissions for the actual repository. Grant Actions only the permissions each workflow needs; do not assume inherited upstream CI or release workflows are active.
- Link only published, verified release APKs and checksums. See the [README](../README.md) for current experimental status, signing/update caveats and the release link.
- Keep GPL-3.0 licensing, upstream attribution and the archived upstream README visible. Do not replace the inherited project's history with Viipio claims.
