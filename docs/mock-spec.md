# Mock demo specification

This spec defines every page and behavior of the mock demo. Two mocks implement it:

- `docs/app-prototype.html` is a self-contained, clickable HTML prototype.
- The native Android mock is a Kotlin and Jetpack Compose app with a real, selectable keyboard (input method). Speech, model downloads, and the language model are simulated.

Both mocks use the same page names, labels, and flows so they can be compared. The keyboard follows **concept 4** (`artifacts/keyboard-mockups/concept-4.html`) for its key layout. User feedback on 2026-10-10 reduced its two-line voice panel to one row with a model picker.

Working app name: **Slim Keyboard**. Application ID: `com.rheerkens.androidkeyboard`.

## What is simulated

| Area | Mock behavior | Later real behavior |
|---|---|---|
| Speech recognition | Scripted English coding phrases arrive word by word as live text; Stop finalizes after about 1 s | Local streaming model, such as Moonshine Small |
| Microphone | Native: real runtime permission request, no audio capture. HTML: toggle | `AudioRecord` at 16 kHz mono |
| Model downloads | Fake progress over about 4 s, cancellable, nothing downloaded | HTTPS download, SHA-256 check, app-private storage |
| Suggestions | Prefix match against a small built-in word list plus personal terms | Real language dictionary and ranking |
| Personal dictionary | Real persistence in the native mock (preferences or small JSON file) | Same contract with a proper store |
| Settings | Real persistence in the native mock | Same |

Every screen showing simulated data displays a small `MOCK` tag.

## Keyboard (input method)

### Keys mode

Total height about 240 dp: a 44 dp top strip plus four 48 dp key rows.

- **Top strip**, left to right: a book button that opens the dictionary in the companion app, three suggestion slots, and the mic/keyboard toggle in a 44 × 44 slot at the far right.
- With no partial word, suggestions show saved personal terms. While typing, they show prefix matches; the middle slot is the best match. Tapping a suggestion replaces the partial word and adds a space.
- Long-press a suggestion or the typed word to show **Add "word" to dictionary**.
- Rows: `q`–`p`; `a`–`l`; shift, `z`–`m`, backspace; `?123`, **globe**, comma, space, period, Enter.
- The top letter row shows small number hints. Holding `q`–`p` opens a large popup with `1`–`0`; releasing inserts the digit. A short tap types the letter; cancelling a hold inserts nothing. The popup adds no keyboard height. English and Dutch use the same shortcuts, including with Shift or Caps Lock on. Each finger keeps its own press during overlapping taps.

- The globe sits immediately after `?123`, Samsung's position. Tap cycles typing layouts (English (US), Nederlands). Long-press opens the system input-method picker. The space bar shows the current layout name.
- Shift: tap for one capital, double-tap for caps lock. Auto-capitalize at the start of a sentence (setting).
- A Backspace tap (released within 300 ms without sliding) deletes one character on release. There is no repeat deletion: holding for 300 ms, or sliding up 8 dp, raises a vertical slider from the key and deletes nothing until the slider moves: a 52 dp capsule reaching about 3 cm (189 dp) above the touch point, capped at the keyboard's top edge. Sliding up fills the capsule to the finger; each notch removes one more word before the cursor in the field, with a haptic tick, and the top tenth (dashed line, trash icon) turns red and removes all text before the cursor; text after the cursor stays. Sliding down restores the words. Steps follow a curve (exponent 1.6 above six words) so the first words stay several dp apart. A chip beside the finger reads Slide up, N words, Clear all (cursor at the end), or All before cursor. Release keeps the field as shown. On the minimized bar, which has no room above its keys, the slider draws in its own non-focusable window above the bar and starts 4 dp above the key, with the same 189 dp of travel; the gesture stays with the keyboard window. TalkBack exposes explicit Delete text before cursor and Clear all text actions.
- `?123` switches to a symbols layer, with `=\<` for a second layer and `ABC` to return. Include coding symbols: `{ } [ ] ( ) < > = + - * / \ | _ & ^ % $ # @ ! ? ; : ' " ~` and backtick.
- Enter follows the editor's IME action, such as Go, Send, Search, or a newline.
- Optional key-press haptics (setting).

