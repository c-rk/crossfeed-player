plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun releaseProperty(name: String): String =
    project.findProperty(name)?.toString() ?: error("$name is missing from ~/.gradle/gradle.properties")

val releaseStore = File(System.getProperty("user.home"), ".android/crossfeed-release.jks")

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
        versionName = "0.5.9"
    }

    signingConfigs {
        create("local") {
            if (releaseStore.exists()) {
                storeFile = releaseStore
                storePassword = releaseProperty("crossfeedStorePassword")
                keyAlias = releaseProperty("crossfeedKeyAlias")
                keyPassword = releaseProperty("crossfeedKeyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("local")
        }
        // the redesign, built so it installs beside the app you already use rather than over
        // it. same key, own package, own diary, so the new one can be lived with for a while
        // before it replaces anything
        create("glass") {
            initWith(getByName("release"))
            applicationIdSuffix = ".glass"
            versionNameSuffix = "-glass"
            signingConfig = signingConfigs.getByName("local")
            matchingFallbacks += listOf("release")
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

// an install can only be updated in place by a build signed with the same key, and that signature is
// the only thing standing between a phone and somebody else's apk. the old fallback to the public
// debug key gave away both without saying so, so packaging a release now stops instead. debug builds
// are untouched, which is why this is a check on the two packaging tasks rather than on the config
tasks.matching { it.name == "packageRelease" || it.name == "packageGlass" }.configureEach {
    doFirst {
        if (!releaseStore.exists()) {
            error(
                "the release signing key is missing from ${releaseStore.path}. restore it from the " +
                    "backup in Documents/Personal/keys before cutting a release or glass build",
            )
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
