buildscript {
    dependencies {
        // AGP 9 compiles Kotlin itself (built-in Kotlin) and defaults to KGP 2.2.10; pin a newer KGP.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
    // Must match the Kotlin (KGP) version above.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
