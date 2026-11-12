# Keyboard size, position and modes

Status: draft for approval. This document describes proposed behavior, not shipped features.

## Intent and agreed scope

Make Lowkey comfortable to use on phones, foldables and tablets. The requested features are direct resizing like Gboard, repositioning, left/right one-handed use and a split keyboard. Repositioning includes both a raised docked keyboard and a freely movable floating keyboard. Automatic splitting must be a user setting. A quick menu on the keyboard is the selected entry point.

Gboard is a functional reference only. Do not copy its code, icons or other assets. Keep Lowkey's identity and approved launcher artwork.

The defaults and detailed interaction rules below are implementation proposals, not additional user requests.

## Approaches

Two access patterns were considered: a keyboard quick menu with preferences in the app, and controls available only in app settings. The quick menu was selected: resizing requires seeing the keyboard, and switching modes should not require leaving the current text field.

Use a shared geometry model and the existing key renderer for every mode. Separate keyboard implementations would duplicate typing, accessibility and gesture behavior. Merely scaling the existing view would leave touch areas, popup coordinates and application insets inconsistent with the visible keys.

## Quick menu

Add one clearly labelled keyboard-tools button in the expanded keyboard's top strip. Keep minimize and voice access. Move the current personal-dictionary shortcut into the menu to avoid crowding the suggestion strip.

Opening the menu replaces the key rows with tools, without leaving the editor or changing its text. Back closes the menu before hiding the keyboard. Show the active mode and these actions:

- Size and position.
- Normal, One-handed left, One-handed right, Floating, and Split.
- Personal dictionary and Keyboard settings.

Switching a mode applies immediately. The menu closes and returns to typing. A normal-mode action is always available. The minimized bar keeps its existing compact role; expanding it restores the selected mode.

## Size and position

Replace Compact/Normal/Tall in settings with a route to the interactive editor in the built-in keyboard test field. The same editor opens directly from the quick menu.

Use Gboard's interaction pattern: an outline with drag handles, live resizing, a move handle, Reset and a confirmation checkmark. Do not substitute a slider or a list of preset sizes. Verify the reference interaction on an available Gboard installation before finalizing handle behavior; public descriptions alone do not establish exact limits or gesture feel.

- In Normal mode, top and bottom handles change height independently; moving the keyboard changes the space beneath it. Width remains the available width.
- In one-handed mode, width and height are adjustable within the available area. Position stays anchored to the chosen left/right side, with adjustable bottom space. Provide a direct side-switch action and return to Normal.
- In Floating mode, resize handles adjust width and height, and a dedicated handle moves the panel horizontally and vertically. It overlays the app; the visible app around it remains interactive. Returning to Normal docks the keyboard again.
- In Split mode, preserve a full-width outer keyboard area, adjust height and bottom space, and allow the central gap to be adjusted. Floating and Split are alternative modes, not a combined floating pair of panels.

Use an edit-session draft. Dragging changes the preview; the checkmark saves it. Back, hiding the keyboard, switching editor, rotation or a fold/window change cancels an unfinished edit and restores saved geometry. Reset previews the default geometry for the current mode/profile; it also requires confirmation.

During geometry editing, suppress typing, dictation starts and destructive keyboard gestures. Keep all handles and controls within system-safe bounds. Clamp dimensions to usable key sizes and available space, without snapping to the three legacy heights. Add accessible increment/decrement actions for controls that otherwise require dragging. Exact lower bounds are a builder decision to validate on the smallest supported screens and against the reference interaction.

## Split key layout

Place the left and right key groups at opposite sides, separated by a real central gap. Do not stretch individual keys across the gap. Apply the split consistently to letters, numbers and symbols, including every enabled language and alternative arrangement.

Split near the weighted midpoint of each row, preserving key order and every output character. Prefer a split between keys; never split a letter key. Treat the spacebar specially: a space key on each half, both inserting one space. Keep Shift and the layer switch reachable on the left, and Backspace and Enter on the right. Do not introduce duplicated letter keys in this first design.

Split is manually available when the two halves can meet minimum usable widths. Disable it with a short explanation in narrower windows. The stored large-screen choice survives temporary fallback to Normal.

## Preferences and screen changes

Add **Automatically split on large screens**, default Off. Manual mode switching remains available regardless of this setting.

