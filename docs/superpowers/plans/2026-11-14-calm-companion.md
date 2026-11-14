# Calm companion screens

## User-approved direction
Continue the completed Home, Voice checklist and keyboard-test design through every remaining companion screen. Prioritize end-user clarity, a quiet hierarchy, concise truthful copy and existing Lowkey branding. Opus implements; the parent reviews and verifies; Fable then independently reviews usability and logical flow. This instruction authorizes the remaining screens as one implementation task, rather than another round of per-screen approvals.

## Design contract
- Keep the existing compact cloud/logo header and navigation, with Home's header unchanged.
- Use existing theme colors and typography, modest groups and clear active states.
- Give one relevant main action prominence. Keep secondary and destructive actions quiet.
- Prefer concise labels and state summaries; put genuinely optional explanation and advanced options behind disclosure.
- Preserve all settings, stored preferences, dialogs, model/language rules and accessibility labels.
- Keep errors, progress, disabled-state reasons and destructive confirmations understandable.
- Keep actual privacy, licensing and dependency information accurate and available.
- No new dependencies, engine changes or speculative features.

## Coverage
- [x] Dictionary and speech-rule editing
- [x] Settings overview
- [x] Keyboard size/modes and physical keyboard
- [x] Typing
- [x] Key feedback
- [x] Languages and downloads
- [x] Keyboard layouts
- [x] Clipboard and photo access
- [x] Appearance
- [x] Custom keyboard theme and advanced colors
- [x] Updates
- [x] Try-it tools
- [x] Diagnostics
- [x] About, privacy and licenses
- [x] Dictation preferences
- [x] Storage and model retention

## Decision log
- Shared helpers in `CalmComponents.kt`: quiet rounded groups (`SettingsGroup`, `surfaceContainerLow`, 20 dp corners as in the voice checklist), `PageSummary`, one full-width `MainAction`, `ChoiceDialogRow` for option lists (the current value shows under the title), `DisclosureRow` for optional detail, and `EmptyState`. Existing `Common.kt` helpers are unchanged.
- Settings overview: three groups (Keyboard, Voice, App) with short state summaries. Removed "Opens the Voice tab". The OpStudio credit is a single quiet row, and the trademark sentence lives in About.
- Keyboard: grouped by intent (size and position, keys, typing and feedback, physical keyboard). Reset size and position now asks for confirmation. Key beside Space is a choice dialog that says what each key does.
- Dictation: the dependent switches sit in a "Separate voice mode" group with one footer explaining why they're disabled. Their saved values and defaults are unchanged. Stop after silence is a choice dialog.
- Languages: your languages and their state come first, and the catalog sits behind Add a language. Downloads in progress or failed stay visible while the catalog is closed. Removal is confirmed, and the last enabled language stays protected. The switch hint follows the key beside Space: hold emoji, or tap globe. Word-list provenance is behind a disclosure.
- Layouts: Keyboard in use first, with the same switching hint. Chips are kept, with one footer.
- Dictionary: one Add action per tab next to the count. Search appears only when there are words. Tap a word to edit it; the trash is quiet and confirmed. The empty states say what words and rules do. Each rule shows the written text, then "When you say …", then its state.
- Theme: Standard and Custom are labelled options with descriptions. In Custom, a bottom bar is always visible with the state (Saved, Unsaved changes, Saving…), Cancel and Save theme. Advanced colors stay collapsed, and the contrast checks are unchanged under their own heading. Dropped a stale reference to a "Test keyboard" button on this page.
- Updates: Check for updates is the main action until a release is known. Then Update is the main action and Check again is quiet.
- Try it: one friendly test field with a clear icon. The EditText, Search, Send, sample prompts and Clear all sit under Advanced test fields. Test tags and IME actions are unchanged.
- Diagnostics: a summary first. Copy crash report is the main action only when there are crashes. Crash details and voice sessions are behind disclosures, and the debug audio input sits under Developer.
- About: product, version and a privacy summary that names every network use (models, languages, update checks and downloads). The license, source, third-party license texts, acknowledgements and trademarks are behind clear entries. The word-list credit no longer says "English and Dutch".

## Delivery gates
- [x] Opus implementation with a brief decision log and test results (507 unit tests pass; `assembleDebug -PuiPreview=true` builds)
- [x] Parent code review, complete tests and native UI verification
- [x] Fresh screenshots of the final screen flows
- [x] Fable independent UX/UI review of those screenshots and source
- [x] Material review findings resolved and rechecked

Existing uncommitted work and unrelated research documents must be preserved. Screenshots and host-specific paths stay outside git.

## Parent review
- All 16 default screens checked on an Android emulator, plus representative dark-mode screens, custom theme editing with the keyboard visible, large text, landscape, language downloads/removal, choice dialogs and reset confirmation.
- Fixed the advanced Android test field losing its contents when collapsed; Clear all fields now clears that field too. Both interactions verified on the device.
- Changed the diagnostics summary to report no recorded crashes rather than imply there are no problems; labelled the pending/failed language section Downloads.
- Storage facts stack their labels and values when space is limited or text is large. Verified with enlarged system text.
- Full unit suite and preview build pass: 507 tests, zero failures. Whitespace and private leak checks pass.
- Release-only update installation, every download/error state, and a complete screen-reader pass remain outside this visual check.

## Fable review follow-up
- No blocking findings. Restored a short hint explaining that tapping a downloaded language selects it. Verified switching to a downloaded language and back on the device.
- Removed the keyboard-switch gesture hint when there is only one enabled layout. Verified it appears when a second layout is enabled and disappears when it is removed.
- Added explicit discard confirmation for unsaved theme edits on system Back, header Back, tab navigation and switching to Standard. Verified that Keep editing preserves the draft and Discard performs the pending action.
- Choice dialogs no longer use the disclosure arrow; expandable explanations retain it. Current choice remains visible in the accent color.
- Updated the README's manual update-button label.
- Added a Done action at the top of the language catalog; verified closing it there. The existing action at the end remains available after scrolling.
- Kept the dedicated key-feedback destination and the theme editor's fixed save area. The static, non-interactive color illustration keeps fixed proportions; surrounding editor controls still follow system text size.
- Re-ran all 507 unit tests and rebuilt the preview successfully. Captured the final default screens and representative dark-mode screens after these corrections.
- Verified the color illustration on-device with enlarged system text: the model, stop and accent labels are no longer clipped; the surrounding controls remain enlarged.
