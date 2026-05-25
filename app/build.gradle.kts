import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.ksp)
}

// Priority 1: Always close the FileInputStream to avoid "too many open files" in CI.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// Priority 2: Fail-fast helper — throws a Gradle build error if a required key is absent.
// Resolution order: local.properties → environment variable → build error.
// This allows CI pipelines to inject secrets as env vars without a local.properties file,
// while still failing loudly when neither source provides a value.
fun requiredLocalProperty(key: String): String =
    localProperties.getProperty(key)
        ?: System.getenv(key)
        ?: error(
            "\nMissing required key \"$key\".\n" +
            "Provide it in local.properties OR as an environment variable named $key.\n" +
            "Example (local.properties): $key=your-value-here"
        )

fun optionalLocalProperty(key: String): String? =
    localProperties.getProperty(key) ?: System.getenv(key)

// Escapes a raw property value so it is safe to embed inside a double-quoted Kotlin/Java
// string literal in generated BuildConfig code. Without this, a value containing a
// backslash or double-quote would produce a malformed source file.
fun String.escapeBuildConfigString(): String =
    this.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.example.toplutasima"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        // TODO: Replace "com.example.*" with a real reverse-domain ID before Play Store distribution.
        //       e.g. "com.kayiklik.toplutasima". The Play Store rejects com.example.* namespaces.
        applicationId = "com.example.toplutasima"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // SECURITY TODO: BuildConfig fields are compiled into the DEX and are trivially
        // extractable from the APK with apktool/jadx. Before any public distribution,
        // replace these with a backend token-proxy or server-side secret delivery so
        // credentials never ship inside the binary.
        buildConfigField("String", "RMV_ACCESS_ID", "\"${requiredLocalProperty("RMV_ACCESS_ID").escapeBuildConfigString()}\"")
        buildConfigField("String", "ORS_API_KEY",   "\"${requiredLocalProperty("ORS_API_KEY").escapeBuildConfigString()}\"")
    }

    buildTypes {
        // Priority 3: Enable R8 minification and resource shrinking for release.
        // This reduces APK size and obfuscates the binary, limiting reverse-engineering exposure.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Private/local builds can opt into debug signing explicitly:
            // ALLOW_DEBUG_RELEASE_SIGNING=true ./gradlew assembleRelease
            // Public distribution must use a real release signingConfig instead.
            if (optionalLocalProperty("ALLOW_DEBUG_RELEASE_SIGNING")?.toBooleanStrictOrNull() == true) {
                signingConfig = signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            // Note: applicationIdSuffix intentionally omitted — google-services.json only
            // registers "com.example.toplutasima". Add a separate debug entry in Firebase
            // console first if you want to use a .debug suffix in the future.
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // Priority 4: All previously hardcoded strings are now resolved from libs.versions.toml.
    // Versions are unchanged — only the declaration location has moved.
    implementation(libs.okhttp)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore.ktx)
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    // Dependency Injection — Koin
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    // Network — Retrofit + kotlinx.serialization
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization.converter)
    implementation(libs.kotlinx.serialization.json)
    // Location — Google Play Services
    implementation(libs.play.services.location)
    // WorkManager
    implementation(libs.work.runtime.ktx)
    // Security
    implementation(libs.security.crypto)
    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
}
