# Custom keyboard theme: device verification

Status of the custom theme, advanced colours and background image on real Android builds.
Plan: [2026-11-12-custom-keyboard-theme.md](2026-11-12-custom-keyboard-theme.md).

Everything except the addendum was verified on the branch **before** it was merged with the new
placement, emoji and settings work from `main` (Lowkey 0.8.2). Those results do not cover the
resizable, one-handed, split or floating keyboard, the emoji panel, the tools menu or the new settings
pages; see [Addendum: after merging main](#addendum-after-merging-main).

## Setup

- Debug build of this branch installed under a separate test application ID, so existing
  installations were not replaced. Its keyboard was the active input method during the tests.
- Android 15 emulator (Pixel 6 profile, dark system theme, gesture navigation).
- Android 14 emulator (Pixel 6 profile, Google APIs image, light system theme, gesture navigation),
  created for this run.
- Images were synthetic fixtures generated for the test (gradients, labels and marker blocks); no
  personal photos. They were chosen through the system photo picker.
- Screenshots and recordings were kept outside the repository.

"Device" below means the action was done in the UI and its result was seen on screen, in the editor
and, where stated, in the real keyboard. "Automated" means unit tests only.

## Results

### Advanced colours (Android 15, device)

| Check | Result |
| --- | --- |
| Reset to automatic on an overridden role (Space bar label) | Pass: row returns to Automatic with the derived colour; preview and real keyboard show the default label colour after Save |
| Cancel after a reset | Pass: the override returns |
| Overrides in several groups (Suggestions, Popups, Pressed keys) saved | Pass: only those roles change in the real keyboard |
| Pressed keys override | Pass: held key drawn in the override colour (recording) |
| Popups override | Pass: accent popup, keyboard chooser and model picker use it |
| Low-contrast warnings | Pass: listed per pair and marked "(advanced color)"; Save still allowed |
| Improve contrast with failing overrides | Pass: moves seed colours only, overrides unchanged, failures reduced (12 to 7) and "No automatic fix found" shown |
| Standard, then Custom | Pass: Standard shows the standard palette without image in the real keyboard; Custom brings back image and overrides |
| Every role maps to its own field and reads back | Automated (`CustomKeyboardThemeTest`) |

### Keyboard surfaces (Android 15, device)

| Surface | Result |
| --- | --- |
| Accent popup | Pass: long press on `e` opens the popup with `3` selected; sliding onto `é` and releasing inserts `é` (recording) |
| Pressed key state | Pass (recording) |
| Clipboard chip in minimized bar | Pass after fix, see below |
| Keyboard chooser (long press space) | Pass: popup colour, current layout check mark |
| Model picker (no model installed) | Pass: popup colour, empty state, open Model button in pressed colour |
| Minimized bar and voice row | Pass |
| Suggestions strip | Pass |
| Navigation bar area | Pass: image continues under the gesture area, icons readable |
| Landscape keyboard | Pass: image centre-cropped to the wider keyboard |

### Background image (Android 15, device)

| Check | Result |
| --- | --- |
| PNG with transparency | Pass: transparent areas show the background colour; half-transparent band blended; preview and real keyboard |
| Landscape JPEG | Pass |
| EXIF orientations 3, 5 (transpose, mirrored), 6, 8 | Pass: stored copy upright and not mirrored, EXIF removed (checked in preview and in the stored file) |
| 48 MP JPEG | Pass: imported and stored at 1414 × 1061 |
| Over 100 MP PNG | Pass: "That image is too large. Choose one under 100 megapixels."; no file kept |
| Corrupt file with a `.jpg` name | Pass: the photo picker offers it; import shows "That file isn't an image Lowkey can read…"; no file kept |
| Replace, then Cancel | Pass: saved image and colours return; unused imported files deleted |
| Replace, then Save | Pass: new image used; the previously saved file deleted |
| Remove, then Save | Pass: image reference removed, colours and overrides kept |
| Draft image across rotation (activity recreation) | Pass |
| Draft image across process death (process killed in the background, task restored from recents) | Pass: draft image and "Unsaved changes" restored |
| Force stop | Pass: draft discarded, saved theme shown (expected; Android also switches away from a force-stopped keyboard) |
| Saved image file missing (deleted from app storage, process restarted) | Pass: editor preview and real keyboard fall back to the background colour; no crash in the log |

### Android 14 (device)

| Check | Result |
| --- | --- |
| Custom from the light standard palette, EXIF 6 image chosen and saved | Pass: upright |
| Real keyboard, minimized bar and full keys | Pass: image behind opaque keys |
| Navigation bar (pre-Android 15 path) | Pass: solid palette background with dark icons; the image is not drawn behind it (by design) |

## Bug found and fixed

Clipboard chips (strip clipboard button and the minimized bar's copied clips) draw the Suggestions
colour on the key colour, but the contrast checks had no such pair. With a Suggestions override near
the key colour, a copied clip in the minimized bar was invisible and the editor gave no warning. The
pair "Clipboard text on clipboard chips" was added, with a regression test; on the device the editor
then listed it (1.0:1).

## Not verified on a device

| Item | Why | Coverage |
| --- | --- | --- |
| Save error (preferences commit fails) | No safe, reversible way to make app storage fail on the emulator | Automated: `KeyboardThemeStoreTest` (fake persistence) |
| Controls disabled during an import, rotation during an import | Imports of the fixtures finished in about a second; no way to hold one open | Automated for import/switch races in `KeyboardThemeStoreTest` |
| Every colour role individually | Roles in the same group draw the same way; representative roles per group were checked | Automated mapping and override tests |
| Speech transcription | No model downloaded; not part of theming | — |
| Android 14 with three-button navigation, advanced colours and popups on Android 14 | Only basic image and colour checks were run on Android 14 | — |

## Observations, not changed

- After the saved image file goes missing, the editor still shows Replace/Remove image without saying
  the file is gone; the preview shows the plain colour.
- In landscape, the inset beside the keyboard (display cutout side) shows the background colour, not
  the image.

## Checks

Before the merge with `main`: `./gradlew testDebugUnitTest` (374 tests, none failing or skipped), `assembleDebug`, `lintDebug`
and the leak check pass.

## Addendum: after merging main

The branch was merged with `main` at Lowkey 0.8.2 (resizable and floating placement, one-handed and
split layouts, emoji panel, number row, tools menu, page-based settings). Integration choices:

- The placement frame and the keyboard content both draw with the resolved palette from
  `KeyboardUiState.colors`; nothing in the new wrappers picks the standard light or dark palette.
- Docked, the frame paints the background colour and image once, across its full width and under
  the navigation bar; the keyboard content then draws no background of its own, so the image is not
  cropped twice or covered. Floating, only the floating panel (with its drag handle) is painted and
  the area around it stays transparent.
- Unselected emoji category tabs are transparent, like suggestion chips, so an image shows through.
- The navigation bar style comes from `keyboardNavigationBarStyle`: docked, as before the merge;
  floating, the bar is transparent and the icons follow the keyboard colour, since no image is under
  the bar.
- Keyboard theme opens from **Settings → Appearance**, and Back returns there.

Device checks after the merge (Android 15 emulator, separate test application ID, saved custom theme
with a dark wine background and three advanced colours):

| Check | Result |
| --- | --- |
| Settings → Appearance summary and Keyboard theme link; Back from the theme page | Device: summary says custom keyboard colours, link opens the page, Back returns to Appearance |
| Theme page with the new radio rows; edit, Save | Device: preview follows the draft; Save stores it and disables Save and Cancel |
| Docked keyboard with the custom colours | Device: keys, strip and the area under the navigation bar use the custom colours; light navigation icons |
| Emoji panel | Device: custom background, keys and selected tab; other tabs transparent |
| Tools menu | Device: tiles use the custom key colours |
| Floating keyboard, colour and with image | Device: panel and drag handle painted, one image across both; the app shows around it; transparent navigation bar |
| One-handed docked keyboard with image | Device: one image across keys, side controls and navigation area; no opaque band |
| Split layout | Not checked: offered only on wide screens |
| Android 14 (navigation bar painted by the window) and Android 16 | Not checked after the merge; memory on the emulator host allowed one emulator. Covered by unit tests of the navigation bar style only |

After the merge: `./gradlew testDebugUnitTest` (459 tests, none failing or skipped), `assembleDebug`
and `lintDebug` (no new warnings) pass.

The integration review also found that the emoji panel's small ABC and Skin tones labels need
the 4.5:1 text threshold, rather than the 3:1 icon threshold. A separate Emoji labels check now
covers both solid and image backgrounds; its regression test failed before the check was added.
With this correction, the complete unit suite has 460 passing tests, none skipped.
