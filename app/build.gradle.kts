// Open decisions: headset optical calibration, HDR and codec matrix need physical devices.
// Optimization: one decoder feeds one viewport; HTTP(S)/HLS/DASH use the same playback pipeline.
// Risks/verification: CI assembles both variants; runtime diagnostics require a separate user request.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "org.grinch27.vrviewer"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "org.grinch27.vrviewer"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.1.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.hls)
    implementation(libs.media3.dash)
    debugImplementation(libs.compose.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.core)
    androidTestImplementation(libs.test.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
}
