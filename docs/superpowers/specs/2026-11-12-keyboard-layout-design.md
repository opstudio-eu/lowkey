# Keyboard size, position and modes

Status: co-design approved in conversation. This document describes agreed target behavior, not shipped features.

## Intent and agreed scope

Make Lowkey comfortable to use on phones, foldables and tablets. The requested features are direct resizing like Gboard, repositioning, left/right one-handed use and a split keyboard. Repositioning includes both a raised docked keyboard and a freely movable floating keyboard. Automatic splitting must be a user setting. A quick menu on the keyboard is the selected entry point.

Gboard is a functional reference only. Do not copy its code, icons or other assets. Keep Lowkey's identity and approved launcher artwork.

The defaults and detailed interaction rules below are implementation proposals, not additional user requests.

## Approaches

Two access patterns were considered: a keyboard quick menu with preferences in the app, and controls available only in app settings. The quick menu was selected: resizing requires seeing the keyboard, and switching modes should not require leaving the current text field.

Use a shared geometry model and the existing key renderer for every mode. Separate keyboard implementations would duplicate typing, accessibility and gesture behavior. Merely scaling the existing view would leave touch areas, popup coordinates and application insets inconsistent with the visible keys.

## Quick menu

Add one clearly labelled keyboard-tools button in the expanded keyboard's top strip. Keep minimize and voice access. Move the current personal-dictionary shortcut into the menu to avoid crowding the suggestion strip.

Opening the menu replaces the key rows within the current keyboard frame, without leaving the editor or changing its text. Back closes the menu before hiding the keyboard. Use six labelled tools, in a responsive grid:

- Size and position.
- One-handed, Floating, and Split.
- Personal dictionary and Keyboard settings.

Mode tools show their active state. Choosing a different mode applies it immediately and returns to typing; choosing the active mode returns to Normal. One-handed opens on the last-used side, right by default; its side controls switch left/right or return to full width. This avoids separate left/right and Normal tiles. Keep Split visible but disabled, with a short explanation, if the available width cannot support it. The minimized bar retains its compact role within the active frame; expanding it restores the selected mode.

Minimize, tools and voice remain reachable in narrow frames. Keep all three suggestions accessible, using a horizontal strip if needed instead of dropping the two alternatives. The menu and model/keyboard choosers must fit the safe area and may scroll; do not expand the floating frame off screen to fit their contents.

## Size and position

Replace Compact/Normal/Tall in settings with a route to the interactive editor in the built-in keyboard test field. This route explicitly starts in keys mode without auto-starting dictation, regardless of the remembered voice mode. The same editor opens directly from the quick menu. Settings also offers **Reset size and position**, an explicit recovery action for all saved geometry, leaving modes and automatic-split preferences intact.

Use Gboard's interaction pattern: an outline with drag handles, live resizing, a move handle, Reset and a confirmation checkmark. Do not substitute a slider or a list of preset sizes. Verify the reference interaction on an available Gboard installation before finalizing handle behavior; public descriptions alone do not establish exact limits or gesture feel.

- In Normal mode, resize handles change height; moving the keyboard changes the space beneath it. Width remains the available width. Confirm the exact top/bottom handle behavior against Gboard rather than inventing a different resize gesture.
- In one-handed mode, width and height are adjustable within the available area. Position stays anchored to the chosen left/right side, with adjustable bottom space. Provide a direct side-switch action and return to Normal.
- In Floating mode, resize handles adjust width and height, and a dedicated handle moves the panel horizontally and vertically. A small drag bar remains usable outside the resize editor. Moving the panel outside the editor saves its position on release; system cancellation restores the position at drag start. It overlays the app; the visible app around it remains interactive. Returning to Normal docks the keyboard again.
- In Split mode, preserve a full-width outer keyboard area, adjust height and bottom space, and allow the central gap to be adjusted. Floating and Split are alternative modes, not a combined floating pair of panels.

Use an edit-session draft with three central controls: Reset, Move and Done (checkmark). Dragging changes the preview; Done saves it. Back, hiding the keyboard, switching editor, rotation or a fold/window change cancels an unfinished edit and restores saved geometry. Reset previews the default geometry for the current mode/profile; Done saves that reset, without an additional confirmation dialog.

Normal, One-handed and Split share height and bottom space within a screen profile. One-handed additionally stores its width and side; Split stores its central gap. Floating has independent size and position. Initial one-handed width is approximately 80% of the available width, constrained by usable keys and a reachable side-control rail. Validate minimum widths against both the key rows and the responsive voice row rather than fixing them from the key rows alone.

