# Keyboard tools and suggestions implementation plan

> Execute inline using superpowers:executing-plans; no delegates or extra worktrees.

**Goal:** implement the approved tool menu, multilingual word suggestions and explicit emoji suggestions.
**Architecture:** existing Compose tool grid and settings flow, pure Kotlin suggestion policies, existing IME insertion paths.
**Tech stack:** Kotlin, Compose, JUnit, Gradle; no new dependencies.
**Spec:** ../specs/2026-11-12-keyboard-tools-and-suggestions-design.md

## Global constraints

Keep the three-lexicon memory budget and existing physical layout. Protect personal
terms, sensitive fields, Space semantics and rejected corrections. No unrelated
placement changes or logo edits. Release preparation is limited to version metadata
and release notes. All execution artifacts belong under ignored artifacts/.

## Review focus

- Number row agrees with companion settings and closes the menu after changing height.
- Split selected/disabled states follow the effective narrow-window mode.
- A secondary-language word survives Space and punctuation; duplicate layouts use one lexicon.
- Language selection changes invalidate pending and cached suggestions.
- Emoji never occupy the Space/action slot or enter the dictionary through long-press.

## Task 1: Tool menu

Files: KeyboardTools.kt, KeyboardUiModel.kt, SlimKeyboardService.kt, user guide.
- [x] Run existing number-row/layout and placement regressions; review shared settings-store routing.
- [x] Replace Dictionary tile, rename Resize, add visible and accessible states; wire the existing setting.
- [x] Run targeted tests.

## Task 2: Multilingual suggestions

Files: Suggester.kt, new SuggestionLanguages.kt, AppGraph.kt, SlimKeyboardService.kt,
new MultilingualSuggesterTest.kt and SuggestionLanguagesTest.kt.
- [x] Add/run tests for foreign-word protection, balanced completions, ambiguity, personal priority and language selection.
- [x] Extend Suggester with secondary lexicons; snapshot participating languages, preserve cache validity, preload only the bounded selection.
- [x] Run targeted tests and existing dictionary tests.

## Task 3: Emoji suggestions

Files: new EmojiSuggestions.kt, Suggester.kt, SuggestionStrip.kt, SlimKeyboardService.kt,
new EmojiSuggestionsTest.kt, docs/user-guide.md.
- [x] Add/run tests for greetings in participating languages, he ambiguity, supported glyph filtering and Space-slot preservation.
- [x] Offer local catalog-backed emoji in a side slot and insert via an explicit emoji suggestion action.
- [x] Run targeted tests.

## Task 4: Verification

- [x] Run testDebugUnitTest and assembleDebug with JDK 21; inspect results.
- [x] Review the diff for state, insertion, privacy, performance and scope regressions.
- [x] Use device_list/device_open with a fresh session and verify actual interaction.
- [x] Update user guide and record results; leave reviewable changes without release or merge.

## Review decisions

- Dictionary-based autocorrect waits for every participating lexicon to load; otherwise
  a valid word absent from a fallback list could be corrected into the wrong language.
  Exact personal-term casing still works during loading.
- Capture the suggestion context before acquiring lexicon snapshots, and reject results
  after an editor session, selected language set, or lexicon generation changes.
- Preserve both personal-term side suggestions when an emoji would displace one.
- Keep the disabled Split caption short enough for narrow floating keyboards; its
  accessibility state explains the wider-screen requirement.
- The emulator screenshot transport was repaired before verification. Device interaction
  used a fresh, dedicated session; no other session was reused or closed.

## Verification result

`testDebugUnitTest assembleDebug --max-workers=2` passed after integration with the
0.7.0 baseline: 384 tests across 56 suites, zero failures, errors or skips. This includes
16 new multilingual and emoji tests and three additional number-hint/grid regressions.
The source leak checks (including new files and the private denylist) passed.
A debug APK was installed on an Android 15 phone emulator and verified through actual
taps, holds and slides. No physical-device installation was performed. Media and
accessibility snapshots remain under ignored artifacts/.

| Emulator check | Observed result |
| --- | --- |
| Six-tile menu | Size & position, One-handed, Floating, Split, Number row and Settings are visible. Active toggles have a checkmark and On caption. |
| Number row | Toggling closes the menu, changes the keyboard height and hides/restores the small digit hints on QWERTY and AZERTY. The setting survives an app restart. |
| Compact accents | Holding E shows a two-row grid above neighboring keys. Releasing the default choice inserts 3; sliding right inserts É; sliding up inserts Ë; sliding away cancels without inserting. |
| Multilingual typing | With English active and Dutch enabled, typing Fiets followed by Space preserves Fiets. He offers English Her and Dutch Het while retaining the typed word in the middle. |
| Emoji suggestions | Hi offers a waving hand; tapping it inserts the emoji. Space inserts Hi, never the emoji. He retains word suggestions. |
| Wide and narrow windows | Landscape enables Split and renders both keyboard halves. Portrait uses the normal layout and labels Split as Wide only. The one-handed menu remains readable and marks the active mode. |

The emulator covered phone portrait, landscape and one-handed layouts. A physical
Fold device was not tested. The complete interaction pass preceded integration of
the mainline minimized-bar, screenshot-history and companion-app updates. A second pass on the integrated build stopped when the
device tool repeatedly rejected navigation in Android's keyboard settings; that
integrated UI pass is not claimed as complete.

## Follow-up: number hints and compact accents

- [x] Hide letter-key digit hints with the dedicated number row, preserving long-press shortcuts.
- [x] Use a balanced grid of at most five columns, with matching drawing and hit testing.
- [x] Keep the default choice by the pressed key, reject empty cells, and preserve slide cancellation.
- [x] Draw the open grid above sibling keys so its lower row remains visible.
- [x] Run the full test suite, debug build and source leak check again.
- [x] Verify actual emulator interaction after the device becomes available; confirm
  number hints, accent selection, cancellation, menu states and suggestion insertion.
