# Delete and dictate verification

Version 0.4.6. Pixel API 36, Android 16, detected serial emulator-5554. One emulator and one device owner.

- `testDebugUnitTest`: 108 tests, zero failures/errors. New tests cover accelerating repeat, release cancellation, invalid-editor termination, full-field clearing despite truncated surrounding text, changed connection/field guards, unsupported Select all, final completion ordering, duplicate/stale final events, and no-speech completion.
- `assembleDebug` passes, including packaged Moonshine runtime verification. `lintDebug` reports zero errors and one existing ChromeOsAbiSupport warning.
- Actual key presses populate a 145-character multiline test field. Holding Backspace for 2.3 seconds deletes 28 characters and opens the clear offer. Repeating stops while the offer is visible; Cancel keeps the 117 remaining characters. A second hold and Clear all empty the whole field.
- The confirmation lives in the IME window, remains accessible, and preserves the host editor connection. Clear is bound to the captured connection and field metadata. Changing editor or hiding the keyboard closes the offer.
- Actual Moonshine Small recognition uses installed weights and bundled debug WAV audio. Tapping the mic starts the compact recording row. Stop finalizes, inserts nonempty text, then brings back keys. A Q tap and Backspace perform an immediate correction without changing the prior transcript.
- Closing the lab during a second recording and reopening shows keys rather than automatically recording again. Session tests reject stale final callbacks.
- Microphone input is restored afterward. Keyboard-first dictation stays enabled by default, and the keyboard lab remains open for inspection.

Physical phone and microphone capture are not claimed. The screenshots preserve the existing clip recognition error, Kotlin → cart in. Source architecture references remain in REFERENCES.md; these changes import no code from the reference projects.
