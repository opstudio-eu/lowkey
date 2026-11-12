# Keyboard layout implementation plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. The user authorized Opus collaboration for design; implementation stays in this session without additional agents.

**Goal:** Implement the approved interactive resizing, positioning, one-handed, floating and split keyboard design.

**Architecture:** A pure layout model owns profile selection, mode policy and constrained geometry. The existing Compose renderer consumes it; the IME service reports matching content/touch regions. Persist preferences through the existing settings store.

**Tech stack:** Kotlin, Jetpack Compose, Android InputMethodService, SharedPreferences, JUnit. No new dependencies.

**Spec:** ../specs/2026-11-12-keyboard-layout-design.md (approved in conversation).

## Global constraints

- Android 9+, JDK 21, existing Gradle wrapper; preserve approved artwork and package identity.
- Gboard is only a functional reference. Do not import its code or assets.
- Keep all private environment information and test media out of git.
- Run `./gradlew testDebugUnitTest` for every change; verify UI through the available device tools.

## Review focus

- Manual mode choices must survive profile changes and automatic-split toggles.
- Malformed saved numbers and smaller windows must never place the keyboard off screen.
- Split must preserve all character outputs in every existing arrangement and symbols layer.
- Floating must pass new touches outside its panel through to the app.
- Resize entry must never auto-start voice or alter editor text.

## Task 1: Preferences and geometry

Files: create `keyboard/KeyboardPlacement.kt`, `keyboard/KeyboardPlacementCodec.kt`; modify `settings/Settings.kt` and `SettingsStore.kt`; test `keyboard/KeyboardPlacementTest.kt`.

Interfaces: `PlacementMode`, `PlacementProfile`, `KeyboardPlacement`, `PlacementPreferences`; pure profile selection, automatic/manual selection and clamped frame calculation. Store shared docked geometry separately from floating geometry. Codec accepts malformed or older data without throwing; legacy row height seeds defaults.

- [x] Write failing tests for profile selection, automatic/manual precedence, clamping and codec round trips/corruption. Interactive edit cancellation belongs to Task 3.
- [x] Run the focused test task and confirm the missing-feature failure.
- [x] Implement model, codec and settings persistence; run the full unit suite.
- [x] Commit the independently tested foundation.

## Task 2: Split rows

Files: create `keyboard/SplitLayout.kt`; modify `keyboard/ui/KeysPanel.kt`; test `keyboard/SplitLayoutTest.kt`.

Interfaces: `splitRows(layout, keyboard, layer): List<SplitRow>`; renderer receives optional gap in dp. Following the user's Gboard ergonomics request and direct inspection, repeat the inner letter of rows two/three (G/V on QWERTY) and keep one broad, continuous bottom-row spacebar. Use a common character-key unit across the upper halves, with fixed outer modifiers.

- [x] Write failing tests for QWERTY/Dvorak boundaries and preservation of character outputs for all supported arrangements and symbol layers.
- [x] Implement the transformation and renderer; run the full suite.

## Task 3: Quick menu and interactive frame

Files: create `keyboard/ui/PlacementFrame.kt`, `keyboard/ui/KeyboardTools.kt`; modify `KeyboardRoot.kt`, `KeyboardUiModel.kt`, `ui/SlimIcons.kt`.

Interfaces: layout preferences/profile flow into `KeyboardUiState`; `KeyboardActions` accepts saved placement, manual mode and frame updates. Frame reports bounds in window coordinates. UI keeps a draft during each drag and saves on release; Done closes the controls and Back hides the keyboard while retaining completed changes, matching direct Gboard inspection. Gesture cancellation or editor/window changes discard an unfinished drag. Accessible actions accompany handles.

- [x] Render six tools, active-mode toggles, one-handed side controls and split availability.
- [x] Add resize outline and Reset/Move/Done; normal height/bottom-space, one-handed width, split gap and floating bounds use the pure constraints.
- [x] Keep voice, model picker and minimized bar anchored within the active frame; constrain their contents.
- [x] Run tests and compile debug APK; exercise controls on the emulator.

## Task 4: IME integration and settings entry

Files: modify `ime/SlimKeyboardService.kt`, `settings/SettingsStore.kt`, `companion/SlimApp.kt`, `companion/screens/SettingsScreen.kt`, `companion/screens/KeyboardTestSheet.kt`, popup and voice UI as required.

- [x] Match IME content insets and touch regions to the reported frame; restore docked behavior on exit. Apply transparent navigation styling while floating.
- [x] Cancel editing and stale popups at editor/configuration boundaries; process Back before hiding the IME.
- [x] Replace the three heights with Resize and Reset actions, add automatic-split switch and open resize through the test field with voice auto-start suppressed.
- [x] Run unit tests, debug build and lint.

## Task 5: Device verification and documentation

- [x] Compare available Gboard resize behavior, recording any unavailable reference checks honestly.
- [x] Exercise normal resize, cancel/reset, raised position, both one-handed sides, floating touch-through, split typing and restore after rotation/restart; recheck voice, clipboard and long-press.
- [x] Update README/user guide and add a public-safe verification record. Keep captures in ignored artifacts.
- [x] Run full unit tests, lint and leak checks; review the complete diff and commit verified changes. Do not publish or modify a physical device.

## Additional request: optional number row

The user requested a persistent on/off option for a dedicated number row. Default it off, prepend 1–0 to letter layouts, preserve the letter-row split boundaries, and keep symbol layers at the same panel height. Feed the fifth row through the shared placement and resize calculations without overwriting stored dimensions.

- [x] Add failing coverage for every letter layout, symbols, split boundaries and five-row geometry.
- [x] Implement the preference, settings switch, renderer and geometry integration.
- [x] Verify toggling, typing, mode changes and persistence on the emulator; repeat the full checks.

## Execution record

Design approved. Plan self-reviewed against the spec: all modes, persistence, reversible editing, integration and regression checks have an owning task. Implementation proceeds locally in the existing feature worktree.

Task 1: complete. Six new tests cover policy, bounds, migration, persistence and reset after migration; 314 unit tests pass. The reset test first failed because an unvisited profile inherited the old height after restart; the versioned preference header now preserves the reset base height.

Reference check resolved: Gboard was already installed, but had no launcher activity; Android's on-screen keyboard settings opened it. Disable the headless automation IME to inspect real keyboards. Directly verified resize, move, reset, persistence on Back, and the tablet split layout in portrait/landscape. The user specifically prioritized Gboard's split ergonomics, superseding the initial design without shared keys.

Implementation and verification complete: six-tool menu, interactive frame, IME bounds, settings entry, split renderer and optional number row are implemented. Phone and tablet-sized emulator interactions verified normal top/bottom resize, both one-handed sides, typing, floating movement, clicking Copy outside the floating region, pasting the copied text, portrait/landscape split typing, profile restoration, automatic/manual split preferences and anchored voice mode. The number-row switch, all four modes, stable symbol-layer height and persistence after app restart were verified. Five-row resizing and the split-gap handle were exercised. The original smallest-width setting and portrait orientation were restored.

All 327 unit tests, the debug build, lint and private-denylist leak check passed. Scope and verification limits are recorded in `docs/demos/keyboard-layout-reference.md`. No release has been published.
