buildscript {
    dependencies {
        // AGP 9 built-in Kotlin remains enabled. Upgrade its KGP runtime so
        // the Kotlin and Compose compiler versions stay paired.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
