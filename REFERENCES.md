# Code references

## FUTO Keyboard

Repository: https://github.com/futo-org/android-keyboard

Canonical source: https://gitlab.futo.org/keyboard/latinime

This is the keyboard the user currently uses. Refer to its Android input-method lifecycle, key layout, resizing settings, and external voice-input integration.

The GitHub repository is the official source mirror. Its README identifies the FUTO Source First License 1.1. Preserve attribution and verify the license before copying code into this project.

## Moonshine voice input

Repository: https://github.com/Today20092/voice-input

This is the community fork of FUTO Voice Input discussed and tested during project setup. The user downloaded its v1.4.6 release.

Refer to its voice-input service, recording lifecycle, compact recording UI, and selectable local speech backends. The fork supports Moonshine, Parakeet, Nemotron, and Whisper.

Use these projects as architecture references for later keyboard and speech milestones. The initial app needs no upstream source code or model downloads.

## Reuse assessment for concept 4

Source reviewed on 2026-10-10: FUTO Keyboard commit `70a5d390c505a6bbcc4e14966e5628e43ca3f1fc` and the voice-input fork commit `ad3b01debf2d08154fe952321b98fafeeb965568`. These are inspection revisions, not imported dependencies.

- FUTO's LatinIME-derived typing engine is the candidate for mature key handling and suggestions. Its `UserDictionaryIO` uses Android's user dictionary, and its personal-dictionary settings support words, shortcuts, and locales. Exact multiword terms and protected casing still need a policy in our app.
- The voice fork's `StreamingSpeechBackend` and `MoonshineBackend` provide model loading, audio chunks, partial callbacks, finalization, and cleanup. Moonshine Small is the proposed first English streaming model; compare Medium on the actual Samsung phone before making it the default.
- The fork's personal dictionary applies explicit text corrections after recognition. It does not train the speech model. Keyboard term protection and optional speech phrase corrections should remain separate.
- The fork supports its own voice IME and a one-shot recognition activity. Its current documentation says the dedicated FUTO provider setup is hidden pending upstream support. A separately installed APK is not a verified integration for our compact panel.
- Adapt insertion and cancellation deliberately. The fork's insertion-safety documentation retains accepted partial text on cancellation; our mode-switch requirement discards pending provisional text. Remove only text that the current dictation still owns, and reject late callbacks after editor, cursor, or session changes.
- The inspected keyboard license is FUTO Source First 1.1-kb; the voice fork license is FUTO Source First 1.0. Both restrict modification to non-commercial purposes and require preserved notices. Model weights and runtimes have separate terms. These licenses apply to those projects only: Lowkey takes no source code or assets from them, which keeps it compatible with its own Apache-2.0 license.

Relevant upstream files and docs:

- [FUTO personal dictionary IO](https://github.com/futo-org/android-keyboard/blob/70a5d390c505a6bbcc4e14966e5628e43ca3f1fc/java/src/org/futo/inputmethod/latin/uix/UserDictionaryIO.kt)
- [Voice backend interface](https://github.com/Today20092/voice-input/blob/ad3b01debf2d08154fe952321b98fafeeb965568/app/src/main/java/org/futo/voiceinput/backend/SpeechBackend.kt)
- [Moonshine backend](https://github.com/Today20092/voice-input/blob/ad3b01debf2d08154fe952321b98fafeeb965568/app/src/main/java/org/futo/voiceinput/moonshine/MoonshineBackend.kt)
- [Model guide](https://github.com/Today20092/voice-input/blob/ad3b01debf2d08154fe952321b98fafeeb965568/docs/model-guide.md)
- [Dictionary and keyboard setup](https://github.com/Today20092/voice-input/blob/ad3b01debf2d08154fe952321b98fafeeb965568/docs/user-guide.md)
- [Insertion safety](https://github.com/Today20092/voice-input/blob/ad3b01debf2d08154fe952321b98fafeeb965568/docs/specs/ime-insertion-safety.md)