During geometry editing, suppress typing, dictation starts and destructive keyboard gestures. Keep all handles and controls within system-safe bounds. Clamp dimensions to usable key sizes and available space, without snapping to the three legacy heights. Add accessible increment/decrement actions for controls that otherwise require dragging. Exact lower bounds are a builder decision to validate on the smallest supported screens and against the reference interaction.

## Split key layout

Place the left and right key groups at opposite sides, separated by a real central gap. Do not stretch individual keys across the gap. Apply the split consistently to letters, numbers and symbols, including every enabled language and alternative arrangement.

Split letter rows at a stable boundary defined by the arrangement, not at the midpoint of the total key weights. Use five letter/character keys on the left for the current conventional arrangements, with an explicit four-key override for Dvorak's third row (which omits the leading semicolon). Thus QWERTY splits as `qwert | yuiop`, `asdfg | hjkl`, `zxcvb | nm`. Verify these boundaries for every existing language layout. For symbol rows, place half of the character keys, rounded up, on the left. Preserve key order and every output character; never split a character key.

Treat the bottom row explicitly: layer switch, globe and comma remain on the left; period and Enter remain on the right, with one space key on each half. Shift and Backspace stay at the outer ends of their row. Both halves use a common character-key width, retain their row staggering and absorb width differences at the inner edge. The gap contains no keys or enlarged key hit areas. Do not duplicate letter keys. The dictionary's logical key-neighbour model stays unchanged; this feature does not retune autocorrection.

Split is manually available when the two halves can meet minimum usable widths. A total width around 480 dp is a starting point to validate, not a guarantee for every alphabet. Disable it with a short explanation in narrower windows. The stored choice survives temporary fallback to Normal. Choose the initial gap to keep character keys comfortably sized on large screens; its final bounds are validated with the widest supported rows.

## Preferences and screen changes

Add **Automatically split on large screens**, default Off. Manual mode switching remains available regardless of this setting.

Store the selected mode and geometry separately for Compact/Large and portrait/landscape. Classify from the potential full-area bounds of the input method's own window, excluding system bars and cutouts, not from the measured keyboard panel height or the host app's activity bounds. Proposed Large eligibility is a shortest available side of at least 600 dp; a landscape phone alone should not be treated as an unfolded tablet. This is Lowkey's size-based policy, not a promise to detect every physical hinge or fold angle. Re-evaluate when the IME's window/configuration changes. App multi-window affects the profile only if it actually changes the IME's available bounds; do not assume both windows are identical.

- Each profile remembers its last selected mode. Folding, rotating and opening another field restore that profile rather than overwriting manual choices.
- Automatic splitting supplies the default for Large profiles. Explicitly enabling it changes saved Large profiles in Normal to Split and marks those choices as automatic; it preserves One-handed, Floating and manually selected Split. New Large profiles default to Split while it is enabled.
- A manual mode selection, including returning to Normal, is remembered as manual. It survives folding, rotation and process restart. Explicitly turning the automatic setting off and on again can reapply its defaults to Large profiles in Normal.
- Disabling automatic splitting returns only automatically selected Split profiles to Normal; manually selected Split stays available. Retain this automatic/manual origin with the profile so that disabling the setting cannot discard an explicit choice.
- Compact profiles are unaffected by the setting. Ineligible geometry temporarily falls back to Normal without overwriting the stored preference.
- Floating adjustments do not overwrite docked dimensions. Normal, One-handed and Split intentionally share the docked height and bottom space.
- Reproject saved relative positions into the current safe area and clamp on restore; a panel must never reopen off screen.

Migrate the old height preference to equivalent initial row heights: Compact 42 dp, Normal 48 dp and Tall 56 dp. Existing installs start in Normal mode with zero additional bottom space; automatic splitting stays Off. A profile without a saved value inherits the migrated height. No user data is reset.

## Integration and boundaries

Keep the existing Compose keyboard, key actions, correction engine and IME service. Compute one set of geometry bounds for rendering, pointer coordinates, long-press popups and window touch regions. A pure geometry/policy layer makes drag limits and transitions testable without Android UI.

