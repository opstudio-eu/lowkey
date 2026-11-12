# Minimized bar bottom anchoring

The minimized bar now uses the bottom of the usable IME viewport rather than the full keyboard's saved bottom space or floating Y position. Its horizontal position and width stay unchanged. The floating drag handle is omitted while minimized so it cannot leave space below the bar. Expanding restores the saved keyboard geometry; no preferences are rewritten.

## Automated regression coverage

`KeyboardPlacementTest` covers:

- Normal, one-handed, floating and split modes in compact/large portrait and landscape viewports, with four and five key rows. Narrow split layouts still fall back to Normal.
- The bottom edge and 49 dp compact height, including omission of the floating handle's 24 dp space.
- Left/right one-handed anchoring and preservation of the other modes' horizontal geometry.
- Repeated minimize/expand calculations and persistence round trips without changing saved geometry.
- Viewports excluding 0, 24 and 48 dp navigation insets, and a viewport shorter than the compact bar.

All four added tests failed against the previous geometry calculation before the fix. The complete unit suite passes: 347 tests, no failures or skipped tests.

`./gradlew --no-daemon testDebugUnitTest assembleDebug lintDebug` passes. The source and debug APK privacy checks also pass with the private denylist.

## IME integration review

The service supplies a viewport excluding system bars and cutouts; the UI retains its navigation/caption-bar bottom padding. Both rendering and `onPlacementBounds` use the corrected frame. Docked content/visible insets continue to start at the panel top; floating mode continues to reserve no app content and limits touches to the visible panel. The compact floating touch region excludes the removed drag handle. Expanded and voice-mode geometry retain their previous calculation.

## Remaining device verification

Actual emulator interaction has not been verified: opening a new device session failed because the emulator host could not be reached. No existing device session or physical phone was used. Geometry tests and code review do not establish runtime IME behavior.

When the emulator connection is available, move the full keyboard upward in each mode, minimize it, use Backspace and expand again. Check that the bar meets the navigation inset, the saved full-keyboard position returns, touches outside a floating bar still reach the app, and docked app content resizes to the compact bar. Repeat in portrait/landscape, with the number row on/off and with gesture/three-button navigation.
