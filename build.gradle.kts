// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application)  apply false
    alias(libs.plugins.kotlin.compose)       apply false
    // Priority 5: Declare kotlin-serialization here so any future module can apply it
    // without re-specifying the version (follows the "declare once, apply per module" pattern).
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.services)      apply false
    alias(libs.plugins.ksp)                  apply false
}
