import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// CR-006 (Phase 20B): the weather Edge Functions are called with the project's PUBLIC anon key
// only. It comes from a Gradle property, the environment (CI secret) or local.properties, and is
// never committed. A secret/service_role key stops the build: it must never ship in an APK.
val weatherFunctionsUrl = "https://zzelvbcuxsboafibfxch.supabase.co/functions/v1"
val weatherAnonKey: String = run {
    val local = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { stream -> load(stream) }
    }
    (providers.gradleProperty("SUPABASE_ANON_KEY").orNull
        ?: providers.environmentVariable("SUPABASE_ANON_KEY").orNull
        ?: local.getProperty("SUPABASE_ANON_KEY")).orEmpty().trim()
}
run {
    check(!weatherAnonKey.startsWith("sb_secret_")) { "SUPABASE_ANON_KEY is a secret key; use the public anon key" }
    val payload = weatherAnonKey.split('.').getOrNull(1)
    if (payload != null) {
        val claims: String = runCatching {
            String(Base64.getUrlDecoder().decode(payload.padEnd((payload.length + 3) / 4 * 4, '=')))
        }.getOrDefault("")
        check(!claims.replace(" ", "").contains("\"role\":\"service_role\"")) {
            "SUPABASE_ANON_KEY is a service_role key; use the public anon key"
        }
    }
}

android {
    namespace = "com.isivoltpro.maginaolivo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.isivoltpro.maginaolivo"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "WEATHER_FUNCTIONS_URL", "\"$weatherFunctionsUrl\"")
        buildConfigField("String", "WEATHER_ANON_KEY", "\"$weatherAnonKey\"")
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "Mágina Olivo Dev")
            buildConfigField("String", "ENVIRONMENT", "\"DEV\"")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            resValue("string", "app_name", "Mágina Olivo Staging")
            buildConfigField("String", "ENVIRONMENT", "\"STAGING\"")
        }
        create("production") {
            dimension = "environment"
            resValue("string", "app_name", "Mágina Olivo")
            buildConfigField("String", "ENVIRONMENT", "\"PRODUCTION\"")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
        resValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation("org.maplibre.gl:android-sdk-opengl:13.6.1")
    implementation("org.locationtech.jts:jts-core:1.20.0")
    implementation("org.locationtech.proj4j:proj4j:1.4.0")
    implementation("org.locationtech.proj4j:proj4j-epsg:1.4.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    // Phase 12: on-device OCR with the bundled Latin model — no network, no upload.
    implementation(libs.mlkit.text.recognition)
    // Navigation requests serialization 1.7.3 while Room's schema reader requires 1.8.1.
    // One BOM keeps the production and instrumented runtime classpaths binary-aligned.
    implementation(platform(libs.kotlinx.serialization.bom))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(libs.androidx.room.testing)
}
