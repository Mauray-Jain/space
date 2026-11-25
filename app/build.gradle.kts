import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "space.app"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "space.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Personal sideloading: signed with the debug key until there's a real one.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // One APK per ABI: arm64 for the phone, x86_64 for the CI emulator. Keeps ONNX Runtime's native libs small.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }
}

/**
 * Downloads pinned files at build time, checks their SHA-256, and skips ones already there.
 * Models ship inside the APK, so the app itself never downloads anything (PLAN.md §2).
 */
abstract class DownloadFiles : DefaultTask() {
    @get:Input abstract val files: MapProperty<String, String> // name → "sha256 url"
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun download() {
        val dir = outputDir.get().asFile.apply { mkdirs() }
        files.get().forEach { (name, spec) ->
            val (sha, url) = spec.split(" ", limit = 2)
            val file = File(dir, name)
            fun sha256(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
            if (file.exists() && sha256(file) == sha) return@forEach
            logger.lifecycle("Downloading $name")
            URI(url).toURL().openStream().use { input -> file.outputStream().use { input.copyTo(it) } }
            check(sha256(file) == sha) { "Checksum mismatch for $name" }
        }
    }
}

/** Copies the downloaded models for the APK, turning Moonshine's 3.8 MB tokenizer.json into a plain tokens.txt. */
abstract class PrepareModels : DefaultTask() {
    @get:InputDirectory abstract val downloads: DirectoryProperty
    @get:InputDirectory abstract val trained: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun prepare() {
        val src = downloads.get().asFile
        val out = outputDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        src.listFiles { f -> f.name.endsWith(".onnx") || f.name == "ocr-dict.txt" }!!.forEach { it.copyTo(File(out, it.name)) }
        // The intent model is trained in this repo (training/), so it's committed rather than downloaded.
        trained.get().asFile.listFiles()!!.forEach { it.copyTo(File(out, it.name)) }
        @Suppress("UNCHECKED_CAST")
        val json = groovy.json.JsonSlurper().parse(File(src, "moonshine-tokenizer.json")) as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val vocab = (json["model"] as Map<String, Any>)["vocab"] as Map<String, Number>
        val byId = vocab.entries.sortedBy { it.value.toInt() }.map { it.key }
        // One token per line, line number = id. Backslashes and line breaks are escaped.
        File(out, "moonshine-tokens.txt").writeText(byId.joinToString("\n") { it.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r") })
    }
}

val moonshine = "https://huggingface.co/onnx-community/moonshine-base-ONNX/resolve/b1e9b6aae3c3c7298f10c3798393fdf38e8fbbad"
val paddle = "https://huggingface.co/monkt/paddleocr-onnx/resolve/7b02d0a30a07ba2b92ad1ff5a8941ae2c633de65"
val downloadModels by tasks.registering(DownloadFiles::class) {
    outputDir = layout.buildDirectory.dir("modelDownloads")
    files = mapOf(
        // Moonshine Base, English, int8 (MIT): ~63 MB.
        "moonshine-encoder.onnx" to "4edb38db96b52ddee5a3b25d2211bb6394f9a9a2d95f67bce0e8c861da018a4d $moonshine/onnx/encoder_model_int8.onnx",
        "moonshine-decoder.onnx" to "45febab0347ffbe1326459e092ae20a2cfda10ca33ab018b3c299247284de61b $moonshine/onnx/decoder_model_merged_int8.onnx",
        "moonshine-tokenizer.json" to "7b913404bdd039af4756783218af4440bc07fb7d6d8258d677e34f95b3ec416f $moonshine/tokenizer.json",
        // PaddleOCR (Apache-2.0): PP-OCRv3 text detection 2.4 MB + PP-OCRv5 English recognition 7.8 MB.
        "ocr-det.onnx" to "ee40e80071ba3a320d4efda75f3e22047a7d049e9bf7bcaaf9daea23fc21b935 $paddle/detection/v3/det.onnx",
        "ocr-rec.onnx" to "4e16deb22c4da6468bdca539b2cd3c8687825538b67109177c47d359ab994cd7 $paddle/languages/english/rec.onnx",
        "ocr-dict.txt" to "e025a66d31f327ba0c232e03f407ae8d105e1e709e7ccb3f408aa778c24e70d6 $paddle/languages/english/dict.txt",
        // Silero VAD v5 (MIT): 2 MB.
        "silero-vad.onnx" to "a4a068cd6cf1ea8355b84327595838ca748ec29a25bc91fc82e6c299ccdc5808 https://huggingface.co/onnx-community/silero-vad/resolve/e71cae966052b992a7eca6b17738916ce0eca4ec/onnx/model.onnx",
    )
}
val prepareModels by tasks.registering(PrepareModels::class) {
    downloads = downloadModels.flatMap { it.outputDir }
    trained = rootProject.layout.projectDirectory.dir("models")
    outputDir = layout.buildDirectory.dir("models")
}
// A LibriSpeech clip for the transcription test (not shipped).
val downloadTestAudio by tasks.registering(DownloadFiles::class) {
    outputDir = layout.buildDirectory.dir("testAudio")
    files = mapOf(
        "librispeech.wav" to "6bc58a4efdf20daac252b6b1502632601a71efe0308f6757dc1eda34891a7e4f https://huggingface.co/csukuangfj/sherpa-onnx-moonshine-tiny-en-int8/resolve/bf2b762c076d8ea61e2af0b3851c9564fb77552e/test_wavs/0.wav",
    )
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(prepareModels, PrepareModels::outputDir)
        variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(downloadTestAudio, DownloadFiles::outputDir)
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(prepareModels, downloadTestAudio)
    systemProperty("models.dir", layout.buildDirectory.dir("models").get().asFile.path)
    systemProperty("testAudio.dir", layout.buildDirectory.dir("testAudio").get().asFile.path)
}

// Unit tests run ONNX Runtime's desktop build; the Android one can't load on the JVM.
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    exclude(group = "com.microsoft.onnxruntime", module = "onnxruntime-android")
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.sqlite.bundled)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.libphonenumber)
    implementation(libs.onnxruntime.android)
    implementation(libs.zxing.core)
    implementation(libs.work.runtime)
    implementation(libs.zip4j)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.onnxruntime.jvm)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