Store the selected mode and mode-specific dimensions/position separately for small/large windows and portrait/landscape. Use the available application/display window before keyboard insets, not the keyboard's reduced height, to classify the environment. Proposed large-screen eligibility is a shortest available side of at least 600 dp; a landscape phone alone should not be treated as an unfolded tablet. Re-evaluate when the window changes, including multi-window use. This is a size-based policy, not a promise to detect every physical hinge or fold angle.

- With automatic splitting Off, restore the saved mode for the destination profile.
- With it On, entering an eligible large-screen environment selects Split. A subsequent manual mode choice wins until the environment changes or the setting is explicitly enabled again. Opening another text field alone does not override that choice.
- Returning to a small environment restores its saved mode. Ineligible geometry temporarily falls back to Normal without overwriting the saved preference.
- Keep geometry per mode as well as per profile: floating adjustments must not overwrite normal or one-handed dimensions.
- Reproject saved relative positions into the current safe area and clamp on restore; a panel must never reopen off screen.

Migrate the old height preference to equivalent initial row heights: Compact 42 dp, Normal 48 dp and Tall 56 dp. Existing installs start in Normal mode with zero additional bottom space; automatic splitting stays Off. A profile without a saved value inherits the migrated height. No user data is reset.

## Integration and boundaries

Keep the existing Compose keyboard, key actions, correction engine and IME service. Compute one set of geometry bounds for rendering, pointer coordinates, long-press popups and window touch regions. A pure geometry/policy layer makes drag limits and transitions testable without Android UI.

Docked modes reserve their keyboard height plus bottom space. Floating mode does not reserve a full rectangular keyboard area in the underlying app; explicitly manage content insets and the touchable region, including active popups. Do not request overlay permission or create a separate system-overlay window. Android exposes these distinctions through [InputMethodService.Insets](https://developer.android.com/reference/android/inputmethodservice/InputMethodService.Insets).

When dictation or the minimized bar is active, use its existing docked compact presentation and retain the expanded keyboard's saved mode. Restore that mode on return to the keys. Do not run a new speech session as a side effect of opening layout controls.

Preserve suggestions, dictionary access, clipboard/image workflows, accents, globe/space language selection, deletion gestures, themes and physical-keyboard behavior. Resizing must not insert, delete or commit composing text. Mode changes must cancel active gestures and dismiss stale popups safely.

Use the existing settings store. No new runtime dependency is required by this design; reassess only if implementation establishes a concrete need, with the required license notices. Do not change version numbers, release metadata or artwork as part of the design step.

## Verification and delivery

Implement in reviewable slices: geometry and preference migration; docked resizing and the quick menu; one-handed/floating modes and IME integration; split rendering and automatic selection; documentation and final regression verification. All requested modes belong to the completed feature, not deferred placeholders.

Run `./gradlew testDebugUnitTest` for every change. Cover geometry bounds, edit commit/cancel, profile restoration, automatic/manual precedence, old-height migration, and preservation of key outputs across splits. Build and lint the final implementation.

Verify actual interactions on an emulator: drag every resize/move handle, confirm/cancel/reset, type after moving, tap the app outside a floating panel, switch one-handed sides, type both split halves, rotate, resize/fold the available window and restart to check persistence. Test narrow and large environments, normal and numeric fields, and both system navigation modes where available. Recheck long-press popups, deletion gestures, clipboard, dictation and minimization after movement.

Compare the resize interaction against Gboard directly. If a suitable reference installation or foldable test environment is unavailable, report that verification gap rather than claiming exact parity or a physical-fold test. Keep test media under ignored artifacts; review media before public attachment. No physical-device changes without a request.

## References and design review

- [Google's original resize/reposition announcement](https://blog.google/products-and-platforms/products/search/gboard-android-gets-new-languages-and-tools/) establishes the functional reference; it does not specify the current interaction in full.
- [Google Pixel Community: resize, move, reset and done](https://support.google.com/pixelphone/thread/299821968/keyboard-wrong-position?hl=en) is community guidance, not a complete product specification.
- [Android adaptive window guidance](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes) supports reacting to the available window rather than assuming a device category. The shortest-side rule above is Lowkey's proposed policy.

Self-review, without additional agents: checked the requested modes, migration, manual/automatic precedence, reversible editing, editor safety and floating touch-through. A device comparison is still required to validate Gboard interaction details. Approval of this design precedes the implementation plan.
