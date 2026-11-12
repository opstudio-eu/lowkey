# Custom keyboard theme: design

Accepted scope for an optional custom keyboard palette. The app's own appearance (System, Light, Dark) is unchanged.

## What the user controls

One custom palette with five opaque colours:

| Seed | Used for |
| --- | --- |
| Background | Keyboard background, strip, voice row, minimized bar |
| Keys | Letter keys |
| Function keys | Shift, Backspace, globe, symbols and other modifier keys |
| Text | Key labels, suggestions, icons |
| Accent | Enter key, current-item checks, progress, active alternatives |

No photos, fonts, shapes, import/export, multiple themes or day/night variants.

## Derived roles

`deriveKeyboardColors(seed)` fills every `KeyboardColors` role from the five seeds:

- Surfaces (divider, pill, popup, popup border, busy, voice toggle, chip divider) are mixes of background, keys and text.
- Pressed and active states (`keyPressed`, `keyOn`, `enterPressed`, `stopPressed`) mix towards the ink they carry, but only as far as the ink stays readable (4.5:1, or the base pair's own ratio if that is already lower).
- Secondary text (`muted`, `spaceText`, `chipText`, `tagText`) blends text towards the background only as far as it stays 4.5:1 on every surface it is drawn on.
- Ink on the accent, Stop and danger fills is black or white, whichever contrasts more.
- Status colours (green, amber, danger) come from the standard light or dark palette, whichever reads better on the custom background and pill.
- Light or dark is never decided with a 0.5 luminance threshold: it compares the contrast of white and black against the colour.
- The five seeds of a standard palette return that palette unchanged, so a fresh custom theme looks exactly like the standard one.

## Contrast checks

`keyboardContrastChecks(colors)` lists every foreground/surface pair the keyboard draws (the same list the unit test guards for the standard palettes): text 4.5:1, icons and indicators 3:1. The editor shows failing pairs with their ratios. A custom palette may be saved with failures; the warning stays visible in the editor.

"Fix contrast" runs a small search that moves seed colours toward black or white, text first, until no pair fails. The button says "Fix contrast" only when the result has no failures; if it improves but cannot resolve every pair it says "Improve contrast" and the remaining pairs stay listed. Without an improvement there is no button.

## Persistence

`KeyboardThemeStore` uses its own SharedPreferences file (`keyboard_theme`): an enabled flag and the five seeds as `#RRGGBB`. It is separate from `Settings.theme`; `ThemeMode` gets no new value. Turning custom off keeps the saved seeds; the keyboard then uses the standard palette for the app theme.

## Settings

Settings → Appearance → Keyboard theme opens its own page:

- Keyboard colors: Standard / Custom. Switching takes effect immediately. The first switch to Custom saves the current standard palette's seeds.
- With Custom on: a live preview, five swatches, a colour picker with Red/Green/Blue sliders and a six-digit hex field (no alpha; invalid input shows an error and changes nothing; sliders update the field, a valid field updates the sliders), contrast warnings and actions Restore default, Cancel and Save.
- The draft does not change the keyboard until Save. Cancel, Back or leaving the page discards it. Restore default sets the draft to the current standard palette's seeds; it does not save or switch to Standard.
- Test keyboard (top bar) opens the real keyboard with the saved palette.

## Keyboard

`KeyboardUiState` carries the resolved `KeyboardColors` instead of a dark flag. Everything reads `LocalKeyboardColors`: keys, suggestions, alternative-character popups, clipboard, voice row, model picker, minimized bar, keyboard chooser and pressed states.

The IME window's navigation bar icons are light when white contrasts more with the palette background than black. The effect is keyed on the background colour, so it updates when a custom background changes without changing light/dark. Before Android 15 the navigation bar colour is still set to the background.
