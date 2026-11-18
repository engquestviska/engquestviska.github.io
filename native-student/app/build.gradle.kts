import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Reads app/google-services.json (Firebase project settings; git-ignored, see FIREBASE-SETUP.md).
// Until that file exists the app builds without notifications.
if (file("google-services.json").exists()) apply(plugin = libs.plugins.google.services.get().pluginId)

// Signing passwords live outside the repo. Without this file (CI, other machines)
// the release build still configures; it is just left unsigned.
val keystorePropsFile = file(
    System.getenv("ENGQUEST_KEYSTORE_PROPERTIES") ?: "D:/android-dev/keystore/keystore.properties"
)
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
// Meta (Facebook) App ID for "Share to Instagram Story": native-student/local.properties
// `meta.appId=...` (git-ignored). Without it the Instagram button is hidden.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val metaAppId = localProps.getProperty("meta.appId").orEmpty().trim()

// Test-only: `-PchronicleDemo=true` builds an app that opens EngQuest Chronicle at once with sample
// numbers (version "2.1-test"), so the story and sharing can be tried before the real date.
val chronicleDemo = (project.findProperty("chronicleDemo") as String?) == "true"

val hasSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !keystoreProps.getProperty(it).isNullOrBlank() }

android {
    namespace = "com.engquest.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.engquest.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 14
        versionName = if (chronicleDemo) "2.1-test" else "2.1"
        buildConfigField("String", "META_APP_ID", "\"$metaAppId\"")
        buildConfigField("boolean", "CHRONICLE_DEMO", chronicleDemo.toString())
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.work.testing)
    testImplementation(libs.room.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
