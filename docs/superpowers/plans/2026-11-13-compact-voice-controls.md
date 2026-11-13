# Compact voice controls

Implement the selected B concept and mirror its controls in the expanded keyboard.

- Keep an icon-only microphone on the left in both keyboard forms. Compact mode has only microphone and Backspace, with clipboard history between them.
- Swipe up to expand; preserve horizontal clipboard scrolling and the Backspace command fan. Keep an accessible expand action and a one-time hint.
- Long-press the microphone for keyboard visibility, model selection, and voice settings. Opening model selection must not start recording.
- Share the voice row: cancel on the left, status in the middle, Stop and insert on the right. Keep model choice accessible without crowding narrow layouts.
- Preserve model readiness, permission handling, cancellation ownership, and the user's saved minimized preference. Do not alter speech processing or the welcome screen.

## Implementation and checks

1. Add gesture regression tests covering direction, threshold, and horizontal movement.
2. Implement the shared microphone/menu, compact gesture/hint, and consistent expanded header.
3. Reuse the existing voice controller and test readiness, cancellation, and completion through the existing unit suite.
4. Run `testDebugUnitTest`, `assembleDebug`, and `lintDebug` with JDK 21.
5. Install only the separate UI preview package. Verify model selection, permissions, recording, stop/cancel, and both keyboard forms via actual emulator interaction. Do not publish.

## Review focus

- A swipe must not also start recording or paste a clip.
- Horizontal scrolling and Backspace gestures must remain unchanged.
- Opening the model picker from keys must never auto-record.
- No-model and no-permission controls must remain reachable.
- Stop/cancel and model access must fit narrow keyboards, including floating mode.

## Progress

- Existing missing-entry-point bug traced to `MinimizedBar`; preliminary microphone addition is being replaced by the selected design.
- Implemented shared controls, gesture handling, persistent expand hint, and readiness refresh in both keyboard forms.
- Review corrections: preserve horizontal scrolling before the expand threshold; close the tools panel before model selection; keep popup focus with the IME and position menus above its short window.
- Validation: 503 unit tests passed; debug APK build and lint passed (no lint errors).
- Emulator: reproduced the original missing compact microphone, installed the separate preview build, downloaded Whisper tiny, checked the missing-permission route, microphone recording/stop, local transcription with bundled debug audio, cancellation retaining earlier text, model picker without auto-recording, both resize gestures, clipboard scrolling, and model selection from the tools panel.
- Restored microphone input after the debug-audio test and left the preview ready in compact mode. No release or pull request was created.

## Follow-up: shared quick settings

- Removed the expanded header's tools and minimize buttons; both keyboard forms now show the same swipe grip.
- Long-pressing the microphone toggles the existing quick-settings panel. Keyboard visibility, voice model, and voice settings are included there; the separate voice popup was removed.
- Quick settings remember whether they were opened from the compact or expanded keyboard. The model picker opens without recording, including when quick settings are opened from idle voice mode.
- Verified these transitions, the new settings tiles, swipe gestures, and short-tap recording on the emulator. All 503 unit tests, debug build, and lint passed again (no lint errors).

## Follow-up: plain icons

- Removed button backgrounds and borders from the microphone and Backspace in both keyboard forms and from the recording cancel/accept controls. Accept now uses a checkmark, retaining its stop-and-insert action and accessibility label.
- Installed the updated preview and verified compact recording/accept, expanded typing/deletion, recording/cancel returning to the expanded keyboard, and long-press quick settings on the emulator with Whisper tiny.
- All 503 unit tests and the debug build passed; lint reported no errors (13 existing warnings and two hints).
