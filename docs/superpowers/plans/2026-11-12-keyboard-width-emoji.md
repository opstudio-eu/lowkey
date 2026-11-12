# Keyboard width and emoji implementation

Approved scope: ../specs/2026-11-12-keyboard-width-emoji-design.md. Implement in this existing feature worktree, without additional agents.

1. Add failing placement tests for both width edges, normal horizontal movement, clamping and legacy codec records. Extend KeyboardPlacement and PlacementCodec; preserve existing profiles and dimensions. Run unit tests.
2. Bundle pinned Unicode 17.0 emoji-test data and its license. Add EmojiCatalog (categories, complete sequences, variants, glyph filtering) and EmojiHistory (bounded unique recents). Test the real catalog, variants and history policy before connecting UI.
3. Add a default Emoji/optional Language bottom-key preference and update the shared layouts. Wire a panel into KeyboardRoot and IME actions, with categories, recents, long-press variants and ABC. Load the catalog off the main thread, retain editor ownership and use existing insertion logic. Clarify the Space chooser heading and routes.
4. Add normal/floating width handles and normal horizontal movement to PlacementFrame. Update docs and notices. Run testDebugUnitTest, assembleDebug and lintDebug on stable sources; verify normal sizing, emoji insertion/variants/recents, Space chooser, alternative language-key setting, Back and rotation on the emulator. Run privacy checks and commit verified work.

Review focus: legacy full-width defaults; fixed opposite resize edge; no broken Unicode sequence; no recent-choice recording in sensitive fields; no popup outside a narrow/floating frame; no editor writes after a session change. Implementation choices are resolved locally within the approved scope.

## Execution record

Steps 1–4 are implemented. Final `testDebugUnitTest`, `assembleDebug` and `lintDebug` pass with 335 tests and zero lint errors. The private-denylist source check passes. Actual emulator interactions cover both width edges, movement/reset, emoji insertion and variants, whole-sequence deletion, recent choices, ABC/Back, floating layout and the alternate language key.

The last comma long-press addition has passing layout tests; its touchscreen check and final restart/rotation checks were blocked by the device helper failing twice in Android's input-method chooser. The verified scope and this limitation are recorded in `docs/demos/keyboard-width-emoji-verification.md`. Predictive suggestions and the parallel app redesign are not part of this change.
