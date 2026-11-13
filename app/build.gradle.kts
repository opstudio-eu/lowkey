import java.net.URI
import java.util.zip.ZipFile
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Downloads [url] to [target] once and verifies the pinned SHA-256 (also when already cached).
fun registerPinnedDownload(name: String, url: String, target: Provider<RegularFile>, sha256: String) =
    tasks.register(name) {
        val offline = gradle.startParameter.isOffline
        inputs.property("url", url)
        inputs.property("sha256", sha256)
        outputs.file(target)
        doLast {
            val file = target.get().asFile
            fun sha256(f: File): String = MessageDigest.getInstance("SHA-256").let { md ->
                f.inputStream().use { input ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        md.update(buffer, 0, n)
                    }
                }
                md.digest().joinToString("") { "%02x".format(it) }
            }
            if (file.isFile && sha256(file) == sha256) return@doLast
            if (offline) {
                throw GradleException("${file.name} is not cached; run the build once without --offline to fetch it.")
            }
            file.parentFile.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            val connection = URI(url).toURL().openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 60_000
            }
            try {
                connection.getInputStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
                val actual = sha256(tmp)
                if (actual != sha256) throw GradleException("${file.name} checksum mismatch: $actual")
                if (!tmp.renameTo(file)) throw GradleException("Can't move ${tmp.name} into place")
            } finally {
                tmp.delete()
            }
        }
    }

// sherpa-onnx built for speech recognition only, with ONNX Runtime 1.31.0 (scripts/build-sherpa-onnx.sh):
// its official Android builds include text-to-speech, which statically links GPL code (eSpeak NG), and
// Microsoft's Maven ONNX Runtime adds a telemetry transport. The zip holds both libraries and their
// notices; it is attached to this repository's "native-libs" release and verified by SHA-256.
val sherpaOnnxVersion = "1.13.8"
val nativeAbis = listOf("arm64-v8a", "x86_64")
val sherpaOnnxZip = layout.buildDirectory.file("downloads/sherpa-onnx-$sherpaOnnxVersion-asr-android.zip")
val downloadSherpaOnnx = registerPinnedDownload(
    "downloadSherpaOnnx",
    // -PsherpaOnnxZipUrl=file:///… builds with a local scripts/build-sherpa-onnx.sh output (same checksum).
    providers.gradleProperty("sherpaOnnxZipUrl").getOrElse(
        "https://github.com/opstudio-eu/lowkey/releases/download/native-libs/sherpa-onnx-$sherpaOnnxVersion-asr-android.zip",
    ),
    sherpaOnnxZip,
    "edf4b067e40229c946f23f7cd31f0614f4bc9162db9f3af3fd84f9c06a6e436d",
)

/** Unpacks the `jni/<abi>/lib*.so` entries of a zip into a jniLibs directory. */
abstract class ExtractJniLibs : DefaultTask() {
    @get:InputFile abstract val zip: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        var count = 0
        ZipFile(zip.get().asFile).use { archive ->
            for (entry in archive.entries()) {
                val match = Regex("^jni/([a-z0-9_-]+)/(lib[A-Za-z0-9_.-]+\\.so)$").find(entry.name) ?: continue
                val target = File(out, "${match.groupValues[1]}/${match.groupValues[2]}")
                target.parentFile.mkdirs()
                archive.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                count++
            }
        }
        if (count == 0) throw GradleException("${zip.get().asFile.name} has no native libraries")
    }
}

val extractSherpaOnnx = tasks.register<ExtractJniLibs>("extractSherpaOnnx") {
    dependsOn(downloadSherpaOnnx)
    zip.set(sherpaOnnxZip)
    outputDir.set(layout.buildDirectory.dir("generated/sherpa-onnx-jni"))
}

