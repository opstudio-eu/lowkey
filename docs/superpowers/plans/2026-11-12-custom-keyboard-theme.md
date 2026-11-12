# Custom keyboard theme: implementation plan

## Current verification status

The current, complete status is in
[2026-11-12-custom-keyboard-theme-verification.md](2026-11-12-custom-keyboard-theme-verification.md):
what passed in the UI on Android 15 and Android 14, what is covered by unit tests only, and what
was not verified and why. The "pending" notes further down are kept as history and are superseded
by that report. Device testing found one bug (no contrast warning for clipboard chip text on the
key colour), now fixed with a regression test.

First device pass (Android 15), kept for reference: a portrait image chosen through the photo
picker with the suggested 56% dimming and saved; Remove then Cancel restored it; the image showed
behind normal keys, the minimized bar and the voice row with opaque keys; a letter key typed and its
long press inserted the number; a space-label override `FFDD44` changed only that label; the image,
dimming and override survived an app relaunch.

Design: [2026-11-12-custom-keyboard-theme-design.md](../specs/2026-11-12-custom-keyboard-theme-design.md).

1. **Contrast in main.** Move `contrast`, `AA_TEXT` and `AA_NON_TEXT` from the test sources to `companion/theme/Contrast.kt` in main; add `prefersLightInk(color)` and `bestInk(color)`. Existing tests keep their imports.
2. **Contrast checks.** Add `keyboardContrastChecks(colors)` with the pairs from `KeyboardColorsContrastTest`; the test then asserts that the standard palettes have no failures.
3. **Derivation (TDD).** Add `KeyboardSeed` (five opaque colours, hex parse/format) and `deriveKeyboardColors(seed)`. Tests: standard seeds round-trip, well-chosen custom seeds pass every check, low-contrast seeds report failures, accent ink is black or white by contrast, opaque-only parsing.
4. **Contrast fix (TDD).** Add `fixContrast(seed)` returning the adjusted seed and its remaining failures. Tests: a fixable palette ends with none, an unchanged good palette is returned unchanged, the result never has more failures.
5. **Store.** Add `KeyboardTheme` (enabled, seed) with `resolveKeyboardColors(theme, appDark)`, and `KeyboardThemeStore` on its own SharedPreferences file; wire it into `AppGraph`. Tests for resolution and encoding.
6. **Keyboard.** Replace `KeyboardUiState.dark` with `colors`; the service collects the theme store and styles the navigation bar from the palette background.
7. **Settings UI.** Link row under Appearance, new `Destination.KEYBOARD_THEME`, `KeyboardThemeScreen` with mode choice, preview, swatches, picker, warnings and actions. Draft is `rememberSaveable`.
8. **Checks.** `./gradlew testDebugUnitTest assembleDebug lintDebug`; install the debug build on the UX Preview emulator and verify: switch to Custom, edit a colour via slider and hex, invalid hex, warnings and fix, Cancel, Save, Test keyboard shows the colours in keys, popup, strip, clipboard, voice row, model picker, chooser and minimized bar, Standard restores the standard palette and keeps the saved colours.

## Extension: advanced colours and background image

9. **Roles (TDD).** Remove the never-drawn roles. Add `ColorRole` (group, label, seed, getter) and `keyboardColorsOf`. Tests: every role reads back what it wrote; seed roles map to the five seeds.
10. **Overrides (TDD).** `CustomKeyboardTheme(seed, overrides, image, dim)` with `effectiveColors()`; `resolveKeyboardColors` uses it. Tests: an override changes only its role, reset restores the derived value, seed roles cannot be overridden, transparent overrides are rejected.
11. **Persistence (TDD).** Pure encode/decode of the preferences values. Tests: a theme saved before overrides loads unchanged, overrides and image round-trip, unknown roles, bad colours, unsafe file names and out-of-range dimming are dropped or clamped, turning custom off keeps everything.
12. **Contrast (TDD).** Checks name their roles; `ImageTone` gives the dimmed image's luminance range and worst-case ratios for pairs on the background. `fixContrast` evaluates the effective theme and moves seeds only. Tests: an override's failure is reported and not fixed, an image can fail pairs the solid background passes, the dimming suggestion resolves them, the fix never claims success while failures remain.
13. **Image import (TDD where pure).** Sample size, target size, EXIF orientation transform and limits as pure functions with tests; Android decode, rotate, scale and encode around them; versioned files, temp-then-rename, cleanup of unreferenced files.
14. **Keyboard.** Shared one-entry image cache loaded on IO; `keyboardBackground` modifier used by `KeyboardRoot` and the preview; strip items stop painting the background colour; navigation bar icons by worst case over the image from Android 15.
15. **Editor.** Draft is the whole `CustomKeyboardTheme`; image section with picker, Replace, Remove and Dimming; Advanced colors grouped with Automatic/override and reset; contrast section with image note and dimming suggestion.
16. **Checks.** Unit tests, `assembleDebug`, `lintDebug`; on the emulator: override one role and see only it change, reset, save/cancel, choose/replace/remove an image, missing file fallback, relaunch, and the real keyboard in keys, voice, model picker, minimized, chooser, popups, suggestions, clipboard and navigation bar.