### Keyboard lab and model retention

Every companion page has a **Test keyboard** action in its top bar. It opens a rounded modal sheet over the current page, with a focused multiline test field, actual model memory state, Clear, Copy, and a return button. The underlying page remains composed, preserving its scroll position and unsaved edits. The shared test draft and cursor selection are saved separately from page editors. Closing the sheet cancels any unfinished dictation through the normal IME editor lifecycle.

**Settings → Voice → Keep model loaded** controls native resources after dictation ends: release immediately, 30 seconds, 2 minutes by default, 5 minutes, 15 minutes, or until the app process ends. Idle expiry also applies with the keys still open. New dictation cancels the previous deadline; a setting change uses elapsed idle time. Active capture and final transcription never expire. Model/language changes and Android memory pressure can release resources sooner. Downloads stay on disk. Models, Voice settings, and the lab show whether the native model is loaded or in use.

### Voice mode (one row, about 56 dp)

A single row, left to right:

- **Model picker**: a compact pill showing a status dot, the active model name, and a chevron, for example `● Moonshine Small ⌄`. It works like T3 Code's model selector. With no model installed, it reads `Choose model ⌄`.
- **Status area**, without labels or repeated text:
  - Listening: a level meter and an elapsed time such as `0:07`.
  - Finalizing: a small spinner.
  - After a commit or cancel: a short note such as `6 words` or `Cancelled · 4 words discarded` that fades after about 2 s.
  - Ready: empty.
- **Talk / Stop**: a single button. It shows a mic icon and `Talk` when ready, and a stop icon and `Stop` while listening. It is disabled while finalizing. Without microphone permission it reads `Allow mic` and opens Home.
- **Toggle**: the keyboard icon in the same 44 × 44 slot at the far right as the mic in keys mode.

Removed as redundant: the `EN speech` tag, the gear, `Tap to talk` and `Listening` text, and `MOCK` tags inside the keyboard. Speech settings are reachable from the model picker.

Behavior:

- No transcript area inside the keyboard. Live provisional text appears in the host editor as composing (underlined) text.
- Entering voice mode starts listening immediately (setting, on by default).
- Stop moves listening to finalizing once. Repeated Stop does nothing. Finalizing commits the final text once and applies enabled phrase rules. It then returns to Ready, or starts a new capture if continuous mode is on.
- Switching to keys cancels listening or finalizing. Provisional text is removed; committed text stays. The note shows the discarded word count.
- Hiding the keyboard or moving to a different editor also cancels. Every capture has a session ID. Results from an old session are ignored.
- **Keyboard-first dictation** is on by default. Each editor opens with keys; tapping the mic starts a capture in the compact voice row. After Stop or auto-stop, stay compact until final text is inserted, then return to keys for corrections. No-speech also returns to keys. With this setting off, the earlier last-mode, auto-start, and continuous options apply.

### Model picker (inside the keyboard)

Tapping the pill opens a compact list that expands upward inside the keyboard area to keys-mode height. It is styled like T3 Code's model selector: a clean list with one row per model and minimal decoration.

- Opening the picker cancels an active capture, just like switching to keys.
- **Installed** section: model name and a one-line hint such as `Live · 246 MB`, with a check on the active model. Tapping a model selects it, closes the picker, and starts a new capture if auto-start is on.
- **Available** section: uninstalled models with their size and a download icon. Tapping one starts the download, shows inline progress with a cancel `×`, and **selects the model automatically when the download completes**.
- Footer: **Manage models** opens the companion Speech models page. **Voice settings** opens Settings at the Voice section.
- Back, tapping outside the list, or tapping the pill again closes the picker without changes.

## Companion app pages

Bottom navigation: **Home**, **Models**, **Dictionary**, **Settings**. Try it, About, and Diagnostics open from Home or Settings.

1. **Home / Setup**
   - Checklist with live status: 1 Enable Slim Keyboard in system settings; 2 Choose it as the current keyboard; 3 Allow the microphone; 4 Download a speech model. Each row has an action button and a done tick.
   - Native mock: steps 1–3 use real system state and intents. Step 4 uses the mock model store.
   - Cards: **Try it**, current model, dictionary size, and last mode.
