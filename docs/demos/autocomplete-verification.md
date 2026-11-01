# Autocomplete refresh regression, 0.4.9

The clipboard change in 0.4.8 cleared every word slot at the start of each suggestion refresh. Compose removed the pressed word button, cancelling its touch handler. Recreating the same candidate did not restore the finger press. A user could tap a visible completion and get no insertion.

Version 0.4.9 keeps the current word buttons mounted until replacement suggestions are ready. Entering a word still hides the clipboard immediately. An empty word clears word slots and shows the clipboard.

## Reproduction

The `autocomplete-refresh` native probe presses **receive** while the editor contains `please receiv`. An unrelated temporary dictionary term triggers the real suggestion refresh while the finger remains down. The probe releases the finger and checks the editor's exact text. It deletes the temporary term in `finally`.

The original probe and its narrowed version fail on 0.4.8:

```text
scripts/check-keyboard-touch.sh emulator-5554 autocomplete-refresh
FAIL: java.lang.AssertionError: Autocomplete expected 'please receive ', actual 'please receiv'
INSTRUMENTATION_CODE: 1
```

The narrowed check confirmed that the editor stayed focused and the candidate was still **receive**. Keeping the word buttons mounted makes the same completion tap succeed.

## Coverage

`autocomplete` types `teh`, `recieve`, and `fucntion` through real key touches and checks their Space corrections. It also taps the completion for `receiv`. At a 30 ms inter-key delay, it presses Space immediately instead of waiting for the corrected candidate to appear. At 100 ms, it also checks the offered correction before pressing Space.

```bash
	scripts/check-keyboard-touch.sh <owned-serial> autocomplete-refresh
	scripts/check-keyboard-touch.sh <owned-serial> autocomplete 1 100
	scripts/check-keyboard-touch.sh <owned-serial> autocomplete 1 30
```

Device runs are sequential on the single owned Pixel API 36 emulator. The build and install steps are the same as the [clipboard checks](clipboard-verification.md).

Verified on 2026-10-15 with installed version 0.4.9, version code 12:

- The refresh regression probe passes.
- The typing probe passes at both 100 ms and 30 ms inter-key delays.
- The full clipboard probe passes, including text and image paste, idle transitions, sensitive clips, and unsupported editors.
- The overlapping same-key check inserts both expected characters.
- All 113 unit tests, both debug APK builds, and lint pass. Lint has zero errors and two existing warnings.

This change addresses dropped completion taps and transiently empty suggestion slots. It does not replace the word-ranking engine or add next-word prediction. Broader word-choice complaints need typed text, the offered result, the expected word, and the language. Physical-phone verification remains outstanding.
