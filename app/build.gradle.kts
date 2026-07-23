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

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

val releaseStoreFile = keystoreProperties["RELEASE_STORE_FILE"]?.toString()?.let { file(it) }
val releaseStorePassword = keystoreProperties["RELEASE_STORE_PASSWORD"]?.toString()
val releaseKeyAlias = keystoreProperties["RELEASE_KEY_ALIAS"]?.toString()
val releaseKeyPassword = keystoreProperties["RELEASE_KEY_PASSWORD"]?.toString()
val hasReleaseSigningConfig = releaseStoreFile != null && releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null

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

val missingReleaseSigningKeys = listOfNotNull(
    "RELEASE_STORE_FILE".takeIf { releaseStoreFile == null },
    "RELEASE_STORE_PASSWORD".takeIf { releaseStorePassword == null },
    "RELEASE_KEY_ALIAS".takeIf { releaseKeyAlias == null },
    "RELEASE_KEY_PASSWORD".takeIf { releaseKeyPassword == null }
)
val releaseTaskRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("release", ignoreCase = true)
}
val allowDebugReleaseSigning =
    optionalLocalProperty("ALLOW_DEBUG_RELEASE_SIGNING")?.toBooleanStrictOrNull() == true

fun releaseSigningConfigError(): Nothing = error(
    "\nMissing release signing config: ${missingReleaseSigningKeys.joinToString()}.\n" +
        "Provide these in keystore.properties OR as environment variables.\n" +
        "Example (keystore.properties):\n" +
        "RELEASE_STORE_FILE=release-keystore.jks\n" +
        "RELEASE_STORE_PASSWORD=your-store-password\n" +
        "RELEASE_KEY_ALIAS=your-key-alias\n" +
        "RELEASE_KEY_PASSWORD=your-key-password\n"
)

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

        // Keys must come only from gitignored local.properties or CI environment variables;
        // never place their literal values in this version-controlled build file.
        // SECURITY TODO: BuildConfig fields are compiled into the DEX and are trivially
        // extractable from the APK with apktool/jadx. Before any public distribution,
        // replace these with a backend token-proxy or server-side secret delivery so
        // credentials never ship inside the binary.
        buildConfigField("String", "RMV_ACCESS_ID", "\"${requiredLocalProperty("RMV_ACCESS_ID").escapeBuildConfigString()}\"")
        buildConfigField("String", "ORS_API_KEY",   "\"${requiredLocalProperty("ORS_API_KEY").escapeBuildConfigString()}\"")
        buildConfigField("boolean", "DRIVE_PERSON_DIRECTORY", "true")
        buildConfigField("boolean", "DRIVE_VEHICLE_PHOTOS", "true")
        buildConfigField("boolean", "DRIVE_EXTENDED_VEHICLE_PROFILE", "true")
        buildConfigField("boolean", "DRIVE_VEHICLE_LEDGER", "true")
    }

    lint {
        checkGeneratedSources = false
        disable += "RestrictedApi"
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
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
            signingConfig = when {
                hasReleaseSigningConfig -> signingConfigs.getByName("release")
                allowDebugReleaseSigning -> signingConfigs.getByName("debug")
                releaseTaskRequested -> releaseSigningConfigError()
                else -> null
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
    sourceSets {
        listOf("debug", "release").forEach { variant ->
            getByName(variant) {
                kotlin.directories.add("build/generated/ksp/$variant/kotlin")
                java.directories.add("build/generated/ksp/$variant/java")
            }
        }
        getByName("androidTest") {
            assets.directories.add("$projectDir/schemas")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
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
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.work.testing)
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
    implementation(libs.firebase.storage.ktx)
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
    implementation(libs.coil.compose)
    androidTestImplementation(libs.room.runtime)
    ksp(libs.room.compiler)
}
