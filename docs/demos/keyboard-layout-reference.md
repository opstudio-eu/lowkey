# Keyboard layout reference inspection

Gboard was inspected directly on an Android 15 emulator in phone and tablet-sized configurations. It was already installed: its settings open through Android's on-screen keyboard list, while the launcher-only app command cannot resolve its settings activity. The automation's headless test IME must be disabled to observe a real keyboard.

Observed resize behavior:

- A border with independent top and bottom handles.
- Central Reset, Move and Done controls.
- Raising the top increases height without moving the bottom.
- Raising the bottom reduces height and creates space beneath the keyboard without moving the top.
- Central movement shifts the panel without resizing its keys.
- Back hides the keyboard and retains completed changes when it reopens.
- Reset followed by Done restores the original geometry.

Observed tablet split behavior, confirmed in portrait and landscape:

- QWERTY rows are `qwert | yuiop`, `asdfg | ghjkl`, and `Shift zxcv | vbnm Backspace`.
- G and V are duplicated across the middle so either thumb can reach them.
- The bottom row remains continuous with a broad central spacebar.
- The upper halves stay near the outer edges rather than stretching keys across the central gap.

This is a behavior reference, not imported code or artwork. Captures remain outside version control. A tablet-sized emulator configuration does not verify a physical folding hinge.

## Lowkey verification

Android 15 emulator interactions covered phone and tablet-sized layouts:

- Top and bottom resize handles, completion, persistence after reopening, and a raised docked position.
- Both one-handed sides, text entry, floating movement, and tapping the application's Copy button outside the floating panel followed by pasting the copied text.
- Landscape and portrait split layouts with shared G/V keys and one continuous spacebar. Tapping both copies produced `ggvv`; space and keys at both outer edges inserted text normally.
- Automatic splitting on a large screen, a manual Normal choice surviving rotation, and a manual Split choice surviving disabling automatic splitting.
- Entry through Settings → Size and position opened the resize controls in keys mode, without starting voice input or changing the existing text.
- Switching a floating panel to the voice row retained its position; no speech model was downloaded and no recording was made.
- The number-row setting defaulted off. Enabling it added ten digits in Normal, One-handed, Floating and Split. Taps produced `19020`; symbol switching retained the same top edge. Disabling it removed the extra row, and enabling it survived an app restart after reinstalling the same build.
- Five-row floating resizing kept the opposite edge fixed. The split-gap handle could be dragged independently of the central Done control. Rotation closed layout editing, and letter keys were absent from the accessibility tree while the resize controls were open.

Unit coverage additionally verifies layout character preservation across every bundled language/arrangement and symbol layer, preference migration and reset, automatic/manual policy, short-window constraints, and fixed opposite edges during resizing, including at geometry limits.

Final checks: 327 unit tests passed; the debug build, lint and private-denylist leak check passed. No new runtime dependencies or artwork changes were introduced.

Physical folding hinges, OEM-specific input-method windows, split-screen app combinations, alternative system navigation modes, and recording/transcription with a loaded speech model were not exercised. The emulator's original screen density and portrait orientation were restored after the tablet comparison.
