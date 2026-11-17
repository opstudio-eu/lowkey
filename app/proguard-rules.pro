# R8 rules for the release build, next to Android's proguard-android-optimize.txt.

# sherpa-onnx's native library reads the config classes' fields by name and constructs its result
# classes (see app/src/main/java/com/k2fsa/sherpa/onnx/README.md), so R8 must not rename or drop
# anything there.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Diagnostics → Crashes shows Java stack traces that users copy into bug reports. Keep them readable
# without a mapping file: no renaming, and keep file names and line numbers.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
