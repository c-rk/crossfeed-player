plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun releaseProperty(name: String): String =
    project.findProperty(name)?.toString() ?: error("$name is missing from ~/.gradle/gradle.properties")

fun buildNumber(): Int = runCatching {
    val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    process.inputStream.bufferedReader().use { it.readText() }.trim().toInt()
}.getOrDefault(0)

android {
    namespace = "dev.crossfeed"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.crossfeed.player"
        minSdk = 26
        targetSdk = 36
        versionCode = 1000 + buildNumber()

        // the translation runtime is a 17 mb native library per architecture, and two of the four
        // only exist for emulators. phones are arm, so only arm is shipped
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        versionName = "0.4.0"
    }

    signingConfigs {
        create("local") {
            val store = File(System.getProperty("user.home"), ".android/crossfeed-release.jks")
            if (store.exists()) {
                storeFile = store
                storePassword = releaseProperty("crossfeedStorePassword")
                keyAlias = releaseProperty("crossfeedKeyAlias")
                keyPassword = releaseProperty("crossfeedKeyPassword")
            } else {
                storeFile = File(System.getProperty("user.home"), ".android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("local")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // on device translation: the models are fetched once, the text never leaves
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
