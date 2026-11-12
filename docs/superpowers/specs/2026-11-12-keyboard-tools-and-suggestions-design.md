# Keyboard tools and multilingual suggestions

Approved direction: keep six keyboard tool tiles, replace Dictionary with Number row,
rename Resize to Size & position, and make toggle states explicit. Retain One-handed,
Floating, Split and Settings. Dictionary stays in the companion app. Number row uses
the existing persisted setting and returns to typing immediately. Split reflects the
effective mode and explains its wide-window requirement.

Suggestions use installed, enabled languages without changing the physical layout.
Known words in another participating language are not autocorrected into the active
language. Personal terms retain priority; ambiguous corrections preserve the input.
The middle suggestion continues to describe what Space will insert.

Emoji suggestions use local keyword data and the existing supported emoji catalog.
An exact greeting such as hi/hoi/hallo may offer a waving hand. Short ambiguous
prefixes such as he retain word suggestions. Emoji are inserted only on a deliberate
tap, never by Space or autocorrect. No next-word prediction or online typing service.

## Approach

Keep the existing tool grid and settings store instead of adding a seventh tile or
a submenu. Extend the existing word suggester instead of replacing the typing engine.
Reuse the existing emoji rendering and history; do not import code or assets from
another keyboard.

Implementation choice: preserve the three-lexicon memory budget. Use the active
language and the next two distinct enabled, installed languages in keyboard order.
Keep secondary-language corrections conservative and all lookup work off the UI thread.

## Boundaries and verification

Work only in the assigned worktree. No logo, minimized-bar placement, release,
version, merge, physical-device installation, or companion-app redesign changes.
No new dependencies. Test multilingual protection/ranking, emoji selection and
ambiguous prefixes, number-row persistence, and narrow-window placement states.
Run the full testDebugUnitTest suite and attempt real emulator interaction. Keep
all logs, APK copies and media under ignored artifacts/. Report any device limitation.

## Follow-up: number hints and accent grid

When the dedicated number row is enabled, hide the small digit hints on letter
keys in every layout, including AZERTY. Preserve the underlying long-press digits
and accents and restore the hints when the number row is disabled.

Replace the long single-row accent chooser with a balanced grid of at most five
columns. Put the default choice over the held key and keep the grid inside the
window horizontally. A multi-row grid's bottom row covers the key so the top row
can stay within the keyboard window. Match drawing and hit testing, including
empty cells, screen edges, cancellation and returning to a choice.