2. **Try it**: a playground with a multiline field, a single-line search field (action Search), and a send field (action Send). Includes sample prompts such as `Explain this Kotlin coroutine` and a **Clear** button.
3. **Speech models**
   - Catalog: Moonshine Tiny (English, live, about 100 MB), Moonshine Small (English, live, 246 MB, **Recommended**), Moonshine Medium (English, live, 450 MB), Parakeet Unified EN 0.6B (English, buffered live, about 650 MB), and Whisper base.en (English, final only, about 150 MB). Sizes are placeholders except the Moonshine Small and Medium sizes.
   - Each row shows name, size, live or final-only, a short description, and an action: Download, a progress bar with Cancel, Use, Active, or a delete menu.
   - A completed download becomes the active model automatically, on this page and in the keyboard's model picker.
   - The active model cannot be deleted. Deleting it is disabled and explains why.
   - Shows storage used and free space, plus a note that models stay in app-private storage outside the APK.
4. **Dictionary** with two tabs:
   - **Personal terms**: exact-spelling entries such as `T3 Code`, `coroutineScope`, `adb`, `ViewModel`, and `Jetpack Compose`. Supports search, add, edit, and delete. Multiword terms are allowed. A note says terms protect spelling and case in suggestions and autocorrect, and do not train speech recognition.
   - **Speech phrase rules**: `spoken → written` rules such as `three code → T3 Code`, `view model → ViewModel`, and `a d b → adb`. Each has its own switch, and there is a master switch (off by default). A note says rules apply once to final speech text after Stop.
   - Shows Import and Export as disabled `Coming later` actions.
5. **Settings**, grouped:
   - Keyboard: height (Compact, Normal, Tall), key-press vibration, auto-capitalize, autocorrect, suggestion strip, and typing layouts (English (US), Nederlands).
   - Voice: Keyboard-first dictation (on), model retention, live text in editor (on), auto-stop after silence (Off, 2 s, 4 s), apply phrase rules (off), voice language, and a link to Speech models. Auto-start, remember last mode, and continuous dictation apply when Keyboard-first dictation is off.
   - Appearance: theme (System, Light, Dark).
   - Links: Diagnostics and About.
6. **Diagnostics**: a voice session log with session ID, start time, transitions such as `listening → finalizing → committed` or `listening → cancelled (switched to keys)`, word count, and simulated latency. Includes a Clear log button and a simulated latency setting (Fast, Realistic, Slow).
7. **About and licenses**: version, privacy statement (everything stays on the device; no network except model downloads), references to FUTO Keyboard and the Moonshine voice-input fork with their roles, a note that code is written from scratch with these as design references, and a model-license note.

## Scripted voice phrases

Cycle through:

1. `explain what this kotlin coroutine scope does and when it gets cancelled`
2. `refactor the view model so the repository is injected`
3. `run the t3 code lint task and show me the errors`
4. `why does adb install fail with insufficient storage`

With phrase rules on, number 2 becomes `refactor the ViewModel so the repository is injected`. Number 3 becomes `run the T3 Code lint task and show me the errors`. With auto-capitalize on, capitalize the first letter of the final text.

## Acceptance flows

1. Setup: complete all four steps; the checklist shows all ticks.
2. Type `t3` in Try it; `T3 Code` appears as a suggestion; tap inserts it.
3. Add the personal term `Gradle wrapper`; it appears in suggestions on an empty word.
4. Open voice mode: listening starts, live text grows in the editor, Stop finalizes, and the text is committed once.
5. Open voice mode and switch to keys mid-listening: provisional text disappears, and the note shows the discarded word count.
6. Hide the keyboard in voice mode and reopen it: it is still in voice mode and a new capture starts.
7. Models: start a download, cancel it, and download again. The model becomes active when the download completes. Switch models from the keyboard's model picker, and confirm the active model cannot be deleted.
8. Enable phrase rules, then dictate phrase 3: the final text contains `T3 Code`.
9. Diagnostics lists the sessions from flows 4–8 with correct outcomes.
