# Custom keyboard theme: design

Accepted scope for an optional custom keyboard theme: five seed colours, an advanced override for
every other colour role, and an optional background image. The app's own appearance (System,
Light, Dark) is unchanged. No fonts, key shapes, import/export, named themes or day/night variants.

## Colours

### Seeds

| Seed | Used for |
| --- | --- |
| Background | Keyboard background, strip, voice row, minimized bar; the image's fallback and dimming colour |
| Keys | Letter keys |
| Function keys | Shift, Backspace, globe, symbols and other modifier keys |
| Text | Key labels, suggestions, icons |
| Accent | Enter key, current-item checks, progress, active alternatives |

### Roles and overrides

`ColorRole` names every field of `KeyboardColors`, with a group and a label for the editor, and
marks the five that are seeds. `keyboardColorsOf { role -> … }` builds a palette from a role lookup;
it calls the constructor with every parameter named and none defaulted, so a new field does not
compile until it has a role, and a unit test checks that every role reads back the field it wrote.

The roles `keyShadow`, `tagBorder`, `tagText`, `noteBg` and `noteText` were never drawn and are
removed, so every listed role changes something visible.

A custom theme is `CustomKeyboardTheme(seed, overrides, image, dim)`. `overrides` maps non-seed
roles to opaque colours. `effectiveColors()` is the single source of truth: it derives every role
from the seeds, then replaces each overridden role and nothing else. Derived roles follow the
seeds, not other overrides. Status colours (green, amber, danger) are derived as before and can be
overridden like any other role; their labels keep naming their meaning.

`resolveKeyboardColors(theme, appDark)` returns `effectiveColors()` while custom is on, else the
standard palette. The keyboard, the editor preview and the contrast checks all use it.

### Derivation

- Surfaces (divider, pill, popup, popup border, busy, voice toggle, chip divider) are mixes of background, keys and text.
- Pressed and active states (`keyPressed`, `keyOn`, `enterPressed`, `stopPressed`) mix towards the ink they carry, but only as far as the ink stays readable (4.5:1, or the base pair's own ratio if that is already lower).
- Secondary text (`muted`, `spaceText`, `chipText`) blends text towards the background only as far as it stays 4.5:1 on every surface it is drawn on.
- Ink on the accent, Stop and danger fills is black or white, whichever contrasts more.
- Status colours come from the standard light or dark palette, whichever reads better on the custom background and pill.
- Light or dark is decided by comparing the contrast of white and black, never a 0.5 luminance threshold.
- The five seeds of a standard palette return that palette unchanged.

All palette roles are opaque; contrast functions reject transparent colours.

## Background image

- Chosen with the system photo picker (`PickVisualMedia`; Android's document picker where the photo picker is unavailable). No storage permission, no network.
- Imported on a background thread: bounds are read first and the image is decoded with a power-of-two sample size, so a large photo never decodes at full size. EXIF orientation is applied, the result is scaled to at most 1440 px on the long side and about 1.5 megapixels, and re-encoded (JPEG, or PNG when it has transparency) into app-private storage. Nothing of the source (URI, name, EXIF, location) is kept.
- Files are immutable and versioned (`bg-<random>.jpg`), written to a temporary name and renamed when complete. Preferences only ever point at a complete file.
- Unreadable files, unsupported formats and images over 100 megapixels show an error in the editor; the draft and the saved theme stay as they were.
- Drawn behind the whole keyboard, centre-cropped to its current size, in keys, voice and minimized mode, then covered by the background colour at the chosen dimming (0 to 90 %, default 50 %). Keys, popups and buttons stay opaque. The preview uses the same drawing code.
- A missing or corrupt file falls back to the background colour.
- Decoding happens once per file on an IO thread into a one-entry cache shared by the keyboard and the editor; recomposition never touches the disk.

## Contrast checks

`keyboardContrastChecks(colors, tone)` lists every foreground/surface pair the keyboard draws: text
4.5:1, icons and indicators 3:1. Each check names its roles.

Without an image, pairs on the background use the background colour. With an image, `ImageTone`
holds the luminance range of the dimmed image, measured over a small sample of the whole image
(a superset of any crop). Pairs drawn directly on the background are checked against the worst
point of that range and labelled "over the image"; fine detail below the sample size is not
measured, which the editor says. Pairs on keys, popups and buttons use their solid colours.

"Fix contrast" moves only seed colours, toward black or white, text first, and evaluates the full
effective theme (overrides and image included). Overrides are never changed: failures that involve
an overridden role are marked "Advanced" and stay listed. The button says "Fix contrast" only when
the result has no failures; otherwise "Improve contrast", or nothing without an improvement. With
an image, the editor also offers the smallest dimming that makes the over-image pairs pass, if one
exists, as a separate, named change.

## Persistence

`KeyboardThemeStore` uses its own SharedPreferences file (`keyboard_theme`): `custom_enabled`,
`custom_colors` (five seeds, unchanged format), and new keys `custom_overrides`
(`role=RRGGBB` pairs; unknown roles and invalid colours are dropped), `background_image` (a file
name in the store's directory, validated) and `background_dim`. Older saved themes load with no
overrides and no image. Turning custom off keeps everything. Saving writes all keys in one commit.

Cleanup deletes only files that the saved theme and the open draft do not reference: after Save,
Cancel or replacing a draft image, and at start for unreferenced files older than a day (so a draft
survives process recreation).

## Settings

Settings → Appearance → Keyboard theme:

- Keyboard colors: Standard / Custom. Switching takes effect immediately and keeps the saved theme.
- With Custom on: live preview, the five seed swatches with the RGB/hex picker, Background image
  (Choose, Replace, Remove, Dimming slider), Advanced colors (grouped roles, each Automatic or
  overridden, with its own picker and Reset to automatic), contrast warnings and Restore default,
  Cancel, Save.
- Everything is a draft until Save: seeds, overrides and image. Cancel, Back or leaving the page
  discards it; the saved image is never deleted by a draft action. The draft survives rotation and
  process recreation (`rememberSaveable`); a draft image file that has gone missing shows as no image.
- Restore default resets the draft's seeds to the standard palette and clears overrides; the image
  stays until removed.
- The hex field accepts exactly six hex digits; anything else stays visible as an error and changes nothing.

## Keyboard

`KeyboardUiState` carries the resolved colours and the background image (bitmap, dimming).
`KeyboardRoot` draws the background with `keyboardBackground(colors, image)`; strip items that
used to paint the background colour now paint nothing when not pressed, so the image shows.

## Navigation bar

Before Android 15 the bar colour is set to the background colour and icon colour follows it, as
before. From Android 15 the bar is transparent over the keyboard: with an image, icons are light
or dark by the worst case over the dimmed image's luminance range, never by an average.
