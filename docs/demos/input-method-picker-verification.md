# Android input-method picker

## Problem and fix

Lowkey's manifest advertised fixed English and Dutch subtypes. Android kept offering those entries even when the corresponding language was disabled in Lowkey. Selecting a system subtype did not update Lowkey's independently stored keyboard choice.

The registration now exposes one language-neutral keyboard subtype. Android selects the Lowkey app; Lowkey's existing language and layout settings select the keys. This avoids a second, conflicting language selector. Android's [InputMethodManager documentation](https://developer.android.com/reference/android/view/inputmethod/InputMethodManager) recommends a unified subtype with internal language switching for keyboards with downloadable languages, rather than the deprecated additional-subtypes API.

## Regression check

With the debug app and its Android test APK installed on an owned emulator:

```sh
scripts/check-keyboard-touch.sh "$ANDROID_SERIAL" registration
```

The probe queries Android's parsed `InputMethodInfo` and enabled subtype list. It requires one language-neutral keyboard entry. With the old registration it fails because Android reports two subtypes; with the new registration it passes. It does not inspect source text.

## Observed on Android 15

- Enabled both legacy system subtypes while only Dutch/AZERTY was enabled in Lowkey. The old system picker showed English and Dutch for the same app.
- Installed the new preview over that app without clearing app data or the system's saved subtype choices. The picker showed one preview entry, without a fixed English or Dutch label.
- Selected that entry in the actual system picker. The keyboard still used Dutch/AZERTY.
- Compared every saved setting before and after the update; none changed.
- The registration probe passed, as did the 390 JVM tests, debug build and lint.

Screenshots and device logs stay in gitignored artifacts. This is an emulator result, not a claim of a direct Samsung-device test. Gboard and other keyboard apps retain their own independently managed entries.