android {
    namespace = "com.rheerkens.androidkeyboard"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rheerkens.androidkeyboard"
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "com.rheerkens.androidkeyboard.test.KeyboardTouchProbe"
        versionCode = 62
        versionName = "0.11.1"

        // The release feed the in-app updater reads. -PupdateFeedUrl=https://… points a test build elsewhere.
        val updateFeedUrl = providers.gradleProperty("updateFeedUrl")
            .getOrElse("https://github.com/opstudio-eu/lowkey/releases/latest/download/update.properties")
        require(updateFeedUrl.startsWith("https://") && '"' !in updateFeedUrl) { "updateFeedUrl must be an HTTPS URL" }
        buildConfigField("String", "UPDATE_FEED_URL", "\"$updateFeedUrl\"")
        // -PupdateRequireUserAction=true makes Android ask to confirm every update, as some phones always do.
        val updateRequireUserAction = providers.gradleProperty("updateRequireUserAction").getOrElse("false").toBoolean()
        buildConfigField("boolean", "UPDATE_REQUIRE_USER_ACTION", "$updateRequireUserAction")

        ndk {
            // Phones (arm64) and the x86_64 emulator; drops 32-bit ARM to keep the APK smaller.
            abiFilters += nativeAbis
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    // Opt-in local UI test APK. Release identity and version are unchanged.
    buildTypes.getByName("debug") {
        if (providers.gradleProperty("uiPreview").orNull == "true") {
            applicationIdSuffix = ".uipreview"
            resValue("string", "app_name", "Lowkey UI Preview")
            resValue("string", "ime_name", "Lowkey UI Preview")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(extractSherpaOnnx, ExtractJniLibs::outputDir)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

// Apache-2.0 NOTICE preservation for the Java/Kotlin dependencies: writes every resolved release
// runtime component with its source location, and the NOTICE files their jars carry, into the
// license assets. CI regenerates this and fails when the committed copy is stale.
val dependencyNoticesDir = layout.projectDirectory.dir("src/main/assets/licenses/dependencies")
tasks.register("generateDependencyNotices") {
    group = "verification"
    description = "Writes the release dependency manifest and NOTICE files into the license assets."
    val runtime = configurations.named("releaseRuntimeClasspath")
    val outDir = dependencyNoticesDir.asFile
    doLast {
        val config = runtime.get()
        val modules = config.incoming.resolutionResult.allComponents
            .mapNotNull { it.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier }
            .sortedWith(compareBy({ it.group }, { it.module }))
        val classesJars = config.incoming.artifactView {
            lenient(true)
            attributes { attribute(Attribute.of("artifactType", String::class.java), "android-classes-jar") }
        }.artifacts
        outDir.deleteRecursively()
        outDir.mkdirs()
        val notices = sortedMapOf<String, String>()
        for (artifact in classesJars) {
            val id = artifact.id.componentIdentifier as? org.gradle.api.artifacts.component.ModuleComponentIdentifier
                ?: continue
            ZipFile(artifact.file).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && Regex("(?i)^META-INF/NOTICE(\\.txt|\\.md)?$").matches(it.name) }
                    .forEach { entry ->
                        notices["${id.group}-${id.module}-NOTICE.txt"] =
                            zip.getInputStream(entry).bufferedReader().readText()
                    }
            }
        }
        notices.forEach { (name, text) -> File(outDir, name).writeText(text) }
        // Components without a published sources jar: their source elsewhere, or why none is needed.
        val sourceOverrides = mapOf(
            "androidx.constraintlayout:constraintlayout:2.0.1" to
                "https://github.com/androidx/constraintlayout/archive/refs/tags/2.0.1.tar.gz",
            "androidx.constraintlayout:constraintlayout-solver:2.0.1" to
                "https://github.com/androidx/constraintlayout/archive/refs/tags/2.0.1.tar.gz",
        )
        fun sourcesUrl(id: org.gradle.api.artifacts.component.ModuleComponentIdentifier): String {
            sourceOverrides["${id.group}:${id.module}:${id.version}"]?.let { return it }
            if (id.module.endsWith("-bom")) return "(bill of materials: version constraints only, no code)"
            if (id.group == "org.jetbrains.kotlin" && id.module == "kotlin-stdlib-common" &&
                !id.version.startsWith("1.")
            ) return "(no code: merged into kotlin-stdlib since Kotlin 2.0)"
            val google = id.group.startsWith("androidx.") || id.group.startsWith("com.google.android")
            val repo = if (google) "https://dl.google.com/android/maven2" else "https://repo1.maven.org/maven2"
            return "$repo/${id.group.replace('.', '/')}/${id.module}/${id.version}/${id.module}-${id.version}-sources.jar"
        }
        File(outDir, "manifest.txt").writeText(buildString {
            append("Java and Kotlin libraries in the release APK (resolved releaseRuntimeClasspath).\n")
            append("Each line: group:artifact:version, then its source archive. NOTICE files they carry are in this folder.\n")
            append("\n")
            modules.forEach { append("${it.group}:${it.module}:${it.version}\n    ${sourcesUrl(it)}\n") }
        })
        logger.lifecycle("Wrote ${modules.size} dependencies and ${notices.size} NOTICE files to $outDir")
    }
}
