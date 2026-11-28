# Use Lowkey

For installation, see the [README](../README.md#install).

## Learn the gestures

Much of what Lowkey does is not written on its keys. Once you have chosen Lowkey as your keyboard in the app, a short tour opens by itself, one time. Each of its four steps asks you to do one thing in a practice field and ticks it off when you have done it: move the cursor by sliding from the red dot on G's corner (on most layouts), select text with Shift and a slide, delete whole words from Backspace, and dictate a sentence. The dictation step offers to set up voice typing if you haven't yet. You can skip a step or the whole tour, and it ends with a list of other things worth trying. It stays on **Home** under **Learn the gestures**. The tour only follows what you do on the keyboard, not what you type.

## Dictate text

1. Open **Voice** and follow the checklist: allow microphone access, then download a speech model. Before Android asks for the microphone, Lowkey explains that audio is processed on your phone, not saved and not uploaded; **Not now** skips the request. A completed download becomes active. Open **Voice → Speech models** to see which models are on your phone, switch with **Use**, download others, or delete ones you no longer need. The active model can't be deleted until you choose another.
2. Open a text field and tap the keyboard's microphone button.
3. Speak, then tap the **checkmark**. Lowkey transcribes on your phone and inserts the finished text.

Lowkey transcribes while you speak. With **Show words as you speak** on, provisional text appears underlined in the field at each pause. If you resume after a pause, Whisper can revise the latest part using audio from both sides of the pause. Tap the checkmark to insert the final text. If you've already paused and the preview is ready, Lowkey can reuse it.

With **Voice → Dictation settings → Dictate from the keys** enabled, keys return after transcription so you can make corrections. This is the default. Turn it off to use a separate voice mode; its settings for mode recall, auto-start and continuous dictation then appear on that page.

Switching to keys, hiding the keyboard, changing editor, moving the cursor, or opening the model picker cancels capture.

Under **Voice → Dictation settings → Keep model loaded**, choose how long to keep an idle model in memory. The default is two minutes. Releasing it does not delete the downloaded files. Recording and transcription do not expire, but changing the model or language can release it, as can Android memory pressure.

## Add keyboard languages

English (US) is built in. Dutch, Polish, English (UK), German, French, Spanish, Italian, Portuguese (Brazil and Portugal), Swedish, Danish, Norwegian (Bokmål), Finnish, Czech, Croatian, Slovenian, Romanian, Lithuanian, Latvian and Russian download on request: open **Settings → Languages & layouts → Add a language** and tap a language's download icon. Each word list is 0.4–5.2 MB. Lowkey downloads it from the Android Open Source Project at a fixed version, checks it against a known checksum, and converts it on your phone. A downloaded language is turned on at once. When your phone's own languages include one that no Lowkey keyboard has yet, Home offers it, such as **Add Dutch**: one tap downloads it and turns it on. Typing in a language that isn't on gets many correct words autocorrected into the other language. **Not now** hides the offer for that language.

Tap a language's name to switch to it. Lowkey remembers the choice and turns the language on if needed. Turn a language's switch off to remove all its layouts from the keyboard switcher. **English (US) can also be turned off**; its bundled word list stays on the phone. Keep at least one language on. The trash icon beside a downloaded language deletes its word list after confirmation. If it is your last active language, turn on another language first.

Each language has its own suggestions, autocorrect, layout and accents. Words typed without their accents get them back (`zolw` becomes `żółw`, `konnen` becomes `können`), and suggestions ignore missing accents. Polish also restores the accents of common words such as `juz` → `już`, and German capitalizes nouns typed in lowercase (`zeit` → `Zeit`). In Dutch, two common words written as one, with or without a linking `s`, `e` or `en`, count as a word even when the word list lacks the compound (`thuiswerkdag`, `verjaardagsfeest`), and a slip in one of its parts is repaired (`toetsenbrodinstellingen` → `toetsenbordinstellingen`).

To dictate in the language you type in, set **Voice → Dictation settings → Voice language** to **Same as keyboard**. Switching languages then reloads the speech model in the new language.

If Dutch was enabled in an earlier version, where it was built in, it downloads again by itself after the update.

The app interface is currently in English. Keyboard language and voice language are separate choices; changing either does not change the app interface.

Android's input-method menu shows one **Lowkey Keyboard** entry. It opens the language and layout selected in Lowkey; it does not list a separate fixed English or Dutch keyboard. Language entries under another keyboard app, such as Gboard, are controlled by that app.

## Choose layouts and type accents

Under **Settings → Languages & layouts**, each language that is on shows its letter layouts below its name, its own layout first. Languages written in the Latin alphabet also offer QWERTY, AZERTY, QWERTZ, Dvorak and Colemak. Tap a layout to turn it on or off. Turning on another layout of the language you type in also switches to it, and the language's line says which layout is in use. Keep at least one layout on. With the language-switch key enabled, tap it to cycle through Lowkey layouts. Hold Space to open Android's picker for keyboard apps.

The selected keyboard supplies the primary language for suggestions and autocorrect. Changing the letter layout does not change that language.

Hold a top-row letter to select a number. With the dedicated number row enabled, the small digit labels disappear from the letters; their long-press shortcuts remain available. A small corner mark identifies letters with accent choices, including when the number row is visible. Hold a letter with accents, slide to the desired character, and release to type it. Longer lists use a compact grid of at most five columns; slide sideways or up to choose. The language's own accents come first: on Polish, hold A for ą, L for ł or Z for ż. Slide off the choices and key to cancel. Shift and Caps Lock also apply to accented letters.

Number, phone, date and PIN fields open on a number pad: large digits with 0 below 8, Backspace, Space and the field's action on the right, and on the left and beside 0 the characters such fields need: - + , . and / in number fields, - + * # and . in phone fields, and - / : , and . in date fields. **?123** opens all symbols, where **123** returns to the pad, and **ABC** opens the letters. These fields always open on the keys, even when you last used voice typing.

When typing with two thumbs, touching the next key accepts an earlier pending tap, so letters stay in press order. A letter you still hold is typed before Shift, ?123, Backspace, the emoji key or the globe key acts. A tap counts even if your finger slides off the key before you lift it, and a tap in the space beside A or L types that letter. To use an accent while another finger types, wait for the accent popup to open first.

Space and punctuation apply ready autocorrections. If you type faster than the correction is calculated, the word stays as typed so the next key does not have to wait. Press Backspace immediately after a correction to restore what you typed. Add terms to the personal dictionary to use them in suggestions and preserve their casing in dictated text. Spoken-phrase replacement rules are opt-in.

Press Space twice quickly after a word to insert a period and a space. Backspace immediately afterwards undoes the period. This shortcut does not run in password, address, numeric or private fields.

After a space, Lowkey can suggest the next word from repeated wording in the current field and from saved multiword dictionary terms. It looks at up to 2,048 characters before the cursor, without storing that text. These are local phrase matches, not general language-model predictions.

To get predictions from your own typing, turn on **Settings → Keyboard → Learn from my typing** (off by default). Lowkey then counts which words you type after which one or two words. After a space it suggests the words you usually type next; while you type a word, it puts the completions you usually choose there first. A correction you undo with Backspace is remembered for good, not only for this session. What it learns stays on your phone, outside backups, and fades over time. Nothing is learned in password, private, email or web address fields. **Forget what Lowkey learned** removes it; your saved words stay. Sentence boundaries, private fields and address fields are excluded. When no prediction is available, the strip returns to the clipboard.

Word suggestions use up to three enabled languages whose dictionaries are installed: the current keyboard language, then the next two distinct languages in keyboard order, wrapping at the end. Multiple layouts of one language count once. This keeps memory use bounded. You can mix those languages without changing the key layout. A word recognized in another participating language is left alone by autocorrect; conflicting correction candidates also leave your typing unchanged. Lowkey follows the language you are writing in: once two of the last few words are clearly in one of these languages, its words come first in the suggestions and it wins close calls in autocorrect, whichever keyboard is in use. On the English keyboard, `probleen` after `ik heb het al gezien maar` becomes `probleem`. Dictionary-based autocorrect waits until all participating word lists are loaded. The middle suggestion still shows what Space will insert.

Common complete English and Dutch words can also suggest an emoji in the right-hand suggestion slot, for example **hi** or **hoi** for a waving hand, and **coffee** or **koffie** for a hot drink. In English, words from the emoji's own Unicode name also work, so **thumb** suggests a thumbs-up and **pancakes** a stack of pancakes. Plurals match too. Very common words such as **open** or **right**, and words shorter than four letters, are skipped unless they are keywords themselves, such as **dog**. In Dutch, a word that is an emoji's whole Dutch name also works, so **fiets** suggests a bicycle and **olifant** an elephant; everyday words such as **elf** or **man** are skipped. Typed emoticons work the same way: right after **;)** the strip offers 😉, and tapping it replaces the emoticon. Others are **:)**, **:D**, **xD**, **:(**, **:'(**, **:P**, **;P**, **:O**, **:\***, **:/**, **:|**, **B)**, **^^**, **^_^**, **(:**, **<3** and **</3**, also with a nose (**;-)**). An emoticon counts at the start of the text, after a space, or (with eyes, such as **;)**) straight after a word. These are local keyword matches, not sentence predictions. Short ambiguous prefixes such as **he** keep their word suggestions. Tap the emoji to replace the current word; Space never inserts the emoji automatically. Only emoji supported by the device are offered, and an emoji you choose joins Recent. Sensitive fields do not show these suggestions.

The top row of **Keyboard tools** (tap the three dots beside the microphone, or hold the microphone) has the field's own editing commands: **Select all**, **Cut**, **Copy**, **Paste** and **Undo**. The tools stay open, so you can select all and then cut or copy. Undo works where the app offers it: in ordinary Android text fields, not in every app.

Tap **Keyboard tools → Number row** to toggle a dedicated 1–0 row above the letters. The tile shows **On** or **Off**, and the menu closes so you can continue typing. This is the same setting as **Settings → Keyboard → Number row**. It is off by default and works with every language and keyboard mode. In Split mode the digits divide between the two halves. Symbol layouts keep their existing characters and fill the same panel height, so switching layers does not move the keyboard. The number row has the function-key colour, so it stands apart from the letters; turn off **Settings → Appearance → Shaded number row** to give it the letter-key colour instead.

## Manage your words

Open **Dictionary → My words** to add, search, edit or delete saved terms. **Match the spelling of my words** controls whether dictation restores their spelling. Under **Speech rules**, add phrases to replace after dictation. The main switch is off by default; each rule keeps its own on/off choice while the main switch is off. To move your words to another phone, see the next section.

## Back up your words and settings

Android doesn't back up Lowkey, so a new phone or a reinstall starts empty. Under **Settings → Backup**, **Export words and settings** saves your saved words, speech rules and all settings in one file, `lowkey-backup.json`, wherever you choose with Android's file picker. **Import words and settings** reads it back: words and rules you don't have yet are added, and the settings are replaced. Word lists, speech models and the keyboard theme's photo are not in the file; Lowkey says which languages to download again. The file is readable text, so keep it somewhere private.

## Resize, move and split the keyboard

The keyboard's text follows Android's font size up to 1.3 times its normal size; larger, labels would no longer fit the keys. For bigger keys, make the keyboard taller as described below. The keys always run left to right, also when the phone uses a right-to-left language.

Tap the three dots beside the microphone, or hold the microphone, to open quick settings. **Size & position** outlines the keyboard: drag the top edge to change height, the bottom edge to leave more or less space underneath, or the centre move handle to reposition it. In Normal and Floating modes, the left and right handles change width independently of height. A narrower Normal keyboard can also move horizontally while remaining docked. **Reset** restores the current mode's default size and position; the checkmark finishes. Completed drags are remembered, including when you press Android's Back button.

The same menu offers **One-handed**, **Floating** and **Split**, with an On/Off label and a checkmark for the active mode. Tap an active mode again to return to the normal keyboard. One-handed has side buttons to move it left/right or restore full width. Floating has a drag bar beneath it and leaves the surrounding app usable. Split is available in wide windows (at least 480 dp); narrower windows show it as unavailable. It shares the middle keys between both thumbs (G and V on QWERTY), and keeps one broad spacebar across the bottom. **Settings** opens the app's **Settings** tab; dictionary management stays in the companion app's **Dictionary** tab.

Lowkey remembers size, position and mode separately for small/large screens and portrait/landscape. **Settings → Keyboard → Split on large screens** chooses Split as the large-screen default; your manual choices are remembered. Turning it off does not cancel a manually chosen Split mode. **Resize and move** opens the controls in a test field. **Reset size and position** resets all saved dimensions without changing the selected modes.

When floating, the Backspace gesture can aim at text above the panel; it does not aim through or below it.

## Emoji and keyboard switching

The smiley beside Space opens an offline emoji panel. All emoji scroll in one list, starting with your recent ones; tap a category icon to jump to it, and tap an emoji to insert it. Hold an emoji with skin-tone variants to choose one. Recent shows up to 40 distinct choices, stored locally; sensitive fields do not show or update that history. Unsupported emoji are filtered using the device's font. Tap the magnifier at the start of the category bar to search: the letter keys then type into the search box above them instead of the text field, and the row above the keys shows matching emoji, best first. Search uses emoji names and keywords in English and, when Dutch is one of your suggestion languages, Dutch (**heart**, **lol**, **red hea**, **fiets**, **duim**), or an emoticon typed from **?123** (**;)** finds 😉); before you type, the row shows your recent emoji. Tap an emoji to insert it; you can insert several. **‹**, Done, the smiley key or Back returns to all emoji, and **✕** clears the search. Tap the **ABC** tab in the bottom-left corner to return to letters. Swiping down on the category bar also returns to letters, and Back closes the panel. Backspace removes a complete known emoji sequence.

With two or more languages or layouts enabled, hold the smiley to choose one; a small mark in its corner shows this. A row of choices opens over the key, the way accents do: your current layout sits over your finger and the next one is to its right. Slide to a layout and let go to switch, or slide away to cancel. If you let go without sliding, the choices stay open: tap one to switch, or tap anywhere else or press Back to close them.

A small red dot sits on the bottom-right corner of G (on QWERTY, where G, H, V and B meet), like the pointing stick on some laptop keyboards. Put a finger on the dot and slide to move the cursor: the keyboard then works as a trackpad, the letters on the keys fade, and nothing is typed. G itself is an ordinary key, and a tap just beside the dot types the key under your finger. Because the dot sits in the middle of the letters, there is room to slide in every direction. On a layout without G, such as ЙЦУКЕН, the dot sits on the corner of the key in G's place on QWERTY; the symbol and number keys have none. **Settings → Keyboard → Pointing stick** turns the dot off. Sideways, the cursor moves one character for about every 10 dp (a quarter of a key); a slow slide is exact and a fast one covers up to three times as much. Up or down, every key row is one line, in fields that take several lines. Mostly sideways movement never changes line, so a slanted slide stays on its line. When you run out of room, rest your finger there and the cursor keeps going: at the side of the screen, above the keyboard, or low on the bottom row for the lines below. The cursor stops at the start and end of the text. To select text instead, tap Shift first and then slide; Cut and Copy are in Keyboard tools. A slow slide works too. Hold Space without moving to open Android's keyboard-app picker directly. **Settings → Keyboard → Key beside Space** can replace the smiley with the language-switch key; holding Space works with either choice. Tap the language key to cycle through enabled Lowkey layouts, or hold it to open the same Android picker. Manage Lowkey's languages and layouts in Settings. Holding the comma also opens emoji, including when the language-switch key is selected.


## Minimize the keyboard

Swipe down over the suggestion strip to minimize the keyboard, or swipe up over the minimized bar to expand it. Both forms have a small horizontal grip. You can also open quick settings and choose **Show keyboard** or **Minimize keyboard**.

The microphone stays at the left in both forms. Tap it to start voice typing. Tap the three small dots beside it, or hold the microphone, for quick settings, including model selection and voice settings. During dictation, the cancel cross is on the left and the checkmark to stop and insert is on the right. Stopping returns to the previous keyboard form when keyboard-first dictation is enabled.

The minimized bar sits at the bottom, above Android's navigation area, even if you moved the full keyboard upward. Expanding restores the keyboard's saved size and position.

With a physical keyboard connected, Lowkey opens minimized by default. **Settings → Keyboard → Show with a physical keyboard** controls whether it stays visible. Lowkey remembers your expanded or minimized choice separately for use with and without a physical keyboard.

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

To clear the history and the current clip, tap the bin at the end of the full keyboard's clip row, or use the minimized bar's Backspace fan. Screenshots disappear from the row too. Until something new is copied, the full keyboard shows an undo arrow in its place and the fan offers **Undo clear**; both bring everything back.

Clips are captured while Lowkey is the selected keyboard, even when hidden. A clip stays in history for an hour after you copied or last pasted it; choose 10 minutes, 1 day or until the keyboard restarts under **Settings → Clipboard & images → Keep copied clips**. History never outlasts the app process. Copied images are cached privately and removed when they leave history or the app restarts. Text longer than 20,000 characters is not kept, though you can still paste it while it is the current clip. Clips marked sensitive are not saved in history, and history is hidden in password fields. Nothing you copy while a password or private (incognito) field has focus is saved either, and the clip row shows it as **Copied text**, without a preview.

Recent screenshots are on by default. They appear once Lowkey may read all your photos: open **Settings → Clipboard & images → Recent screenshots** and choose **Allow all**. Lowkey first explains what it reads and that a picture is uploaded only if you attach it to an AI prompt; **Continue** opens Android's photos dialog and **Not now** asks nothing. Use that switch to turn them off. Up to five screenshots from the last day appear beside your clips. With limited photo access, new screenshots stay hidden; Settings explains how to allow all photos. Choosing **Not now** turns this optional feature off.


## Edit an image before pasting

Hold an image clip, slide up onto its preview, and release to open the image editor. Crop or annotate with drawing tools, shapes, highlights, and text. Pinch to zoom and pan; tap **Fit** to see the whole image.

Tap a shape with its drawing tool selected to move, resize, delete, or restyle it. Rectangles and circles can also rotate. Use Undo and Redo to revise edits.

The frosted color swatch makes tools blur. Blur does not guarantee that information is unreadable. For redaction, cover it with a filled solid rectangle.

Add an optional caption and tap **Attach** to return the edited image to the original field. The image is also copied to the clipboard if the field cannot accept it. Android's Back closes the editor without attaching.

## AI on the keyboard (experimental)

Turn on **Settings → Experimental → AI on the keyboard** to get ✨ at the right of the keyboard's top strip. It opens a prompt box just above the keyboard. For now it creates images; more actions, such as cleaning up the text you wrote, will join it there as a choice in the same box.

Choose the **AI service** once; every action uses it:

- **OpenAI**: paste your API key from platform.openai.com. OpenAI charges that account for what you create.
- **Your own server**: an HTTPS address (without a password or `?` options) of a server that speaks OpenAI's API, such as one on your private network published with `tailscale serve`, plus an optional bearer **Access token**. The address may be the server or its `/v1` API address.

### Create an image

Tap ✨. A box opens just above the keyboard, over the app, which stays where it is. The box is an ordinary text field with the focus, so you type into it as into any app: Backspace and its word slider, suggestions, the clipboard row, a physical keyboard, Lowkey's mic and other voice-typing apps all work there. Tap **Create** (or Enter). The first time a request would go to the service you chose, Lowkey stops and says what leaves the phone: the prompt, the attached pictures and how many, a follow-up image link if the service answers with one, and your IP address, which that service sees. **Continue** sends it and is remembered for that service. **Cancel** keeps it on the phone. Switching service, or changing your server's address, asks again. You can read the same explanation under **Settings → Experimental → What a request sends**. Tap ✕, press Back or tap outside the box to close it; the words come back the next time you open it from the same field.

The chip at the top of the box shows the defaults, such as **OpenAI · Flare · Fast · ≈$0.01**. Tap it to change them just under it: switch between **OpenAI** and **Your server**, then pick the model and quality. With OpenAI, **Flare** is fast and cheapest, **Sunburst** has finer detail and readable text but costs more; each quality shows a rough price for one square image (your OpenAI usage page has the real cost). Your choice stays as the new default; **Done** folds the choices away and **AI settings** opens the settings page. With your own server, its address shows with **Edit**; set the **Server model** in Settings if it needs one. If the service isn't set up yet, the chip says so (**OpenAI · key needed**) and Create becomes **Add key** (or **Add server**), which opens that setting ready to paste; your words are kept.

Tap **Improve** under the box to have the AI rewrite your words into a clearer, more vivid prompt: it fills in the subject, setting, style and light where you left them open, and keeps words meant to appear in the image. The new prompt replaces yours, and Improve turns into **Undo**, which brings your own words back until you change the new ones. While it works, Improve shows **Stop**. With OpenAI it uses a small, fast text model; with your own server, set its **Text model** in Settings if it needs one.

### Change or combine pictures

Tap the picture button under the box to add up to four pictures to the prompt. Its tray offers **Photos** (Android's photo picker, which shares only the photos you pick) and the images in your clipboard row and recent screenshots; tapping an image in the keyboard's clipboard row while the box is open adds it too. The pictures show above the box, each with ✕ to take it out. Then say what to change, such as "make it night time" or "put the cat on the beach": Create sends the pictures with your words, and the service changes or combines them. To change an image you just made, tap the wand on its card: the box opens with that image already added.

Lowkey shrinks each picture to at most 1536 pixels on its long side before sending it.

Create closes the box, the app's field gets the keyboard back, and the image is made in the background, so you keep typing. ✨ keeps a ring while images are being made (with their number when there are several). Tap ✨ to start another one; images being made are listed on top of the box, each with **Stop** (the service may still charge for an image it had started). Up to four run at once.

When an image is ready, a card shows above the strip (if the box is open then, once you close it); tap its thumbnail for a larger preview. If a key or token was refused, the card offers **Edit key** (or **Edit token**) instead of **Try again**. Where the app takes images from keyboards, the card has **Insert**; nothing goes into the app until you tap it. It is also first in your clipboard row and on the system clipboard (unless you copied something else in the meantime), so where an app takes no images from keyboards, long-press its field and choose **Paste**. It expires from the clipboard row like a copied image; turn on **Save to Gallery** to also keep each image in Pictures/Lowkey. The flag on that card reports the image: Lowkey removes it from the clipboard (and tries to remove the Pictures/Lowkey copy) and sends the report from the app. The flag beside **Undo** does the same for an improved prompt and puts your own words back. You pick a reason and an optional note, then **Send report**, and confirm. Lowkey sends the report to Op Studio itself, over HTTPS; you stay in Lowkey and no email or other app is involved. **Include the content** is off until you turn it on; without it, only the reason, your note, the app version and the time are sent (the server also sees your IP address). Without a connection the report waits on the phone and Lowkey sends it automatically later; **Settings → Experimental → Content reports** lists your reports with **Send now** and **Delete**. Once a report is sent, its content is deleted from the phone.

Your own server receives `POST <address>/v1/images/generations` with JSON `prompt`, `n`, `quality` and, if set, `model`, and its answer gives `data[0].b64_json` (or an HTTPS `data[0].url`). With pictures added, the same fields go as `multipart/form-data` to `POST <address>/v1/images/edits`, with the pictures as `image` (one) or `image[]` (several). Improve sends `POST <address>/v1/chat/completions` with a system and a user message and, if set, the text `model`, and reads `choices[0].message.content`.

Only what is in the prompt box when you tap **Create** is sent: your words and the pictures you added, nothing else you type. The service also sees this phone's IP address. If it answers with a link instead of the image, Lowkey downloads that follow-up link. **Improve** sends only the words and how many pictures there are, not the pictures. With OpenAI, Lowkey also sends that text, and a finished image, to OpenAI's safety check with your key before anything is shown or copied. Your own server is not asked to look at the picture; an on-device list checks the words. If a check fails, the result is not shown. **Stricter safety checks** (on by default) also blocks other content that check flags. Sexual content involving a child is always blocked, and so are phrases for sexual content made without consent. Unsent words come back with the next prompt in the same field, not in another one. Reports are not part of a backup. The API key and token are encrypted on the phone and never part of a backup. ✨ is hidden in password, private and number fields.

## Choose light or dark

**Settings → Appearance → Theme** sets the look of the app and the keyboard together. **System**, the default, follows Android's dark mode and switches as soon as Android does. **Light** and **Dark** keep one look regardless of Android. Both use OpStudio's colours.

To give the keyboard its own look, open **Settings → Appearance → Keyboard theme** and choose **Custom**. Pick five colours (background, keys, function keys, text and accent) with the sliders or a six-digit hex code; Lowkey derives pressed keys, popups and the other shades from them.

Under **Advanced colors**, every other colour has its own row, grouped by where it appears: the number row (the function-key colour unless you change it), pressed and active keys, Enter, suggestions and secondary text, popups and clipboard, the voice row, and status colours such as warnings and the delete-all zone. Each row says **Automatic** (following the five colours) or **Custom**; tap it to set its own colour, and **Reset to automatic** to follow the five again. Changing one row changes only that colour.

**Background image → Choose image** opens Android's photo picker; Lowkey needs no storage permission. It keeps its own smaller, upright copy without the photo's location or other details, and fills the keyboard with it behind the keys, cropped from the middle. **Dimming** covers the image with the background colour to keep text readable; keys stay solid. **Remove image** goes back to the plain colour. If the copy goes missing, the keyboard uses the background colour. A docked keyboard shows the image across its full width, including one-handed and split layouts; a floating keyboard shows it only inside the floating panel.

The preview shows your draft, including a new image, and the keyboard changes only when you tap **Save theme**. **Cancel** drops the draft. Going back, switching tabs or choosing Standard asks before discarding unsaved changes; choose **Keep editing** to retain them. The saved image stays until you save without it. **Restore default colors** sets the five colours to the standard ones for the app theme and clears advanced colours. Lowkey lists text that falls below the WCAG AA contrast minimum, with the ratio. Over an image it checks the lightest and darkest parts after dimming, from a small copy of the image, so fine detail is not measured. It offers a fix that changes only the five colours, never your advanced colours, and with an image it can suggest more dimming; you can still save low-contrast themes. Choose **Standard** to return to the standard keyboard; your saved custom theme is kept for later. The app's own theme does not change.

## Find a setting

Type in **Search settings** at the top of the **Settings** tab to find any setting by name or by an everyday word: *dark* finds **Theme**, *haptic* finds **Vibrate on key press**, and *silence* finds **Stop after silence** under Voice. Each result shows where the setting lives. Tap a result to open its page, where the setting is scrolled into view and briefly highlighted. **Back** returns to your results; tapping a tab at the bottom clears the search.

A link to another page, also one on another tab, keeps the page you were on: **Back** returns to it, scrolled to where you left it. Tapping a tab at the bottom starts that tab at its top.

## Try settings and manage updates

Home offers **Try your keyboard** once Lowkey is set up. The Voice checklist also offers a test after setup. The draft stays available when you close and reopen the test field. **Settings → Try it → Advanced test fields** has additional editor types.

**Settings → About & updates → Check for updates** checks manually; the same page can turn automatic checks off, and ends with crash reports and voice sessions for troubleshooting. Downloads continue when you leave the app. If Android needs confirmation, tap the ready-to-install notification or the update action in the app. Without notification permission, update notices appear in the app.
