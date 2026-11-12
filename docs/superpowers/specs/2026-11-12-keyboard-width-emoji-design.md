# Keyboard width and emoji follow-up

The user approved the four-point proposal in conversation: horizontal sizing in the normal docked mode, a clearer long-press Space chooser, an emoji key by default with an optional language key, and a compact emoji panel with categories, recent choices and skin-tone variants.

## Behavior

- Normal mode gains independent left/right resize handles and horizontal movement while remaining docked. Height and width do not change each other. Save its width and relative horizontal position per existing screen profile. Floating also gets both width handles; one-handed keeps its outer-side anchor. Split retains its full-width outer frame and adjustable middle gap.
- Space remains a space on tap. After testing the demo, the user requested that holding it open Android's keyboard-app picker directly, without the intermediate Languages & layouts panel. No separate language key is required for this route. Lowkey layout management remains in Settings; tapping the optional language key still cycles through enabled Lowkey layouts.
- The key beside Space defaults to Emoji. A Keyboard setting can replace it with the existing language key. Preserve long-press Space in either configuration.
- Keep emoji accessible by holding the comma when the language key is selected (also available with the emoji key). This is a local interaction choice to avoid making the two features mutually exclusive.
- Emoji opens inside the current keyboard frame; ABC returns to letters. Show category tabs and a scrollable grid, including a recent category. Holding an emoji with variants offers its supported skin tones. Insert one complete Unicode sequence, without added whitespace. Remember at most 40 distinct recent choices locally; do not record choices in sensitive fields.
- Use Unicode 17.0's official emoji-test data, under Unicode License V3, with notices. Android supplies the artwork. Filter unsupported glyphs on the device. No new network service or runtime library, and no Gboard code or assets.
- Preserve frame anchoring, number-row height, editor ownership, text, voice access, and Back behavior. Close transient panels on editor changes. Returning from emoji must not change mode or geometry.

## Approach

Reuse the placement model, key renderer, settings store and IME callbacks. Extend the versioned placement codec with defaults for old records. Keep emoji parsing and recent-choice policy in pure Kotlin; load and filter the bundled catalog off the main thread. Render one panel within the existing frame rather than adding a separate window. This keeps touch regions and narrow-screen behavior aligned with the keyboard.

Exact category visuals and data plumbing are implementation choices. The approved logo and the parallel app-UX worktree remain unchanged. Test geometry/migration and Unicode behavior in unit tests, then exercise the actual controls and insertion on the emulator. No release or automatic transfer is part of this follow-up.
