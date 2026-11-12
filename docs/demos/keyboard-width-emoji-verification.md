# Keyboard width and emoji verification

Follow-up to the keyboard placement and number-row work, verified on 2026-11-12.

## Implemented behavior

- Normal and floating keyboards have independent left and right width handles. The opposite edge remains fixed; normal mode can move horizontally after narrowing. Existing placement records migrate to full width without losing their other settings.
- Emoji is the default key beside Space. Settings can restore the language key. Holding Space opens **Languages & layouts**, including **Other keyboards** for Android's picker. Holding comma also opens emoji in either configuration.
- The offline panel has categories, recent choices, supported skin-tone variants and an ABC return button. It inserts complete Unicode sequences without added whitespace; Backspace removes a known complete emoji sequence. Recent choices are bounded at 40 and hidden and not recorded in sensitive fields.
- Unicode 17.0 data and its license are bundled; the device provides glyphs. Gboard remains a behavior reference only. Predictive emoji and next-word suggestions are outside this follow-up.

## Automated checks

`testDebugUnitTest`, `assembleDebug` and `lintDebug` pass on the final source. All 335 unit tests pass. Lint reports zero errors, 14 warnings and two hints. New coverage exercises fixed resize edges, movement bounds, old placement records, every available layout's bottom-key preference, complete Unicode catalog coverage, skin-tone grouping, unsupported-glyph filtering, bounded history and whole-sequence deletion.

The source privacy check with the private denylist passes. Media and build outputs remain outside version control.

## Emulator interactions

Actual Android 15 touchscreen interactions verified:

- Opening the emoji panel, selecting a category and inserting a face.
- Holding a waving hand, selecting a skin tone, and seeing the exact two-emoji editor result. One Backspace removed the entire toned hand while retaining the preceding face.
- Recent choices, ABC return, and Android Back closing the panel before the keyboard.
- Normal-mode right-edge resizing with the left edge and top unchanged, horizontal movement, left-edge resizing with the right edge unchanged, and Reset.
- Emoji inside a floating frame; Space's language/layout chooser and its system-picker route; replacing the emoji key with the language key in Settings.

These interactions were performed before the final comma long-press addition. Its layout coverage passes in the final unit-test run. After installing the final APK, Android switched the selected input method. The automation helper failed twice while reading Android's input-method chooser, including the advised retry. Device interaction stopped there, so comma long-press, persistence after a fresh process, and a further rotation check are not claimed as verified on the final APK. Earlier placement-track rotation checks remain recorded separately in `keyboard-layout-reference.md`.

The prior demo delivery predates this follow-up. No release, new transfer, or physical-device installation was performed for these changes.

## Demo feedback: direct Android keyboard picker

After testing demo build 50, the user requested removal of the intermediate language/layout list. Holding Space now calls the same Android input-method picker previously reached through **Other keyboards**. The custom chooser, its callbacks and its state were removed. Lowkey layout management stays in Settings, and tapping the optional language key still cycles enabled Lowkey layouts. Emoji Back handling remains separate from Android's picker.

The updated source passes all 335 unit tests, `assembleDebug`, `lintDebug` and the private-denylist source check. No additional unit test was added for the direct forwarding call. An actual device check was attempted, but the emulator connection could not be opened after a connectivity retry. This revision's system-picker interaction is therefore not claimed as device-verified. An updated demo is prepared for user testing before release.
