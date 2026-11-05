import java.util.Base64
import java.util.Properties
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// CR-006 (Phase 20B): the weather Edge Functions are called with the project's PUBLIC anon key
// only. It comes from a Gradle property, the environment (CI secret) or local.properties, and is
// never committed. A secret/service_role key stops the build: it must never ship in an APK.
val localSettings = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun setting(name: String): String = (providers.gradleProperty(name).orNull
    ?: providers.environmentVariable(name).orNull ?: localSettings.getProperty(name)).orEmpty().trim()
fun publicKey(key: String): Boolean {
    if (Regex("^sb_publishable_[A-Za-z0-9_-]+$").matches(key)) return true
    val claims = runCatching { String(Base64.getUrlDecoder().decode(key.split('.')[1])) }.getOrDefault("")
    return key.split('.').size == 3 && Regex("\"role\"\\s*:\\s*\"anon\"").containsMatchIn(claims) &&
        !claims.contains("service_role")
}
fun publicUrl(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.port == -1 && uri.rawFragment == null
}.getOrDefault(false)
val devWeatherKey = setting("SUPABASE_ANON_KEY")
run {
    check(devWeatherKey.isEmpty() || publicKey(devWeatherKey)) { "SUPABASE_ANON_KEY must be public" }
}
val devWeatherUrl = "https://zzelvbcuxsboafibfxch.supabase.co/functions/v1"
fun weatherSettings(environment: String): Pair<String, String> {
    val url = setting("MAGINA_WEATHER_${environment}_URL")
    val key = setting("MAGINA_WEATHER_${environment}_PUBLIC_KEY")
    check((url.isEmpty() && key.isEmpty()) ||
        (publicUrl(url) && Regex("^[a-z]{20}\\.supabase\\.co$").matches(URI(url).host) &&
            URI(url).path == "/functions/v1" && URI(url).rawQuery == null && publicKey(key))) {
        "Weather $environment requires an HTTPS functions URL and public key together"
    }
    return url to key
}
val stagingWeather = weatherSettings("STAGING")
val productionWeather = weatherSettings("PRODUCTION")
check(productionWeather.first.isEmpty() || URI(productionWeather.first).host != URI(devWeatherUrl).host) {
    "Production weather must not use the DEV project"
}

// Publication uses an explicit, monotonically increasing Play versionCode. CI run numbers remain
// diagnostic build numbers and are not safe as Play version codes across independent workflows.
val appVersionName = "1.0.0"
val appBuildNumber: Int = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 0
val publicationVersionCode = setting("MAGINA_VERSION_CODE").toIntOrNull()
val privacyUrl = setting("MAGINA_PRIVACY_URL")
val accountDeletionUrl = setting("MAGINA_ACCOUNT_DELETION_URL")
val uploadStore = setting("MAGINA_UPLOAD_STORE_FILE")
val uploadStorePassword = setting("MAGINA_UPLOAD_STORE_PASSWORD")
val uploadKeyAlias = setting("MAGINA_UPLOAD_KEY_ALIAS")
val uploadKeyPassword = setting("MAGINA_UPLOAD_KEY_PASSWORD")

// Account Auth is independent of weather and each environment uses its own project.
fun authSetting(name: String): String = setting(name)
fun authSettings(environment: String): Pair<String,String> {
    val url=authSetting("MAGINA_AUTH_${environment}_URL")
    val key=authSetting("MAGINA_AUTH_${environment}_PUBLIC_KEY")
    if(url.isEmpty() && key.isEmpty()) return "" to ""
    check(Regex("^https://[a-z]{20}\\.supabase\\.co$").matches(url)) { "Account Auth requires an explicit HTTPS Supabase project URL" }
    check(url !in setOf("https://zzelvbcuxsboafibfxch.supabase.co","https://klchkjsdybbgritxkswp.supabase.co")) { "Existing weather projects are forbidden for Account Auth" }
    val claims=runCatching { String(Base64.getUrlDecoder().decode(key.split('.')[1])) }.getOrDefault("")
    val public=Regex("^sb_publishable_[A-Za-z0-9_-]+$").matches(key) ||
        (key.split('.').size==3 && Regex("\"role\"\\s*:\\s*\"anon\"").containsMatchIn(claims) && !claims.contains("service_role"))
    check(public) { "Account Auth only accepts a publishable/anon key; secret/service_role keys are forbidden" }
    return url to key
}
val devAuth=authSettings("DEV")
val stagingAuth=authSettings("STAGING")
val productionAuth=authSettings("PRODUCTION")
check(productionAuth.first.isEmpty() || productionAuth.first !in setOf(devAuth.first, stagingAuth.first,
    "https://zubpicdibqbmewxeurhm.supabase.co")) { "Production Auth must use a separate project" }

