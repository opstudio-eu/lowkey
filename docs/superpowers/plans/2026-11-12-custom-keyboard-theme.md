# Custom keyboard theme: implementation plan

Design: [2026-11-12-custom-keyboard-theme-design.md](../specs/2026-11-12-custom-keyboard-theme-design.md).

1. **Contrast in main.** Move `contrast`, `AA_TEXT` and `AA_NON_TEXT` from the test sources to `companion/theme/Contrast.kt` in main; add `prefersLightInk(color)` and `bestInk(color)`. Existing tests keep their imports.
2. **Contrast checks.** Add `keyboardContrastChecks(colors)` with the pairs from `KeyboardColorsContrastTest`; the test then asserts that the standard palettes have no failures.
3. **Derivation (TDD).** Add `KeyboardSeed` (five opaque colours, hex parse/format) and `deriveKeyboardColors(seed)`. Tests: standard seeds round-trip, well-chosen custom seeds pass every check, low-contrast seeds report failures, accent ink is black or white by contrast, opaque-only parsing.
4. **Contrast fix (TDD).** Add `fixContrast(seed)` returning the adjusted seed and its remaining failures. Tests: a fixable palette ends with none, an unchanged good palette is returned unchanged, the result never has more failures.
5. **Store.** Add `KeyboardTheme` (enabled, seed) with `resolveKeyboardColors(theme, appDark)`, and `KeyboardThemeStore` on its own SharedPreferences file; wire it into `AppGraph`. Tests for resolution and encoding.
6. **Keyboard.** Replace `KeyboardUiState.dark` with `colors`; the service collects the theme store and styles the navigation bar from the palette background.
7. **Settings UI.** Link row under Appearance, new `Destination.KEYBOARD_THEME`, `KeyboardThemeScreen` with mode choice, preview, swatches, picker, warnings and actions. Draft is `rememberSaveable`.
8. **Checks.** `./gradlew testDebugUnitTest assembleDebug lintDebug`; install the debug build on the UX Preview emulator and verify: switch to Custom, edit a colour via slider and hex, invalid hex, warnings and fix, Cancel, Save, Test keyboard shows the colours in keys, popup, strip, clipboard, voice row, model picker, chooser and minimized bar, Standard restores the standard palette and keeps the saved colours.

## Verification status

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
