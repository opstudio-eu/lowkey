# Lowkey

Lowkey is a slim Android keyboard with on-device voice typing. Speech recognition, suggestions, and autocorrect run on your phone. Lowkey uses the internet to download speech models and keyboard languages, and to check for and download app updates. If you turn on the experimental AI on the keyboard, it also sends the prompts you type for it to the AI service you choose.

**[Download Lowkey for Android](https://github.com/opstudio-eu/lowkey/releases/latest/download/lowkey.apk)** · Android 9 or later, 64-bit ARM

## Install

1. Download and open the APK. Allow your browser to install apps when Android asks.
2. Open **Lowkey** and follow the setup steps to enable and choose the keyboard. Voice setup is optional.
3. Open **Voice**, allow microphone access and download a speech model to use voice typing offline.

Lowkey is a personal project, available as source and signed APKs. It is not on the Play Store.

The app checks for updates automatically. Use **Settings → About & updates → Check for updates** to check manually. Android may ask you to allow Lowkey to install updates and confirm the installation. Updates keep your settings, dictionary, and downloaded models. Release notes and checksums are on the [releases page](https://github.com/opstudio-eu/lowkey/releases/latest).

## Features

- Voice typing with downloadable English and multilingual speech models.
- Suggestions and autocorrect across up to three enabled, installed keyboard languages, following the language you are writing in, with English bundled and Dutch, Polish and 18 more languages to download, plus a personal dictionary for saved terms and dictation casing, and optional next-word predictions learned on the phone from your own typing.
- Each language's own layout (such as German QWERTZ with Ü, Ö and Ä, or Russian ЙЦУКЕН), plus QWERTY, AZERTY, QWERTZ, Dvorak, and Colemak, with long-press numbers and accents, and a number pad in number, phone and date fields.
- A minimized keyboard for voice input or use with a physical keyboard, gestures for deleting words, a pointing stick, a red dot on G's corner (on most layouts) that turns the keyboard into a trackpad for the cursor (sideways for characters, up and down for lines, with Shift to select), and select all, cut, copy, paste and undo in Keyboard tools.
- Direct resizing and positioning, one-handed and floating modes, a split keyboard for wider screens, and an optional number row.
- An offline emoji panel with categories, skin-tone variants, and recent choices, plus tap-to-insert emoji suggestions for English and Dutch words (in English also from emoji names, such as thumb for 👍). Hold the emoji key to switch between your keyboard languages.
- Clipboard history for text and images, with previews and an image editor for cropping, annotation, and blur.
- Experimental: AI on the keyboard (✨), through OpenAI with your own API key or your own OpenAI-compatible server. Describe an image in a box just above the keys; it is made in the background and lands in your clipboard, ready to insert.
- A short tour that teaches the gestures by having you do them, and a built-in test field to try the keyboard without leaving settings.
- Export and import of your saved words, speech rules and settings in one file, for a new phone or a reinstall.
- Light and dark themes in OpStudio's colours, following the system by default, plus an optional custom keyboard theme: your own colours for every part of the keyboard and an optional background photo, with a live preview and contrast checks.

## Documentation

- [User guide](docs/user-guide.md): voice typing, languages and layouts, gestures, clipboard, and image editing.
- [Build and contribute](CONTRIBUTING.md): development setup, checks, signing, and releases.

## License

[Apache License 2.0](LICENSE). Copyright 2026 OpStudio. Contributions use the same license.

The OpStudio name and logo are trademarks of OpStudio and are not covered by the Apache License. Forks and modified versions should remove the logo.

Bundled components retain their own licenses. See the [third-party notices](app/src/main/assets/licenses/README.txt), also available in the app under **About & updates**.
