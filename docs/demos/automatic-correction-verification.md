# Automatic correction coverage, 0.4.10

The user reported that Slim barely applied automatic corrections. The previous 0.4.9 fix addressed dropped suggestion taps. This change addresses spelling targets, ranking, and correction when punctuation ends a word.

## Reproduced failures

The shipped English dictionary and original corrector reproduce these results on 0.4.9:

| Typed | Original result | Expected |
| --- | --- | --- |
| `autoxomplete` | unchanged | `autocomplete` |
| `autocoreecrs` | `autocorrect` | `autocorrects` |
| `tyoing` | `going` | `typing` |
| `omputer` | `imputed` | `computer` |
| `acomputer` | unchanged | `computer` |

The first two inputs came from the user's messages. The old AOSP list lacks `autocomplete` and `autocorrects`. For `tyoing`, `typing` has a weighted distance of 0.55, but the frequency bonus makes `going`, distance 1.55, win. The candidate filter discards `computer` when the first letter is missing or has an extra letter before it.

The actual keyboard also reproduces the punctuation gap:

```text
scripts/check-keyboard-touch.sh emulator-5554 autocomplete-boundaries
FAIL: java.lang.AssertionError: Autocomplete expected 'please the.', actual 'please teh.'
INSTRUMENTATION_CODE: 1
```

## Changed behavior

The built-in vocabulary adds autocomplete and autocorrect word forms. Ranking gives spelling distance more weight against word frequency. Candidate filtering accepts a missing or extra leading letter when the next two letters line up. Space and `. , ! ? ; :` use the same correction path.

Backspace immediately after a correction restores the original spelling and removes the Space or punctuation that triggered it. The restored word stays uncorrected for that keyboard session. Known words, names, code, URLs, email addresses, private fields, and the autocorrect setting retain their existing protections. The selected layout still chooses the dictionary. This is a spelling engine with no sentence-context model or next-word prediction.

## Verification

Run device probes sequentially on the single owned emulator. Build and install instructions are in [the clipboard checks](clipboard-verification.md).

```bash
	./gradlew --no-daemon --max-workers=2 testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
	scripts/check-keyboard-touch.sh <owned-serial> autocomplete-boundaries
	scripts/check-keyboard-touch.sh <owned-serial> autocomplete 1 30
	scripts/check-keyboard-touch.sh <owned-serial> autocomplete-refresh
	scripts/check-keyboard-touch.sh <owned-serial> correction
	scripts/check-keyboard-touch.sh <owned-serial> clipboard
```

The unit suite checks 45 English typo pairs, six Dutch typo pairs, known words, code, names, saved terms, and correction speed. The expanded `autocomplete` probe uses actual touches to type eight misspellings, presses Space without waiting for a suggestion at 30 ms between keys, checks the exact inserted text, and taps a completion. The punctuation probe checks corrections, immediate undo, respecting the undo on the next punctuation press, and preserving known words, code, URLs, and email addresses.

Verified on the Pixel API 36 emulator with version 0.4.10, version code 13, on 2026-10-15:

- All 115 unit tests pass.
- Both debug APK builds pass.
- Lint has zero errors and two existing warnings, `ConfigurationScreenWidthHeight` and `ChromeOsAbiSupport`.
- The punctuation probe that failed on 0.4.9 passes, including undo and rejection.
- All eight actual-touch Space correction cases pass at a 30 ms inter-key delay, along with tapping the completion for `receiv`.
- The suggestion-refresh regression probe passes and removes its temporary dictionary term.
- The native correction timing probe reports `[39, 43, 33, 14, 20, 30]` ms, within its 50 ms limit.
- The complete clipboard probe passes, including text and image paste, idle transitions, sensitive clips, password and private fields, and unsupported editors.

Physical-phone verification remains outstanding. These checks cover specific spelling and interaction regressions, not a measured comparison with FUTO.
