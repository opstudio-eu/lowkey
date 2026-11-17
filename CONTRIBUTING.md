# Build and contribute

Lowkey is licensed under Apache-2.0. Contributions use the same license.

## Build and test

Install JDK 21 and the Android SDK with API 37, and put their tools on `PATH`. The Gradle wrapper downloads Gradle and verifies its checksum. Build configuration files specify dependency and tool versions.

Run the checks and build a debug APK:

```bash
	./gradlew --no-daemon testDebugUnitTest assembleDebug lintDebug
```

Install it on an emulator or test device, then enable Lowkey in Android's keyboard settings:

```bash
	adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug builds use your local debug key and cannot update a release installation. To update a release install, use a signed APK from the [latest release](https://github.com/opstudio-eu/lowkey/releases/latest).

Run `./gradlew testDebugUnitTest` for every change. For UI changes, verify the actual interaction on an emulator. Debug builds include audio clips under **Settings → Diagnostics → Dictation audio** for testing without a microphone. Existing feature checks are recorded in [docs/demos/](docs/demos/).

When changing the leak check, also run `python3 -I scripts/test_check_leaks.py`.

When changing suggestions or autocorrect, compare `LOWKEY_BENCH=1 ./gradlew testDebugUnitTest --tests '*SuggestionLatencyBenchmark' --rerun` before and after. It reports the time per keystroke and a checksum of all results, which stays the same when behavior doesn't change.

For changes to suggestions or autocorrect, run the quality benchmark (about three minutes): `LOWKEY_BENCH=1 ./gradlew testDebugUnitTest --tests '*QualityBenchmark' --rerun`. It types 1,000 words with one slip each per language and language mix, plus correct words, words of a second language and words without their accents, through the full English, Dutch and Polish word lists. It writes `app/build/reports/quality-benchmark.md` and fails when a result falls past its threshold in `QualityBenchmark`. The sample is fixed, so the same code gives the same numbers and checksum; compare the report before and after a change, and when a change improves a result, tighten its threshold. A 200-word part runs with every `testDebugUnitTest`. For changes to how word lists are stored, `LOWKEY_BENCH=1 ./gradlew testDebugUnitTest --tests '*LexiconFootprintBenchmark' --rerun` reports the heap each list keeps and its build time in `app/build/reports/lexicon-footprint.md`.


For changes to predictions or learning, `LOWKEY_BENCH=1 ./gradlew testDebugUnitTest --tests '*PredictionBenchmark' --rerun` types a fixed set of short messages (`app/src/test/resources/prediction/`) with learning off and on, and reports how often the next word was predicted and how many keys the strip saved, in `app/build/reports/prediction-benchmark.md`.

## Prepare a contribution

Keep private hostnames, network addresses, home-directory paths, personal details, and secrets out of files, commits, pull requests, logs, and release notes. Do not commit `local.properties`, model weights, APKs, build outputs, signing keys, or credentials. Check screenshots and videos before attaching them to a pull request; keep demo media out of git.

Enable the pre-push leak check once per clone:

```bash
	git config core.hooksPath .githooks
```

Set your Git author email to your GitHub no-reply address. The hook requires a private list of strings to exclude in `~/.config/lowkey/leak-denylist`, one per line. Keep that file outside the repository. Never bypass the hook with `--no-verify`.

The check covers pushed commits, author and tag metadata, and ref names. CI also checks pull request text, APK contents, and release notes. Its private list comes from the `LEAK_DENYLIST` Actions secret; fork pull requests use the general patterns only.

Use `.leak-allowlist` only for manually reviewed third-party texts and binary assets that must ship. `scripts/check-leaks.py allow <path>` records a file's checksum; changing that file requires another review. Do not use the allowlist to exempt your own content.

## Add a keyboard language

Keyboard languages are data: add a section to `app/src/main/assets/dict/languages.txt` with the language's name, key rows, layout name, accents, Whisper code, and the size and SHA-256 of its AOSP LatinIME word list (see the file's header). Then run `python3 -I scripts/build-dictionaries.py --check`, which verifies every pinned list and updates `app/src/main/assets/dict/NOTICE`. Only English (US) is bundled; other lists download on the phone. A bundled list is written to the app's assets and must be added to `.leak-allowlist` after review. `python3 -I scripts/test_build_dictionaries.py` and the unit tests check the catalog.

## Update dependencies

Add only dependencies with licenses compatible with the project's policy, such as Apache-2.0, MIT, BSD, or MPL-2.0. Do not add GPL or LGPL code.

For a new or upgraded bundled component, update [the notices](app/src/main/assets/licenses/README.txt). Regenerate Java and Kotlin notices with:

```bash
	./gradlew generateDependencyNotices
```

CI checks that these generated notices are current. Speech models are downloaded separately and retain their publishers' licenses.

Build the native speech libraries with `scripts/build-sherpa-onnx.sh`. It excludes text-to-speech and uses ONNX Runtime without its Java telemetry transport. Keep the vendored Kotlin API at the same version as the native library. The build downloads the native archive from the `native-libs` release and verifies its checksum.

## Publish a release

CI runs the leak check, unit tests, and release build on pull requests and pushes to `main`. With signing secrets available, it signs the APK and attaches it to the run. Without them, it uploads an unsigned APK that cannot be installed as-is.

Release builds are shrunk with R8 (rules in `app/proguard-rules.pro`) and contain native libraries for arm64 phones only. `app/src/main/baseline-prof.txt` lists the keyboard's own packages for ahead-of-time compilation. Add `-PreleaseAbis=arm64-v8a,x86_64` to `./gradlew assembleRelease` to test a release build natively on an x86_64 emulator. `python3 -I scripts/apk-size.py <apk> [<other.apk>]` shows what takes space in an APK, or compares two. `scripts/startup-bench.sh <serial> <apk> <label>` installs an APK over the app and measures cold start, word list loading and memory.

Temporary APK artifacts are retained for one day, and failure test reports for three days. After a release is published or an existing release is verified to contain all downloads, CI removes that run's temporary APK. Published release downloads remain available. A failed publication keeps its temporary APK until it expires.

To publish a new version, bump `versionName` and `versionCode` in `app/build.gradle.kts` in the pull request. After merge, a push to `main` publishes the release if its `v<versionName>` release does not exist. It includes `lowkey.apk`, its checksum, and `update.properties` for the in-app updater. Documentation-only changes do not need a version bump.

Keep the application ID and keyboard service name unchanged so updates continue to work.

### Signing

Sign and verify a release APK with:

```bash
	scripts/sign-release-apk.sh <unsigned.apk> <signed.apk>
```

Set `RELEASE_KEYSTORE` to the keystore path and `RELEASE_KEYSTORE_PASSWORD` to its password. CI restores the keystore from the `RELEASE_KEYSTORE_BASE64` Actions secret and uses the `RELEASE_KEYSTORE_PASSWORD` secret. Never commit the keystore or passwords.

Releases use APK Signature Scheme v3. Keep `signing/rotation-lineage.bin`: it allows the release key to update the earliest development-key installations without losing data. Other debug keys cannot update release installations.
