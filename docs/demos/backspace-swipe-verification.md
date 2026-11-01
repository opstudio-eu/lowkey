# Backspace swipe verification, 0.4.7

The clear-all confirmation from 0.4.6 is replaced by a small, non-interactive hint above Backspace. It occupies no extra keyboard height and creates no window, scrim, focus change, or confirmation buttons.

Press Backspace to delete one character. Hold to repeat after 400 ms and accelerate after one second. The hint reads "↑ Clear all". Move above the key's top edge by 12 dp, within the key's horizontal corridor, to arm clear-all. The hint changes to "Release to clear" and repeat deletion pauses. Release there to clear the entire field. Slide back onto the key to disarm and resume normal deletion. Every release or cancellation hides the hint. A second finger cancels the gesture.

Clear-all uses the editor captured on the initial press. Changing input connection or field metadata, hiding the keyboard, or switching to voice prevents it from clearing another editor. TalkBack exposes an explicit "Clear all text" action. Editors that reject Select all get feedback rather than a truncated surrounding-text fallback.

## Automated checks

`testDebugUnitTest`, `assembleDebug`, and `lintDebug` pass. There are 111 unit tests with no failures or errors. Lint reports zero errors and two warnings in unchanged code: `ConfigurationScreenWidthHeight` in the keyboard lab and `ChromeOsAbiSupport` in the dynamic ABI configuration.

Tests cover continued accelerated repetition beyond two seconds, pausing and resuming while armed, cancellation, upward release, returning to the key, sideways release, invalid editor binding, and whole-field clearing rather than a truncated text snapshot. Existing dictation completion tests still pass.

## Device checks

T3's running Pixel API 36 emulator, serial `emulator-5554`, was reused and opened in the thread's Device panel. The pinned agent-device CLI drove the real IME and Keyboard lab. No second emulator was started.

- A 145-character multiline field was entered with saved-term key taps and Enter.
- Tapping Backspace removed one character.
- Holding for 2.6 seconds left 104 characters and opened no confirmation dialog. The hint was absent after release.
- Swiping up from Backspace and releasing cleared the entire field, showed "All text cleared", and removed the hint.
- A held upward gesture displayed the small "Release to clear" hint and paused deletion while held.
- Returning to Backspace before release preserved the remaining text, resumed deletion, and removed the hint on release.

The CLI supports straight pan/swipe gestures. Android's `input motionevent DOWN/MOVE/UP` supplied the continuous up-and-back path it cannot express. Screenshots and editor values were inspected with the pinned CLI. The keyboard was left open for inspection.

Screenshots and APKs are served privately over Tailscale; build outputs and screenshots are not committed.
