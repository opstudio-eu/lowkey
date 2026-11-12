# Use Lowkey

For installation, see the [README](../README.md#install).

## Dictate text

1. Download a speech model from **Models** and select it.
2. Open a text field and tap the keyboard's microphone button.
3. Speak, then tap **Stop**. Lowkey transcribes on your phone and inserts the finished text.

With **Settings → Voice → Keyboard-first dictation** enabled, keys return after transcription so you can make corrections. This is the default. Turn it off to use mode recall, auto-start, or continuous dictation settings.

Switching to keys, hiding the keyboard, changing editor, moving the cursor, or opening the model picker cancels capture.

Under **Settings → Voice → Keep model loaded**, choose how long to keep an idle model in memory. The default is two minutes. Releasing it does not delete the downloaded files. Recording and transcription do not expire, but changing the model or language can release it, as can Android memory pressure.

## Add keyboard languages

English (US) is built in. Dutch, Polish, English (UK), German, French, Spanish, Italian, Portuguese (Brazil and Portugal), Swedish, Danish, Norwegian (Bokmål), Finnish, Czech, Croatian, Slovenian, Romanian, Lithuanian, Latvian and Russian download on request: open **Settings → Keyboard → More languages** and tap **Download**. Each word list is 0.4–5.2 MB. Lowkey downloads it from the Android Open Source Project at a fixed version, checks it against a known checksum, and converts it on your phone. A downloaded language is turned on at once; delete it on the same page.

Each language has its own suggestions, autocorrect, layout and accents. Words typed without their accents get them back (`zolw` becomes `żółw`, `konnen` becomes `können`), and suggestions ignore missing accents. Polish also restores the accents of common words such as `juz` → `już`, and German capitalizes nouns typed in lowercase (`zeit` → `Zeit`).

To dictate in the language you type in, set **Settings → Voice → Voice language** to **Same as keyboard**. Switching languages then reloads the speech model in the new language.

If Dutch was enabled in an earlier version, where it was built in, it downloads again by itself after the update.

## Choose layouts and type accents

Enable layouts under **Settings → Keyboard → Keyboard layouts**. Each language on your phone is listed with its own layout first. Languages written in the Latin alphabet also offer QWERTY, AZERTY, QWERTZ, Dvorak and Colemak. Tap the globe key to cycle through the layouts that are on, or hold the space bar to choose one. Holding the globe opens Android's picker for other keyboard apps.

The selected language controls suggestions and autocorrect. Changing the letter layout does not change that language.

Hold a top-row letter to select a number. Hold a letter with accents, slide to the desired character, and release to type it. The language's own accents come first: on Polish, hold A for ą, L for ł or Z for ż. Slide off the choices and key to cancel. Shift and Caps Lock also apply to accented letters.

Space and punctuation apply autocorrections. Press Backspace immediately after a correction to restore what you typed. Add terms to the personal dictionary to use them in suggestions and preserve their casing in dictated text. Spoken-phrase replacement rules are opt-in. Next-word prediction is not available.

## Minimize the keyboard

Tap the chevron at the left of the suggestion strip to minimize the keyboard. Tap the keyboard button on the minimized bar to expand it.

With a physical keyboard connected, Lowkey opens minimized by default. **Settings → Keyboard → Show with physical keyboard** controls whether it stays visible. Lowkey remembers your expanded or minimized choice separately for use with and without a physical keyboard.

## Delete words with gestures

Tap Backspace to delete one character. Hold it and slide up to delete words before the cursor. Slide back down to restore words before releasing. The top of the slider deletes everything before the cursor; text after the cursor stays.

For more than 10,000 characters before the cursor, clearing all preceding text happens only on release. The slider does not start with a selection or in editors that do not report their text.

On Android 14 or later, slide left from the word slider into a supported text field to delete back to the word under your finger. Release to keep the deletion, or slide back over the keyboard to restore the text. Word positions are captured when the gesture starts, so a field that resizes can move away from those positions. Fields that do not report word positions show **Not supported here**.

On the minimized bar, hold Backspace to open its fan of actions. Slide to an action and release:

- Up deletes words using the slider.
- Up-left inserts a space; farther out clears clipboard history. **Undo clear** remains available until something new is copied.
- Left runs the field's action, such as Send or Search; farther out inserts a new line where supported.
- The outer choices open numbers or letters for the current field. The next field opens minimized again.

Release near Backspace to cancel. On the full keyboard, slide down from Backspace to minimize; slide back before releasing to cancel.

## Paste from clipboard history

The minimized bar shows up to ten recent clips. The full keyboard shows them when you are not typing a word. Swipe sideways to find older clips, tap to paste, or hold to preview. Images can be pasted only into fields that accept them.

Clips are captured while Lowkey is the selected keyboard, even when hidden. History lasts until the app process ends. Copied images are cached privately and removed when they leave history or the app restarts. Clips marked sensitive are not saved in history, and history is hidden in password fields.

To include recent screenshots, enable **Settings → Keyboard → Recent screenshots** and choose **Allow all** for photo access. This setting is off by default. It adds up to five screenshots from the last day; tapping one copies it into history and pastes it. With **Allow limited access**, Android hides screenshots you take later, so the switch stays off and Settings asks for all photos instead.

## Edit an image before pasting

Hold an image clip, slide up onto its preview, and release to open the image editor. Crop or annotate with drawing tools, shapes, highlights, and text. Pinch to zoom and pan; tap **Fit** to see the whole image.

Tap a shape with its drawing tool selected to move, resize, delete, or restyle it. Rectangles and circles can also rotate. Use Undo and Redo to revise edits.

The frosted color swatch makes tools blur. Blur does not guarantee that information is unreadable. For redaction, cover it with a filled solid rectangle.

Add an optional caption and tap **Attach** to return the edited image to the original field. The image is also copied to the clipboard if the field cannot accept it. Android's Back closes the editor without attaching.

## Choose light or dark

**Settings → Appearance → Theme** sets the look of the app and the keyboard together. **System**, the default, follows Android's dark mode and switches as soon as Android does. **Light** and **Dark** keep one look regardless of Android. Both use OpStudio's colours.

## Try settings and manage updates

Tap **Test keyboard** in the app's top bar to open a test field over the current page. Its draft stays available when you close and reopen it. The **Try it** page has additional editor types.

**Settings → Updates** lets you check now or turn automatic checks off. Downloads continue when you leave the app. If Android needs confirmation, tap the ready-to-install notification or **Install** on the Home card. Without notification permission, update notices appear in the app.
