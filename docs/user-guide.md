# Use Lowkey

For installation, see the [README](../README.md#install).

## Dictate text

1. Open **Voice** and follow the checklist: allow microphone access, then download a speech model. A completed download becomes active. Open **Change speech model** to choose another model; selecting it alone does not download or activate it.
2. Open a text field and tap the keyboard's microphone button.
3. Speak, then tap the **checkmark**. Lowkey transcribes on your phone and inserts the finished text.

With **Voice → Dictation settings → Dictate from the keys** enabled, keys return after transcription so you can make corrections. This is the default. Turn it off to use mode recall, auto-start, or continuous dictation settings.

Switching to keys, hiding the keyboard, changing editor, moving the cursor, or opening the model picker cancels capture.

Under **Voice → Storage & performance → Keep model loaded**, choose how long to keep an idle model in memory. The default is two minutes. Releasing it does not delete the downloaded files. Recording and transcription do not expire, but changing the model or language can release it, as can Android memory pressure.

## Add keyboard languages

English (US) is built in. Dutch, Polish, English (UK), German, French, Spanish, Italian, Portuguese (Brazil and Portugal), Swedish, Danish, Norwegian (Bokmål), Finnish, Czech, Croatian, Slovenian, Romanian, Lithuanian, Latvian and Russian download on request: open **Settings → Languages & layouts → Add a language** and tap a language's download icon. Each word list is 0.4–5.2 MB. Lowkey downloads it from the Android Open Source Project at a fixed version, checks it against a known checksum, and converts it on your phone. A downloaded language is turned on at once.

Tap a language's name to switch to it. Lowkey remembers the choice and turns the language on if needed. Turn a language's switch off to remove all its layouts from the keyboard switcher. **English (US) can also be turned off**; its bundled word list stays on the phone. Keep at least one language on. The trash icon beside a downloaded language deletes its word list after confirmation. If it is your last active language, turn on another language first.

Each language has its own suggestions, autocorrect, layout and accents. Words typed without their accents get them back (`zolw` becomes `żółw`, `konnen` becomes `können`), and suggestions ignore missing accents. Polish also restores the accents of common words such as `juz` → `już`, and German capitalizes nouns typed in lowercase (`zeit` → `Zeit`).

To dictate in the language you type in, set **Voice → Dictation settings → Voice language** to **Same as keyboard**. Switching languages then reloads the speech model in the new language.

If Dutch was enabled in an earlier version, where it was built in, it downloads again by itself after the update.

The app interface is currently in English. Keyboard language and voice language are separate choices; changing either does not change the app interface.

Android's input-method menu shows one **Lowkey Keyboard** entry. It opens the language and layout selected in Lowkey; it does not list a separate fixed English or Dutch keyboard. Language entries under another keyboard app, such as Gboard, are controlled by that app.

## Choose layouts and type accents

Enable layouts under **Settings → Languages & layouts → Layouts**, then choose **Keyboard in use** on that page to select the one you want. Each language on your phone is listed with its own layout first. Languages written in the Latin alphabet also offer QWERTY, AZERTY, QWERTZ, Dvorak and Colemak. With the language-switch key enabled, tap it to cycle through Lowkey layouts. Hold Space to open Android's picker for keyboard apps.

The selected keyboard supplies the primary language for suggestions and autocorrect. Changing the letter layout does not change that language.

Hold a top-row letter to select a number. With the dedicated number row enabled, the small digit labels disappear from the letters; their long-press shortcuts remain available. A small corner mark identifies letters with accent choices, including when the number row is visible. Hold a letter with accents, slide to the desired character, and release to type it. Longer lists use a compact grid of at most five columns; slide sideways or up to choose. The language's own accents come first: on Polish, hold A for ą, L for ł or Z for ż. Slide off the choices and key to cancel. Shift and Caps Lock also apply to accented letters.

When typing with two thumbs, touching the next letter, Space or Enter accepts an earlier pending tap so letters stay in press order. To use an accent while another finger types, wait for the accent popup to open first; an already accepted tap cannot be cancelled by sliding away afterward.

Space and punctuation apply ready autocorrections. If you type faster than the correction is calculated, the word stays as typed so the next key does not have to wait. Press Backspace immediately after a correction to restore what you typed. Add terms to the personal dictionary to use them in suggestions and preserve their casing in dictated text. Spoken-phrase replacement rules are opt-in.

Press Space twice quickly after a word to insert a period and a space. Backspace immediately afterwards undoes the period. This shortcut does not run in password, address, numeric or private fields.

After a space, Lowkey can suggest the next word from repeated wording in the current field and from saved multiword dictionary terms. It looks at up to 2,048 characters before the cursor, without storing that text. These are local phrase matches, not general language-model predictions. Sentence boundaries, private fields and address fields are excluded. When no prediction is available, the strip returns to the clipboard.

Word suggestions use up to three enabled languages whose dictionaries are installed: the current keyboard language, then the next two distinct languages in keyboard order, wrapping at the end. Multiple layouts of one language count once. This keeps memory use bounded. You can mix those languages without changing the key layout. A word recognized in another participating language is left alone by autocorrect; conflicting correction candidates also leave your typing unchanged. Dictionary-based autocorrect waits until all participating word lists are loaded. The middle suggestion still shows what Space will insert.

Common complete English and Dutch words can also suggest an emoji in the right-hand suggestion slot, for example **hi** or **hoi** for a waving hand, and **coffee** or **koffie** for a hot drink. In English, words from the emoji's own Unicode name also work, so **thumb** suggests a thumbs-up and **pancakes** a stack of pancakes. Plurals match too. Very common words such as **open** or **right**, and words shorter than four letters, are skipped unless they are keywords themselves, such as **dog**. These are local keyword matches, not sentence predictions. Short ambiguous prefixes such as **he** keep their word suggestions. Tap the emoji to replace the current word; Space never inserts the emoji automatically. Only emoji supported by the device are offered, and an emoji you choose joins Recent. Sensitive fields do not show these suggestions.

Tap **Keyboard tools → Number row** to toggle a dedicated 1–0 row above the letters. The tile shows **On** or **Off**, and the menu closes so you can continue typing. This is the same setting as **Settings → Keyboard → Number row**. It is off by default and works with every language and keyboard mode. In Split mode the digits divide between the two halves. Symbol layouts keep their existing characters and fill the same panel height, so switching layers does not move the keyboard.

## Manage your words

Open **Dictionary → My words** to add, search, edit or delete saved terms. **Match the spelling of my words** controls whether dictation restores their spelling. Under **Speech rules**, add phrases to replace after dictation. The main switch is off by default; each rule keeps its own on/off choice while the main switch is off. Import and export are not available.

## Resize, move and split the keyboard

Hold the microphone to open quick settings. **Size & position** outlines the keyboard: drag the top edge to change height, the bottom edge to leave more or less space underneath, or the centre move handle to reposition it. In Normal and Floating modes, the left and right handles change width independently of height. A narrower Normal keyboard can also move horizontally while remaining docked. **Reset** restores the current mode's default size and position; the checkmark finishes. Completed drags are remembered, including when you press Android's Back button.

The same menu offers **One-handed**, **Floating** and **Split**, with an On/Off label and a checkmark for the active mode. Tap an active mode again to return to the normal keyboard. One-handed has side buttons to move it left/right or restore full width. Floating has a drag bar beneath it and leaves the surrounding app usable. Split is available in wide windows (at least 480 dp); narrower windows show it as unavailable. It shares the middle keys between both thumbs (G and V on QWERTY), and keeps one broad spacebar across the bottom. **Settings** opens the keyboard settings; dictionary management stays in the companion app's **Dictionary** tab.

Lowkey remembers size, position and mode separately for small/large screens and portrait/landscape. **Settings → Keyboard → Automatically split on large screens** chooses Split as the large-screen default; your manual choices are remembered. Turning it off does not cancel a manually chosen Split mode. **Size and position** opens the controls in the keyboard lab. **Reset size and position** resets all saved dimensions without changing the selected modes.

When floating, the Backspace gesture can aim at text above the panel; it does not aim through or below it.

## Emoji and keyboard switching

The smiley beside Space opens an offline emoji panel. Choose a category, scroll its grid, and tap an emoji to insert it. Hold an emoji with skin-tone variants to choose one. Recent shows up to 40 distinct choices, stored locally; sensitive fields do not show or update that history. Unsupported emoji are filtered using the device's font. **ABC** returns to letters, and Back closes the panel. Backspace removes a complete known emoji sequence.

With two or more languages or layouts enabled, hold the smiley to choose one; a small mark in its corner shows this. A row of choices opens over the key, the way accents do: your current layout sits over your finger and the next one is to its right. Slide to a layout and let go to switch, or slide away to cancel. If you let go without sliding, the choices stay open: tap one to switch, or tap anywhere else or press Back to close them.

Hold Space to open Android's keyboard-app picker directly. **Settings → Keyboard → Key beside Space** can replace the smiley with the language-switch key; holding Space works with either choice. Tap the language key to cycle through enabled Lowkey layouts, or hold it to open the same Android picker. Manage Lowkey's languages and layouts in Settings. Holding the comma also opens emoji, including when the language-switch key is selected.


## Minimize the keyboard

Swipe down over the suggestion strip to minimize the keyboard, or swipe up over the minimized bar to expand it. Both forms have a small horizontal grip. You can also hold the microphone and choose **Show keyboard** or **Minimize keyboard** in quick settings.

The microphone stays at the left in both forms. Tap it to start voice typing; hold it for quick settings, including model selection and voice settings. Three small dots beside the microphone show that it opens this menu when held. During dictation, the cancel cross is on the left and the checkmark to stop and insert is on the right. Stopping returns to the previous keyboard form when keyboard-first dictation is enabled.

The minimized bar sits at the bottom, above Android's navigation area, even if you moved the full keyboard upward. Expanding restores the keyboard's saved size and position.

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

Recent screenshots are on by default. They appear once Lowkey may read all your photos: open **Settings → Clipboard & images → Recent screenshots** and choose **Allow all**. Use that switch to turn them off. Up to five screenshots from the last day appear beside your clips. With limited photo access, new screenshots stay hidden; Settings explains how to allow all photos. Choosing **Not now** turns this optional feature off.


## Edit an image before pasting

Hold an image clip, slide up onto its preview, and release to open the image editor. Crop or annotate with drawing tools, shapes, highlights, and text. Pinch to zoom and pan; tap **Fit** to see the whole image.

Tap a shape with its drawing tool selected to move, resize, delete, or restyle it. Rectangles and circles can also rotate. Use Undo and Redo to revise edits.

The frosted color swatch makes tools blur. Blur does not guarantee that information is unreadable. For redaction, cover it with a filled solid rectangle.

Add an optional caption and tap **Attach** to return the edited image to the original field. The image is also copied to the clipboard if the field cannot accept it. Android's Back closes the editor without attaching.

## Choose light or dark

**Settings → Appearance → Theme** sets the look of the app and the keyboard together. **System**, the default, follows Android's dark mode and switches as soon as Android does. **Light** and **Dark** keep one look regardless of Android. Both use OpStudio's colours.

To give the keyboard its own look, open **Settings → Appearance → Keyboard theme** and choose **Custom**. Pick five colours (background, keys, function keys, text and accent) with the sliders or a six-digit hex code; Lowkey derives pressed keys, popups and the other shades from them.

Under **Advanced colors**, every other colour has its own row, grouped by where it appears: pressed and active keys, Enter, suggestions and secondary text, popups and clipboard, the voice row, and status colours such as warnings and the delete-all zone. Each row says **Automatic** (following the five colours) or **Custom**; tap it to set its own colour, and **Reset to automatic** to follow the five again. Changing one row changes only that colour.

**Background image → Choose image** opens Android's photo picker; Lowkey needs no storage permission. It keeps its own smaller, upright copy without the photo's location or other details, and fills the keyboard with it behind the keys, cropped from the middle. **Dimming** covers the image with the background colour to keep text readable; keys stay solid. **Remove image** goes back to the plain colour. If the copy goes missing, the keyboard uses the background colour. A docked keyboard shows the image across its full width, including one-handed and split layouts; a floating keyboard shows it only inside the floating panel.

The preview shows your draft, including a new image, and the keyboard changes only when you tap **Save theme**. **Cancel** drops the draft. Going back, switching tabs or choosing Standard asks before discarding unsaved changes; choose **Keep editing** to retain them. The saved image stays until you save without it. **Restore default colors** sets the five colours to the standard ones for the app theme and clears advanced colours. Lowkey lists text that falls below the WCAG AA contrast minimum, with the ratio. Over an image it checks the lightest and darkest parts after dimming, from a small copy of the image, so fine detail is not measured. It offers a fix that changes only the five colours, never your advanced colours, and with an image it can suggest more dimming; you can still save low-contrast themes. Choose **Standard** to return to the standard keyboard; your saved custom theme is kept for later. The app's own theme does not change.

## Try settings and manage updates

Home offers **Try your keyboard** once Lowkey is set up. The Voice checklist also offers a test after setup. The draft stays available when you close and reopen the test field. **Settings → Try it → Advanced test fields** has additional editor types.

**Settings → Updates → Check for updates** checks manually; the same page can turn automatic checks off. Downloads continue when you leave the app. If Android needs confirmation, tap the ready-to-install notification or the update action in the app. Without notification permission, update notices appear in the app.
