plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
}

// Build output stays at the default build/ folders. build.ps1 turns those folders into
// junctions to D:\android-dev\build\native-teacher, so the bytes land on D: (owner keeps
// C: free). Setting layout.buildDirectory to D: directly breaks AGP's unit-test resource
// config ("this and base files have different roots") because the sources are on C:.
