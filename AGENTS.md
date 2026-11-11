# Project instructions

## Scope

Lowkey is a slim Android keyboard with on-device voice input. Read README.md and REFERENCES.md first. Implement features only when requested.

Host-specific instructions (build environment, emulators, memory budget, delivery) live outside the repository in `~/.config/lowkey/AGENTS.local.md`. If that file exists, read it and follow it; it takes precedence over the general notes below.

## This repository is public

Everything pushed is public: files, commit messages, author emails, branch names, pull request titles, bodies and comments, release notes, CI logs and uploaded artifacts. Never put any of these in them:

- Hostnames, Tailscale names or addresses (`*.ts.net`, the 100.64–100.127 range), private IP addresses, or links to development servers.
- Home-directory paths, user names of the development machine, or local tool paths. Use environment variables or `~` in instructions.
- Personal email addresses, names of real people, phone numbers, or account identifiers. Use invented names in tests and examples.
- Secrets of any kind: keystores, passwords, tokens, API keys.
- Screenshots, videos or logs that show any of the above. Keep demo media out of git; attach checked media to pull requests instead.

Commit with the GitHub no-reply address. Enable the leak check once per clone with `git config core.hooksPath .githooks`; it runs `scripts/check-leaks.py` before every push and needs the private list in `~/.config/lowkey/leak-denylist`. Never bypass it with `--no-verify`. If it flags something, remove the content (rewrite the unpushed commits if needed). Add a file to `.leak-allowlist` (`scripts/check-leaks.py allow <path>`) only after reviewing it by hand, and only for third-party texts and binary assets that must ship, never to silence your own content.

## Build

Use the Gradle wrapper with JDK 21 and the Android SDK on `PATH`. Do not commit `local.properties`, model weights, APKs, build outputs, signing keys or credentials.

Releases are signed with `scripts/sign-release-apk.sh` (see [Signing](CONTRIBUTING.md#signing)). To update an installed app, use the signed APK from the latest GitHub release: Android only installs an update signed with the installed app's key or its rotation lineage. Debug builds and the unsigned APKs from fork pull requests can't update a release install.

## Licenses

Lowkey is Apache-2.0. Add only dependencies under permissive licenses (Apache-2.0, MIT, BSD, MPL-2.0 and similar); never add GPL or LGPL code, which would bring source-distribution duties. For every new or upgraded bundled component, update the notices in `app/src/main/assets/licenses/` and its `README.txt`; `./gradlew generateDependencyNotices` updates the Java/Kotlin list. sherpa-onnx is built with `scripts/build-sherpa-onnx.sh` (speech recognition only, so no eSpeak NG) and attached to the `native-libs` release; keep the vendored Kotlin API in `com/k2fsa/sherpa/onnx/` at the same version. Never copy code or assets from FUTO Keyboard or other projects under FUTO Source First licenses; they are design references only.

## Checks

Run `./gradlew testDebugUnitTest` for every change, and `python3 -I scripts/test_check_leaks.py` when changing the leak check. Verify UI changes on an emulator with an actual UI result, not just an APK build or screenshot.
