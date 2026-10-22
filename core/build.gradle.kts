// Open decisions: keep projection/profile policy independent of Android and UI frameworks.
// Risk/verification: math contracts are tested on the JVM; GPU/device checks remain separate.
plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies { testImplementation(libs.junit) }