Docked modes reserve their keyboard height plus bottom space. Floating mode reports no reserved content area and limits new touches to the panel and its drag bar. Use coordinates relative to the IME window, with no-content top insets at the window bottom. Restore normal insets on docking. Do not request overlay permission or create a separate system-overlay window. Android exposes content, visible and touchable bounds through [InputMethodService.Insets](https://developer.android.com/reference/android/inputmethodservice/InputMethodService.Insets); verify both legacy pan/resize apps and modern inset-aware apps.

Existing hold-driven popups use a separate non-focusable window, and their gesture continues from the key that received the initial press. Do not blindly add every popup to the IME touch region. A future independently tappable popup needs its own appropriate touch handling. Keep popups/choosers within the safe screen area, opening below an anchor if there is insufficient room above. While floating, avoid painting an opaque keyboard-coloured navigation bar across the screen. Keep handles clear of system gesture edges.

Dictation, its model picker and the minimized bar retain the active frame's horizontal anchoring, width, bottom space and floating/docked nature. Their compact heights may change, but the voice/keyboard toggle must not jump to the opposite side of the screen. Split uses a full-width strip. Adapt model labels and status content to narrow frames while retaining Stop and the mode toggle. Do not run a new speech session as a side effect of opening layout controls.

The existing Backspace gesture aims into editor text above the keyboard. Preserve that behavior above a floating panel; do not silently claim it can aim below or through the panel. Validate the word slider near every screen edge, and document this limitation in the user guide.

Preserve suggestions, dictionary access, clipboard/image workflows, accents, globe/space language selection, deletion gestures, themes and physical-keyboard behavior. Resizing must not insert, delete or commit composing text. Mode changes must cancel active gestures and dismiss stale popups safely.

Use the existing settings store. No new runtime dependency is required by this design; reassess only if implementation establishes a concrete need, with the required license notices. Do not change version numbers, release metadata or artwork as part of the design step.

## Verification and delivery

Implement in reviewable slices: geometry and preference migration; docked resizing and the quick menu; one-handed/floating modes and IME integration; split rendering and automatic selection; documentation and final regression verification. All requested modes belong to the completed feature, not deferred placeholders.

Run `./gradlew testDebugUnitTest` for every change. Cover geometry bounds, edit commit/cancel, profile restoration, automatic/manual precedence including disabling the setting, old-height migration, and preservation of key outputs across splits. Build and lint the final implementation.

Verify actual interactions on an emulator: drag every resize/move handle, confirm/cancel/reset, recover geometry from settings, type after moving, tap the app outside a floating panel, switch one-handed sides, type both split halves, rotate, resize/fold the available window and restart to check persistence. Test narrow and large environments, normal and numeric fields, multi-window, and both system navigation modes where available. Recheck long-press popups, deletion gestures, clipboard, dictation, model selection and minimization after movement. Check that a manual mode survives returning to its profile and that switching automatic splitting off preserves manual Split choices.

Compare the resize interaction against Gboard directly. If a suitable reference installation or foldable test environment is unavailable, report that verification gap rather than claiming exact parity or a physical-fold test. Keep test media under ignored artifacts; review media before public attachment. No physical-device changes without a request.

## References and design review

- [Google's original resize/reposition announcement](https://blog.google/products-and-platforms/products/search/gboard-android-gets-new-languages-and-tools/) establishes the functional reference; it does not specify the current interaction in full.
- [Google Pixel Community: resize, move, reset and done](https://support.google.com/pixelphone/thread/299821968/keyboard-wrong-position?hl=en) is community guidance, not a complete product specification.
- [Google Pixel Tablet Help](https://support.google.com/googlepixeltablet/answer/13555948?hl=en) documents split and floating toggles, but not the complete resize interaction.
- [Android WindowManager metrics](https://developer.android.com/reference/android/view/WindowManager#getCurrentWindowMetrics()) describes bounds for the current context's window and separately reported insets. Use the IME context with a compatible fallback on older Android versions. The shortest-side threshold is Lowkey's proposed policy, distinct from width-only adaptive breakpoints.

Reviewed jointly with Opus 5.5. Incorporated a smaller mode menu, explicit split boundaries, shared docked dimensions, frame-preserving voice/minimized modes and persistent manual choices. Refinements during integration: keep unavailable Split discoverable but disabled; retain all suggestions; disable only automatic Split selections when turning the setting off. The review's fixed minimum-width numbers remain provisional because the current voice row also needs responsive sizing. A device comparison is still required to validate Gboard handles, limits, cancellation and gesture feel; no claim of exact parity is made yet. Approval of this design precedes the implementation plan.
