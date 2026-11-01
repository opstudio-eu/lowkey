# sherpa-onnx Kotlin API (vendored)

Unmodified copies of `OfflineRecognizer.kt`, `OfflineStream.kt`, `FeatureConfig.kt`,
`HomophoneReplacerConfig.kt` and `QnnConfig.kt` from
[sherpa-onnx v1.13.8](https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8/sherpa-onnx/kotlin-api),
licensed under the Apache License 2.0 (`app/src/main/assets/licenses/Apache-2.0.txt`).

They call the native library that `scripts/build-sherpa-onnx.sh` builds. The native code reads these
classes' fields by name, so keep them identical to the sherpa-onnx version the library is built from.
