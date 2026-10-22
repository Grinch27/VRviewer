// Open decisions: headset calibration and target device matrix are tracked in docs/DESIGN.md.
// Risk/verification: keep repositories centralized and verify upgrades with APK, lint and tests.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "VRviewer"
include(":app", ":core")
