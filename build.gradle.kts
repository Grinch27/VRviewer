// Open decisions/research: see docs/DESIGN.md; avoid introducing native SDKs before device validation.
// Risk/verification: pinned dependencies form the initial reproducible, non-container baseline.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