android {
    namespace = "com.isivoltpro.maginaolivo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.isivoltpro.maginaolivo"
        minSdk = 26
        targetSdk = 37
        versionCode = maxOf(appBuildNumber, 1)
        versionName = appVersionName

        buildConfigField("int", "BUILD_NUMBER", "$appBuildNumber")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            // Integrated 1.0 review APKs must install over earlier DEV builds whose CI
            // versionCode already exceeds the local default (1).
            versionCode = 100_000 + appBuildNumber
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "Mágina Olivo Dev")
            buildConfigField("String", "ENVIRONMENT", "\"DEV\"")
            buildConfigField("String", "AUTH_PROJECT_URL", "\"${devAuth.first}\"")
            buildConfigField("String", "AUTH_PUBLIC_KEY", "\"${devAuth.second}\"")
            buildConfigField("String", "WEATHER_FUNCTIONS_URL", "\"$devWeatherUrl\"")
            buildConfigField("String", "WEATHER_ANON_KEY", "\"$devWeatherKey\"")
            buildConfigField("String", "PRIVACY_URL", "\"\"")
            buildConfigField("String", "ACCOUNT_DELETION_URL", "\"\"")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            resValue("string", "app_name", "Mágina Olivo Staging")
            buildConfigField("String", "ENVIRONMENT", "\"STAGING\"")
            buildConfigField("String", "AUTH_PROJECT_URL", "\"${stagingAuth.first}\"")
            buildConfigField("String", "AUTH_PUBLIC_KEY", "\"${stagingAuth.second}\"")
            buildConfigField("String", "WEATHER_FUNCTIONS_URL", "\"${stagingWeather.first}\"")
            buildConfigField("String", "WEATHER_ANON_KEY", "\"${stagingWeather.second}\"")
            buildConfigField("String", "PRIVACY_URL", "\"\"")
            buildConfigField("String", "ACCOUNT_DELETION_URL", "\"\"")
        }
        create("production") {
            dimension = "environment"
            resValue("string", "app_name", "Mágina Olivo")
            buildConfigField("String", "ENVIRONMENT", "\"PRODUCTION\"")
            buildConfigField("String", "AUTH_PROJECT_URL", "\"${productionAuth.first}\"")
            buildConfigField("String", "AUTH_PUBLIC_KEY", "\"${productionAuth.second}\"")
            buildConfigField("String", "WEATHER_FUNCTIONS_URL", "\"${productionWeather.first}\"")
            buildConfigField("String", "WEATHER_ANON_KEY", "\"${productionWeather.second}\"")
            buildConfigField("String", "PRIVACY_URL", "\"$privacyUrl\"")
            buildConfigField("String", "ACCOUNT_DELETION_URL", "\"$accountDeletionUrl\"")
            versionCode = publicationVersionCode ?: 1
        }
    }

    signingConfigs {
        if (listOf(uploadStore, uploadStorePassword, uploadKeyAlias, uploadKeyPassword).all { it.isNotBlank() }) {
            create("upload") {
                storeFile = file(uploadStore)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
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
            signingConfigs.findByName("upload")?.let { signingConfig = it }
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

val verifyProductionRelease = tasks.register("verifyProductionRelease") {
    doLast {
        check(productionAuth.first.isNotBlank() && productionAuth.second.isNotBlank()) { "Production Auth URL and public key are required" }
        check(privacyUrl.isNotBlank() && publicUrl(privacyUrl)) { "MAGINA_PRIVACY_URL must be a public HTTPS page" }
        check(accountDeletionUrl.isNotBlank() && publicUrl(accountDeletionUrl)) { "MAGINA_ACCOUNT_DELETION_URL must be a public HTTPS page" }
        check(publicationVersionCode != null && publicationVersionCode > 0) { "MAGINA_VERSION_CODE must be a positive Play versionCode" }
        check(uploadStore.isNotBlank() && file(uploadStore).isFile && uploadStorePassword.isNotBlank() &&
            uploadKeyAlias.isNotBlank() && uploadKeyPassword.isNotBlank()) { "Upload signing configuration is incomplete" }
    }
}
tasks.matching { it.name == "preProductionReleaseBuild" }.configureEach { dependsOn(verifyProductionRelease) }

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
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")

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

// Phase 21C: «Qué hay de nuevo» reads docs/CHANGELOG-APP.md, bundled as an asset at build time so
// the notes on the phone are always the ones of the APK installed.
abstract class CopyChangelogAsset : DefaultTask() {
    @get:InputFile
    abstract val changelog: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.mkdirs()
        changelog.get().asFile.copyTo(File(out, "CHANGELOG-APP.md"), overwrite = true)
    }
}

// One task per variant, each with its own output directory, so variants never share a location.
androidComponents {
    onVariants { variant ->
        val copyChangelog = tasks.register<CopyChangelogAsset>("copy${variant.name.replaceFirstChar(Char::uppercase)}ChangelogAsset") {
            changelog.set(rootProject.layout.projectDirectory.file("docs/CHANGELOG-APP.md"))
            outputDir.set(layout.buildDirectory.dir("generated/changelog-assets/${variant.name}"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(copyChangelog, CopyChangelogAsset::outputDir)
    }
}
