# Clipboard strip verification

The keyboard shows the current system clipboard when no word precedes the cursor. A text clip pastes its exact characters at the selection. An image clip shows a thumbnail and uses Android's `commitContent` API with temporary URI read access. The editor must advertise support for the image's MIME type.

Word suggestions replace the clipboard as soon as a word is typed. A space, punctuation, or an empty field brings the clipboard back. Clearing the system clipboard clears the preview. Password and private fields hide the clipboard. Clips marked sensitive use a generic label and omit image thumbnails. The keyboard keeps the current clip in memory while visible and stores no clipboard history.

## Automated coverage

`EditorClipboardTest` checks exact text preservation and rejects a paste after the connection or field changes. The unit suite contains 113 tests.

The `clipboard` native probe launches a test editor in the separate test APK. Its private content provider grants access to a generated PNG through the system clipboard. The editor's image callback requests URI permission, reads the delivered image, and displays the result. The fixture and provider are absent from the shipped app.

The probe checks these behaviors:

- Idle fields show a clipboard preview and no dictionary suggestions.
- Text paste preserves case, emoji, newlines, and trailing whitespace.
- Typing hides the clipboard and shows word suggestions.
- A clipboard change during a word leaves word suggestions visible.
- Image clips show a thumbnail.
- Image paste reaches the editor callback with URI read permission.
- Unsupported and rejecting editors leave text unchanged and show feedback.
- Sensitive clips hide their contents until pasted.
- Password and private fields hide clipboard previews.
- Clearing the system clipboard removes the previous preview.

## Run the probe

Only one agent may own the emulator. Close the pinned agent-device session before native testing because Android allows one UI Automation controller at a time. Keep the emulator and T3 running.

```bash
	source "$ANDROID_ENV"  # puts adb and the Android SDK on PATH
	scripts/mem-guard.sh --need 3 --emulator-max 12
	./gradlew --no-daemon --max-workers=2 testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
	adb -s <owned-serial> install -r app/build/outputs/apk/debug/app-debug.apk
	adb -s <owned-serial> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
	scripts/check-keyboard-touch.sh <owned-serial> clipboard
```

The probe saves `clipboard-text-preview.png`, `clipboard-image-preview.png`, and `clipboard-image-pasted.png` in the debug app's cache. Screenshots, APKs, and build outputs stay out of git.

## Verification status

Verified on 2026-10-13 on the Pixel API 36 emulator. All clipboard probe checks pass, including an actual image commit, URI permission request, image decode, and visible received-image preview. T3's pinned agent-device CLI also confirms the image preview and pastes it with a real touch.

The 113 unit tests, `assembleDebug`, `assembleDebugAndroidTest`, and `lintDebug` pass. Lint has zero errors and the two existing warnings, `ConfigurationScreenWidthHeight` and `ChromeOsAbiSupport`.

The overlapping same-key touch regression also passes with two characters from two finger presses.

The native test script selects Slim explicitly because agent-device can switch to its text-entry helper. The probe opens the companion activity before the separate editor to restore IME binding after Android force-stops the target package at instrumentation startup. It refreshes accessibility nodes and waits for the actual word suggestion instead of assuming a fixed rendering delay.

Image support on physical phones, older Android versions, and third-party messaging apps remains unverified. Editors must accept the clip's image MIME type. Only the current system clip is shown, with no saved history.
