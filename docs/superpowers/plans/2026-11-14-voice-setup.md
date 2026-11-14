# Voice setup implementation plan

> **For agentic workers:** Execute inline with superpowers:executing-plans. The approved design is the checklist below; no new design approval is needed.

**Goal:** Implement the approved Voice checklist and model chooser with a compact Lowkey header outside Home.

**Architecture:** Keep the existing ModelStore, permission launcher and navigation. Render their real state as three checklist steps. Put model selection and optional management in a bottom sheet. Replace the shared non-Home top bar with the existing sky/logo styling at a smaller height.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, existing Android model store.

**Spec:** User-approved checklist variant 2: microphone, model download, ready to dictate. Model chooser groups multilingual and English-only models with name, size and recommendation. No Voice title or keyboard action in the global header. Home keeps its current header.

## Constraints
- Preserve existing downloads, cancellation, retry, model activation/deletion, permission handling and settings destinations.
- No automatic permission requests or model downloads.
- Keep model source/license details available without crowding the chooser.
- No changes to the keyboard engine or Home layout.
- Do not commit unrelated research files or temporary screenshots.

## Review focus
- Permission denied or revoked: microphone step remains actionable; installed models survive.
- Existing active model: ready immediately after microphone permission, with management available.
- Cancelled/failed download: retry remains possible; never claim ready before installation.
- Selecting another model: choice itself never downloads; explicit action activates/downloads it.
- Narrow screens, large text and dark mode: checklist, chooser and navigation remain usable.

## Tasks
- [x] Connect the already approved quiet keyboard test sheet and conditional voice-setup CTA.
- [x] Add compact logo header to the shared shell; preserve Back on nested pages and visible page titles in content.
- [x] Implement checklist in ModelsScreen from actual permission and ModelStore state.
- [x] Add grouped model picker with explicit confirmation and optional details/deletion.
- [x] Run testDebugUnitTest and assembleDebug with the UI-preview package.
- [x] Verify actual UI: permission deny/allow, chooser, download/cancel/retry, ready, dark/large text, nested Back and unchanged Home.
- [x] Run diff and leak checks; retain existing staged changes and unrelated documents.

Review: corrected returning to a pending download when another model is active, retained restored model selection, and reserved space for the percentage at large text sizes.

Device verification completed after reauthentication: permission denial/retry/grant, model selection, real download/cancel/restart/completion, revisiting a second download with an active model, dark mode, large text, nested Back, and test input/clear were exercised. No real recording was made.

## Opus review corrections
- Separate actual active-model readiness from the candidate in the picker; keep ready state and settings during another model selection/download.
- Keep model management available with missing microphone permission and expose cancellation/retry for every download/error in the picker.
- Use a shorter header in landscape and cap brand-text scaling while keeping content text accessible.
- Include the whole test-field decoration in its focus target, enforce 56 dp model rows, reset stale permission flags after a grant, and mark page titles as headings.
- The removed keyboard action in headers was explicitly requested. Do not add it back or ask for another approval; Home and the ready state still open the shared test field.
- Added four regression cases for actual readiness, revoked permission and selection/download independence. They reproduced three failures before correcting the old selection-dependent status.

Final review validation: all 507 unit tests pass, including the four new regression tests; UI-preview APK builds. Native checks confirmed readiness while selecting a different model, cancellation of an unselected download, management with microphone permission revoked, and the conditional test-sheet CTA. Landscape with enlarged text showed the shorter header and single navigation row. A final extra scroll check and restoring device orientation were interrupted when the device host connection dropped; no further device actions were attempted after the failed recovery.