## Verification status (history)

The device notes in this and the following sections were written before device testing finished;
see the verification report linked at the top for the current status.

The editor was exercised on Android 15: custom selection, hex input, sliders, contrast correction,
Cancel and Save. Review tightened hex validation so invalid or overlong pasted text cannot silently
become a different valid colour; regression tests cover this.
After that correction, all 326 unit tests passed with none skipped, and the debug APK built.

Actual input-method verification is still pending. The shared emulator was held by another session;
opening it and retrying with the tool's bounded wait both returned `DEVICE_IN_USE`. No other session
was taken over or closed. Still verify typing, pressed keys, accent popups, suggestions, clipboard,
voice and model panels, minimized mode, keyboard chooser, navigation bar, persistence after relaunch,
and switching back to Standard while retaining saved colours. Android 14 coverage is also pending.
The feature must not be considered fully verified until those interactions pass.

### Extension status

Steps 9 to 15 are implemented. Unit tests cover role mapping, override resolution and reset, old
preference compatibility, stored-value validation, the editor draft's saved form, worst-case image
contrast, the dimming suggestion, fixes that keep overrides, navigation bar icons, and the image size,
sample, orientation and crop arithmetic. `testDebugUnitTest` (362 tests, none skipped or failing),
`assembleDebug` and `lintDebug` pass.

Device verification of the extension is pending. The Android 15 emulator was held by another
session; a bounded 90-second wait returned `DEVICE_IN_USE`, and no other session was taken over.
Still verify on a device, in the editor and in the real keyboard: one advanced override changes only
its role, Reset to automatic, Save and Cancel with overrides and images, choosing, replacing and
removing an image (portrait, landscape, rotated EXIF, PNG with transparency, an unsupported file),
dimming, a deleted image file falling back to the colour, relaunch and process recreation with a
draft image, and the keyboard in keys, voice, model picker, minimized and chooser modes with
pressed keys, popups, suggestions, clipboard and the navigation bar. Android 14 is also pending.

### Review fixes: storage failures and image cleanup

Review found four problems, now fixed with regression tests:

- A cleanup used the saved image as it was when requested, so a cleanup still queued during a save
  could delete the newly saved image, and cleanup could delete a fresh import before the editor took
  it. `BackgroundImages` now holds the saved image, the draft image and fresh imports, and checks
  those holds per file at deletion time under the lock that changes them.
- The theme was published before the preferences commit, whose result was ignored. The store now
  commits on an IO thread through a `ThemePersistence` seam, checks the result and publishes only
  after success; a failure keeps the draft, shows an error and deletes nothing.
- Standard/Custom stayed active during an import. It is now disabled while an import or write runs;
  an import that finishes after the custom theme was turned off is discarded.
- Decoding a stored image caught only out of memory; other decode errors now fall back to no image
  without swallowing cancellation. Import also maps unexpected bitmap errors to "unsupported".

`KeyboardThemeStoreTest` (fake persistence, temporary folder, test dispatcher) covers commit-before-
publish order, failed save and switch, a stale cleanup during save, fresh and stale imports, draft
replacement, the saved image while custom is off, and startup cleanup; `BackgroundImagesDecodeTest`
covers decode failures and cancellation. Mutating the store to ignore the commit result, or the
cleanup to ignore fresh imports, makes these tests fail. `testDebugUnitTest` (373 tests, none skipped
or failing), `assembleDebug` and `lintDebug` pass.

Device verification is still pending (the emulator was held by another session). Also verify the
disabled controls during an import, the save error (hard to force on a device), and rotation during
an import. (Superseded: the verification report lists which of these were done on a device and
which remain automated only.)

### Contrast pair for clipboard chips

Device testing showed clipboard chips draw suggestion text on the key colour without a matching
contrast check. `keyboardContrastChecks` now includes "Clipboard text on clipboard chips", with a
regression test in `CustomKeyboardThemeTest`. `testDebugUnitTest` (374 tests, none skipped or
failing), `assembleDebug` and `lintDebug` pass.
