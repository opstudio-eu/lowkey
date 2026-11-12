# Companion UI/UX implementation plan

**Goal:** Implement the approved OpStudio-style companion in English, retain existing settings and deliver a test APK.

**Architecture:** Keep the current Compose app, stores, application ID and IME. Route existing deep links to explicit category screens. Native Material controls retain accessibility and state. Only the companion typography/shell changes; the keyboard track remains separate.

**Spec:** `docs/design/lowkey-ux/v3-uitwerking.md` and `settings-en-talen-audit.md`, with the user's decision to use English for the app now. No app-language picker yet.

## Constraints

- No version bump, publication, merge or unsolicited PR.
- Keep the original logo paths and all 26 existing Settings fields and storage keys.
- Preserve all model choices, keyboard languages/layouts, dictionary rules and permission/update actions.
- New keyboard modes belong to the separate keyboard-track, not this implementation.
- Fonts only under their existing OFL license, with notices; no network font loading.
- Do not install or drive the physical phone. Deliver the checked APK using the authorized file-sharing route.

## Work

- [x] Add tested canonical navigation and keyboard readiness states; preserve old deep-link names.
- [x] Implement the companion shell, open surfaces, headline type and one-time nonblocking entry animation.
- [x] Replace the long Settings list with category pages using the same stored values.
- [x] Implement honest Home/setup states, optional speech, canonical test sheet and native back behavior.
- [x] Refine Voice/models and Dictionary with existing stores, honest statuses and clear actions.
- [x] Complete the settings-preservation audit; run unit tests, lint and leak checks.
- [x] Verify real UI results on an authorized emulator, including light/dark, navigation, large text and settings persistence.
- [x] Build and verify a signed test APK or an explicitly separate test install, then share to the requested device.

## Review focus

- Enabling/choosing an IME is checked from Android state; microphone/model are optional for typing.
- Old keyboard shortcuts still open the right screen; back returns to the canonical parent.
- Model progress preserves list identity. The existing store activates completed downloads; the native action explicitly says Download & use. This intentional difference from the prototype preserves existing store behavior.
- The shared test editor keeps its draft, closes correctly and leaves settings usable.
- Large text, screen insets and notices must not hide navigation or permission actions.
- English-only models do not erase the user's preferred multilingual speech language.
- No unsigned/debug APK is presented as an in-place update for a release-signed installation.
