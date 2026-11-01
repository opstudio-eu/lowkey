# Keyboard lab verification

Version 0.4.5. Pixel API 36, Android 16, emulator serial detected as emulator-5554. One emulator and one device owner throughout.

- `testDebugUnitTest`: 99 tests, zero failures or errors. Six new engine tests exercise expiry after dictation, reuse across the previous deadline, immediate release after finalization, shorter/longer retention changes, cancellation, and memory-pressure release.
- `assembleDebug`: passes, including verification of the packaged Moonshine ONNX Runtime patch.
- `lintDebug`: zero errors, one existing ChromeOsAbiSupport warning.
- The lab opens the real Slim Keyboard automatically. Tapping Q enters q; holding W enters 2. Saved T3 Code is inserted through the actual suggestion strip.
- The shared draft survives Models → Dictionary and opening the lab from Settings, Diagnostics, About, Home, and the original Try it page. Close/return controls and system Back leave the underlying page selected.
- Voice settings retain the exact setting-row rectangle before and after opening/closing the sheet, including when scrolled to the Voice section.
- Real Moonshine Small recognition uses the installed model and bundled debug WAV clips. Stop commits nonempty text; actual native resource status changes from in-use to loaded. The recognition errors remain visible.
- A 30-second idle policy releases the real model while the keys remain visible. Downloaded model files remain installed. A new capture reloads the model.
- Rotation recreates the activity. Draft content survives both rotations. Landscape uses a compact field and close control above the keys.
- Reversible recording settings are restored to microphone input and 2-minute retention afterward.

The video uses continuous Android screen recording with captions and measured interaction outlines added afterward. Capture actions and elapsed timestamps live in `scripts/demos/android_lab.py` and the generated timeline. `scripts/demos/render_android_lab.py` renders those timestamps. The planned script and independent accuracy/teaching reviews are in `keyboard-lab-script.md`.

Physical phone and microphone capture are not claimed. Timed options longer than 30 seconds are verified with virtual-time engine tests; the live video demonstrates 5-minute retention and immediate idle release.

Encoded video: 147.8 seconds, 1920 × 1080, silent H.264, 2,325,352 bytes. FFmpeg decodes through the end with no errors. T3 browser playback reaches `ended` at 147.8 seconds with no media error. Encoded overview/action/result samples show the actual typed draft and transcript; the first clip's existing recognition error, "Kotlin" → "cart in", remains visible. Captions occupy a separate side panel and green outlines mark measured touch targets. The closing caption and idle memory release are held on screen.

Independent encoded-frame review inspected all 84 samples across the 15 steps and 18 actions. It found stale outlines after layout transitions. The renderer now removes those outlines when dispatch starts and retains them only on stable keyboard targets. The MP4 and every review frame were regenerated, and the final video passes another full decode.
