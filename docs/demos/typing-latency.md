# Typing latency verification

Baseline: `e4db804` (0.10.0). Changes: `fix/typing-latency`.

## Changes

- One bounded text snapshot supplies the word, token, sentence and saved-phrase
  context for each suggestion refresh. Identical editor echoes keep the existing
  suggestion request instead of cancelling and restarting it.
- Edits, changed requests and input sessions invalidate obsolete results. Worker
  completion checks a generation rather than making more synchronous editor reads.
- Space and punctuation use a matching cached correction. A cache miss keeps the
  literal word; it neither scans the dictionary on the input thread nor corrects
  the word after subsequent edits. Corrections are calculated before optional
  suggestion ranking, and obsolete searches check cancellation within candidate scans.
- Overlapping typing keys commit pending taps in press order. An established
  accent/number hold stays independent. Starting another tap before the hold
  opens accepts the earlier tap; sliding away afterward cannot retract it.
- The keyboard layout is remembered across suggestion-only recompositions.

## Measurement

Both APKs used the same Android 15 emulator, debug configuration and test runner.
The preview application ID kept the release installation separate. Each version
ran three trials of 20 alternating letter taps with capitalization, suggestions
and autocorrect enabled. The runner waits for the published English dictionary
(160,641 entries), rather than relying on a fixed startup delay.

`LatencyFixtureActivity` is a separate-process editor that delays each synchronous
text-before-cursor or capitalization query by 5 ms. It counts those queries and
records `TextWatcher` timestamps. The reported latency is the difference between
the injected finger-up timestamp and the editor's text-change timestamp.

| Metric | Baseline | Changed |
| --- | ---: | ---: |
| Synchronous queries across 60 letters | 667 | 180 |
| Queries per letter | 11.12 | 3.00 |
| Median text-change latency | 23 ms | 8 ms |
| Per-trial medians | 19.5, 22, 28.5 ms | 8.5, 6.5, 12 ms |
| 95th percentile, nearest rank | 44 ms | 43 ms |
| Maximum | 52 ms | 88 ms |

This demonstrates less synchronous work (73% fewer measured queries) and lower
typical latency in this deliberately slow editor. It does **not** demonstrate that
occasional stalls are eliminated: the tail did not materially improve and the
changed version had a larger single maximum. These are 60 samples per version,
not phone measurements, and `TextWatcher` observes text application, not the
frame in which pixels become visible. Emulator scheduling, debug overhead and
other device work remain uncontrolled sources of variation.

Early exploratory trials used a fixed warm-up and sometimes ran before the full
dictionary was published. They are excluded from the table above.

## Reproduce

Build both revisions with the same JDK 21, SDK, debug key and test runner:

```sh
./gradlew -PuiPreview=true testDebugUnitTest assembleDebug assembleDebugAndroidTest
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

Select Lowkey UI Preview as the active keyboard. Close any competing UI automation
session before running the instrumentation. The runner restores its temporary
keyboard preferences before finishing.

```sh
adb -s "$ANDROID_SERIAL" shell am instrument -w \
  -e probe latency -e typingDefaults 1 \
  com.rheerkens.androidkeyboard.uipreview.test/com.rheerkens.androidkeyboard.test.KeyboardTouchProbe
```

Repeat three times on each APK. Match each `releases` timestamp to the same-index
`changes` timestamp; report the distribution as well as the read count. The test
asserts that all 20 letters arrive in the correct order.

Other targeted probes use the same command with a different `probe` argument:
`rollover`, `numbers`, `accents`, `autocomplete-boundaries`, `autocomplete-miss`
and `autocomplete-stale`. `hold-overlap` keeps an established number popup open
while another finger types; `same-key-reverse` reverses the release order of two
fingers on the same key.

## Regression results

- `testDebugUnitTest`: 479 tests, no failures. Covers bounded context parsing,
  a single editor read reused by multiple context checks, cancellation inside
  dictionary scans, press order and cancelled pending taps.
- `lintDebug`: passed, with existing warnings and no errors.
- `rollover`: the baseline produced `ds` instead of `sd`; the changed APK
  produced the expected press order when the second finger released first.
- `numbers`: all ten long-press digits, preview-before-release and cancellation
  passed.
- `accents`: selection, highlight placement, cancellation and shifted accents
  passed. The probe follows the visible cells and current theme colour rather
  than the obsolete single-row geometry and fixed palette.
- `hold-overlap`: the established popup survives typing with a second finger,
  and inserts its digit only when released.
- `same-key-reverse`: all ten characters arrive exactly once with two fingers
  overlapping on the same key and releasing in reverse order.
- `autocomplete-boundaries`: cached punctuation corrections, immediate undo,
  rejection after undo, known words, code and URLs passed.
- `autocomplete-miss`: all Default dispatcher workers are deliberately blocked.
  The baseline still synchronously corrects the word and fails the literal-input
  assertion. The changed APK keeps the literal word and never changes it later.
- `autocomplete-stale`: replacing the input and moving the cursor discard obsolete
  work; a correction in the middle of existing text can still be undone.

The raw comparison measurements and screenshots are local test artifacts, not
repository assets. No release was published or installed on a physical phone.
