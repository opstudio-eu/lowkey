# Typing and number previews, 0.4.8

Holding a top-row letter now opens a large popup with its number. Release to insert the digit; a short tap inserts the letter. Cancelling the hold inserts nothing. The popup takes no extra keyboard height. Its selected number is also exposed in the key's accessibility description.

## Missed-touch regression

The original `detectTapGestures` handler treated two overlapping fingers on the same key as one gesture. A real touch check against the IME reproduced the loss with two presses, without voice input:

```text
scripts/check-keyboard-touch.sh emulator-5554 same-key 1 100
probe=same-key expected=2 actual=1 text=S
INSTRUMENTATION_CODE: 1
```

The replacement tracks each pointer separately, including its release, cancellation and hold timer. The same test now enters both characters. Stress checks also pass:

| Touch check | Expected characters | Actual characters |
| --- | ---: | ---: |
| Same key, two overlapping fingers | 2 | 2 |
| Same key, reversed release order | 2 | 2 |
| Same key, 30 overlapping pairs | 60 | 60 |
| Two different keys, 30 overlapping pairs | 60 | 60 |
| Rapid alternating taps | 100 | 100 |

These checks inject real touchscreen events into the IME. They do not insert strings through a helper keyboard. The 100-tap run took 7.44 seconds of injection; controller overhead is included, so that is not an app latency measurement.

## Spelling latency

The Space handler can run uncached autocorrection on the main thread. The spelling engine's first search lazily built a candidate index, and each query created sequences, candidate pairs and thousands of edit-distance arrays. Its inner loop also repeated boxed keyboard-neighbour lookups.

The candidate index is now built with the word list. Cold spelling initialization runs on the dictionary loader thread before the list is published. The search uses a precomputed neighbour table, visits eligible candidates directly, and reuses its edit-distance rows. Candidate order, scoring and conservative correction rules remain the same.

A native probe measures the actual correction function used by Space against the full 160,631-word English list. Every call runs on the main thread. It includes the first correction after a fresh app process and fails if any call exceeds 50 ms on this test host.

| Input | Before, ms | After, ms |
| --- | ---: | ---: |
| First `recieve` | 365 | 32 |
| `functon` | 113 | 33 |
| `langauge` | 87 | 25 |
| `typng` | 42 | 12 |
| `keybord` | 68 | 19 |
| Repeated `recieve` | 87 | 27 |

The preceding passing run was also below the budget, at 13–34 ms. These are Pixel API 36 emulator measurements on a development host, not timings from a physical phone and not total key-to-screen latency. Further lag or missed touches on the phone still need verification there.

## Number checks

The native probe holds Q–P, verifies the selected digit is visible while the field is unchanged, releases, then checks the exact result. All ten yield `1234567890`, with no letters or duplicates. It also verifies the preview closes after release and that cancelling a held Q inserts nothing. The saved screenshot shows a large 2 over W while the editor still contains only the previously released 1.

T3's pinned agent-device CLI additionally verifies ordinary taps, a held number, Backspace, and swipe-up clear in the final installed app. The keyboard is left open in the thread's Device panel.

## Run the native probes

Only one agent may own the device. Close your own pinned agent-device session before these probes because Android allows one UI Automation controller at a time. Keep the emulator and T3 running. Reopen the app with the pinned CLI after native testing.

```bash
source "$ANDROID_ENV"  # puts adb and the Android SDK on PATH
scripts/mem-guard.sh --need 3 --emulator-max 12
./gradlew --no-daemon assembleDebug assembleDebugAndroidTest --max-workers=2
adb -s <owned-serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <owned-serial> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
scripts/check-keyboard-touch.sh <owned-serial> same-key 1 100
scripts/check-keyboard-touch.sh <owned-serial> correction
scripts/check-keyboard-touch.sh <owned-serial> numbers
```

`testDebugUnitTest` passes all 111 tests, including correction-quality and dictionary cases. `assembleDebug`, `assembleDebugAndroidTest` and `lintDebug` pass. Lint has zero errors and the same two warnings in unchanged code, `ConfigurationScreenWidthHeight` and `ChromeOsAbiSupport`. APKs, screenshots and build outputs stay out of git and are shared only over Tailscale.
