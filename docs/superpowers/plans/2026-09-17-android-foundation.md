# Mágina Olivo — Phase 1 Android Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the first reproducible Mágina Olivo Android project and produce a DEV APK that builds, tests, installs and launches on a physical Android device without implementing agricultural product features.

**Architecture:** Phase 1 deliberately creates only one Android application module (`:app`) plus reproducible build/test/CI infrastructure. It uses Kotlin + Jetpack Compose and establishes environment variants, but it does **not** add Room, Supabase, MapLibre, repositories, navigation architecture or field-domain features; those belong to later approved phases. Android Gradle Plugin 9 uses built-in Kotlin; this project keeps built-in Kotlin enabled and explicitly upgrades its Kotlin Gradle Plugin runtime to the pinned Kotlin version needed by the Compose compiler plugin.

**Tech Stack:** JDK 17; Gradle 9.4.1; Android Gradle Plugin 9.2.1; Kotlin/KGP + Compose compiler plugin 2.4.20; compileSdk/targetSdk 37; minSdk 26; Jetpack Compose BOM 2026.08.00; AndroidX Core 1.19.0; Activity Compose 1.13.0; Material 3 from the BOM; AndroidX Test Core/Runner 1.7.0, JUnit extension 1.3.0 and Espresso 3.7.0.

**Spec:** `docs/00-master/RC1-BASELINE.md`, `docs/00-master/RC1-NORMATIVE-ADDENDUM.md`, `docs/00-master/CURRENT-STATE.md`, `docs/07-plans/ROADMAP-RC1.md`.

## Global Constraints

- RC1 remains Android-first and must produce a real installable APK.
- Kotlin + Jetpack Compose are mandatory.
- Do not add Room, Supabase, MapLibre, WorkManager or agricultural domain features in Phase 1.
- Do not introduce a second app module or premature feature modules in Phase 1.
- Base namespace and production application ID: `com.isivoltpro.maginaolivo`.
- Environment variants: `dev`, `staging`, `production`.
- DEV app ID: `com.isivoltpro.maginaolivo.dev`.
- STAGING app ID: `com.isivoltpro.maginaolivo.staging`.
- PRODUCTION app ID: `com.isivoltpro.maginaolivo`.
- `compileSdk = 37`, `targetSdk = 37`, `minSdk = 26`.
- Java/JVM target is 17.
- Build versions are pinned; no `+`, `latest.release`, floating or dynamic dependency versions.
- AGP built-in Kotlin remains enabled. Do not apply `org.jetbrains.kotlin.android`.
- Release signing secrets/keystores must never be committed.
- The Phase 1 application surface is a smoke shell only. Do not implement the RC1 design system early; that is Phase 3.
- Gate 1 is not passed by CI/emulator alone. A DEV APK must launch successfully on a physical Android device.
- `main` remains stable; implementation belongs on `feat/android-foundation`.

## Toolchain decision

Pin this compatibility set for Phase 1:

```text
JDK                 17
Gradle              9.4.1
AGP                 9.2.1
Kotlin / KGP        2.4.20
Compose Compiler    2.4.20
compileSdk          37
targetSdk           37
minSdk              26
Compose BOM         2026.08.00
```

Why AGP 9.2.1 rather than blindly taking the newest AGP: this Phase 1 pin prioritizes a small verified compatibility surface. AGP 9.2 supports API 37 and requires Gradle 9.4.1/JDK 17; 9.2.1 also contains a post-9.2.0 fix. Kotlin 2.4.20 documents full compatibility with Gradle 7.6.3–9.7.0 and AGP 8.5.2–9.3.1. This combination therefore stays inside the documented compatibility ranges while supporting Android 17/API 37.

Official references checked when this plan was written:

- `https://developer.android.com/build/releases/agp-9-2-0-release-notes`
- `https://developer.android.com/build/migrate-to-built-in-kotlin`
- `https://kotlinlang.org/docs/gradle-configure-project.html`
- `https://developer.android.com/about/versions/17/setup-sdk`
- `https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler`
- `https://developer.android.com/develop/ui/compose/bom`

---

## Planned file tree after Phase 1

```text
.
├── .editorconfig
├── .gitignore
├── .github/
│   └── workflows/
│       └── android-ci.yml
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── settings.gradle.kts
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/isivoltpro/maginaolivo/
│       │   │   ├── MainActivity.kt
│       │   │   └── FoundationScreen.kt
│       │   └── res/values/
│       │       ├── strings.xml
│       │       └── themes.xml
│       ├── test/java/com/isivoltpro/maginaolivo/
│       │   └── FoundationContractTest.kt
│       └── androidTest/java/com/isivoltpro/maginaolivo/
│           └── MainActivitySmokeTest.kt
└── docs/
    ├── 01-architecture/
    │   └── ADR-008-ANDROID-TOOLCHAIN.md
    ├── 06-testing/
    │   └── PHASE1-GATE-CHECKLIST.md
    └── superpowers/plans/
        └── 2026-09-17-android-foundation.md
```

Do not create Phase 2 module structure yet.

---

### Task 1: Bootstrap the reproducible Gradle/Android toolchain

**Files:**
- Create: `.editorconfig`
- Create: `.gitignore`
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/wrapper/gradle-wrapper.jar` via Gradle wrapper generation
- Create: `gradlew` via Gradle wrapper generation
- Create: `gradlew.bat` via Gradle wrapper generation
- Create: `docs/01-architecture/ADR-008-ANDROID-TOOLCHAIN.md`

**Interfaces:**
- Consumes: RC1 baseline only.
- Produces: a deterministic Gradle 9.4.1 Android build root that Task 2 can attach the `:app` module to.

- [ ] **Step 1: Create the version catalog**

Create `gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.2.1"
kotlin = "2.4.20"
coreKtx = "1.19.0"
activityCompose = "1.13.0"
composeBom = "2026.08.00"
junit4 = "4.13.2"
testCore = "1.7.0"
testRunner = "1.7.0"
testExtJunit = "1.3.0"
espresso = "3.7.0"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
junit4 = { module = "junit:junit", version.ref = "junit4" }
androidx-test-core = { module = "androidx.test:core", version.ref = "testCore" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "testRunner" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "testExtJunit" }
androidx-test-espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

Do **not** declare `org.jetbrains.kotlin.android`; AGP 9 built-in Kotlin is the chosen path.

- [ ] **Step 2: Create Gradle root settings**

Create `settings.gradle.kts`:

```kotlin
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

rootProject.name = "MaginaOlivo"
include(":app")
```

- [ ] **Step 3: Configure built-in Kotlin and root plugins**

Create `build.gradle.kts`:

```kotlin
buildscript {
    dependencies {
        // AGP 9 built-in Kotlin is kept enabled. This explicitly upgrades
        // the KGP runtime to the version paired with the Compose compiler.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
```

Create `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.nonTransitiveRClass=true
```

Do not set `android.builtInKotlin=false` or `android.newDsl=false`.

- [ ] **Step 4: Add repository hygiene files**

Create `.gitignore`:

```gitignore
*.iml
.gradle/
.kotlin/
/local.properties
/.idea/caches/
/.idea/libraries/
/.idea/modules.xml
/.idea/workspace.xml
/.idea/navEditor.xml
/.idea/assetWizardSettings.xml
.DS_Store
/build/
/captures/
.externalNativeBuild/
.cxx/
*.jks
*.keystore
keystore.properties
```

Create `.editorconfig`:

```ini
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
indent_style = space
indent_size = 4
trim_trailing_whitespace = true

[*.{yml,yaml}]
indent_size = 2

[*.md]
trim_trailing_whitespace = false
```

- [ ] **Step 5: Generate the Gradle 9.4.1 wrapper**

On a machine with Gradle available, run:

```bash
gradle wrapper --gradle-version 9.4.1 --distribution-type bin
```

Expected artifacts:

```text
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
```

Verify `gradle-wrapper.properties` points at:

```text
https\://services.gradle.org/distributions/gradle-9.4.1-bin.zip
```

- [ ] **Step 6: Run the wrapper version check**

Linux/macOS:

```bash
./gradlew --version
```

Windows:

```powershell
.\gradlew.bat --version
```

Expected essentials:

```text
Gradle 9.4.1
JVM 17.x
```

If the JDK is not 17, fix the environment before proceeding. Do not change the project target to match the wrong local JDK.

- [ ] **Step 7: Record the toolchain ADR**

Create `docs/01-architecture/ADR-008-ANDROID-TOOLCHAIN.md` with:

```markdown
# ADR-008 — Android Phase 1 Toolchain

Status: Accepted for RC1 Phase 1
Date: 2026-09-17

## Decision

Mágina Olivo Phase 1 uses JDK 17, Gradle 9.4.1, AGP 9.2.1,
AGP built-in Kotlin with KGP explicitly upgraded to Kotlin 2.4.20,
Compose Compiler 2.4.20, compileSdk/targetSdk 37 and Compose BOM 2026.08.00.

## Why

The combination remains inside the documented Gradle/Kotlin/AGP
compatibility ranges used by this plan, supports API 37 and avoids dynamic
version upgrades. Built-in Kotlin is retained because AGP 9 enables it by
default and Android recommends migrating to it rather than opting out.

## Consequence

Changing AGP/Kotlin/Gradle as part of an unrelated feature is prohibited.
A toolchain update requires its own reviewed dependency/ADR change.
```

- [ ] **Step 8: Commit Task 1**

```bash
git add .editorconfig .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle gradlew gradlew.bat docs/01-architecture/ADR-008-ANDROID-TOOLCHAIN.md
git commit -m "build(android): pin Phase 1 toolchain"
```

---

### Task 2: Create the single Android application module and three environments

**Files:**
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`

**Interfaces:**
- Consumes: Gradle root from Task 1.
- Produces: `devDebug`, `stagingDebug`, `productionDebug` and corresponding release variants with stable application IDs.

- [ ] **Step 1: Add the application module configuration**

Create `app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
```

Do not add Room/Supabase/MapLibre/Navigation/WorkManager dependencies here.

- [ ] **Step 2: Create the minimal manifest**

Create `app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.MaginaOlivo">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
```

Do not request Internet, location, camera or storage permissions in Phase 1.

- [ ] **Step 3: Add minimal resources**

Create `app/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="foundation_title">Mágina Olivo</string>
    <string name="foundation_subtitle">Gestión privada de tu olivar</string>
</resources>
```

`app_name` is supplied per flavor with `resValue`, so do not duplicate it here.

Create `app/src/main/res/values/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.MaginaOlivo" parent="android:style/Theme.Material.Light.NoActionBar">
        <item name="android:windowLightStatusBar">true</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/white</item>
    </style>
</resources>
```

This is a bootstrap theme only. Phase 3 owns the real Compose design system.

Create `app/proguard-rules.pro` as an intentionally empty project rule file with a comment explaining that rules are added only when dependencies require them.

- [ ] **Step 4: Ask Gradle to enumerate variants**

```bash
./gradlew tasks --all
```

Verify the output contains at least:

```text
assembleDevDebug
assembleStagingDebug
assembleProductionDebug
```

- [ ] **Step 5: Compile resources/configuration before adding UI**

```bash
./gradlew :app:processDevDebugResources :app:processStagingDebugResources :app:processProductionDebugResources
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit Task 2**

```bash
git add app
git commit -m "build(android): add environment-aware app module"
```

---

### Task 3: Add the smallest Compose launcher shell

**Files:**
- Create: `app/src/main/java/com/isivoltpro/maginaolivo/MainActivity.kt`
- Create: `app/src/main/java/com/isivoltpro/maginaolivo/FoundationScreen.kt`
- Test later in Task 4: `app/src/androidTest/java/com/isivoltpro/maginaolivo/MainActivitySmokeTest.kt`

**Interfaces:**
- Consumes: `BuildConfig.ENVIRONMENT` from Task 2.
- Produces: launcher UI with stable semantics test tag `foundation-root`.

- [ ] **Step 1: Create the launcher activity**

Create `MainActivity.kt`:

```kotlin
package com.isivoltpro.maginaolivo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FoundationScreen(environment = BuildConfig.ENVIRONMENT)
        }
    }
}
```

- [ ] **Step 2: Create the minimal smoke screen**

Create `FoundationScreen.kt`:

```kotlin
package com.isivoltpro.maginaolivo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun FoundationScreen(
    environment: String,
    modifier: Modifier = Modifier,
) {
    MaterialTheme {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .testTag("foundation-root"),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Mágina Olivo",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Gestión privada de tu olivar",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (environment != "PRODUCTION") {
                    Text(
                        text = environment,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
```

Do not add bottom navigation, maps, dashboards or domain screens. Those belong to later phases.

- [ ] **Step 3: Build all debug environment variants**

```bash
./gradlew assembleDevDebug assembleStagingDebug assembleProductionDebug
```

Expected: all three APK variants build successfully.

Expected IDs/names:

```text
DEV         com.isivoltpro.maginaolivo.dev       Mágina Olivo Dev
STAGING     com.isivoltpro.maginaolivo.staging   Mágina Olivo Staging
PRODUCTION  com.isivoltpro.maginaolivo           Mágina Olivo
```

- [ ] **Step 4: Commit Task 3**

```bash
git add app/src/main/java
git commit -m "feat(android): add foundation launcher shell"
```

---

### Task 4: Establish unit and physical/instrumented smoke tests

**Files:**
- Create: `app/src/test/java/com/isivoltpro/maginaolivo/FoundationContractTest.kt`
- Create: `app/src/androidTest/java/com/isivoltpro/maginaolivo/MainActivitySmokeTest.kt`

**Interfaces:**
- Consumes: `FoundationScreen`, DEV variant.
- Produces: a local contract test and an on-device Compose smoke test required by Gate 1.

- [ ] **Step 1: Write the local contract test**

Create `FoundationContractTest.kt`:

```kotlin
package com.isivoltpro.maginaolivo

import org.junit.Assert.assertEquals
import org.junit.Test

class FoundationContractTest {
    @Test
    fun productionApplicationIdContractIsStable() {
        assertEquals("com.isivoltpro.maginaolivo", PRODUCTION_APPLICATION_ID)
    }

    private companion object {
        const val PRODUCTION_APPLICATION_ID = "com.isivoltpro.maginaolivo"
    }
}
```

This intentionally protects the production application ID from casual future renaming before publication.

- [ ] **Step 2: Run the unit test**

```bash
./gradlew testDevDebugUnitTest
```

Expected: PASS.

- [ ] **Step 3: Write the Compose launcher smoke test**

Create `MainActivitySmokeTest.kt`:

```kotlin
package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launcherShowsFoundationContent() {
        composeRule.onNodeWithTag("foundation-root").assertIsDisplayed()
        composeRule.onNodeWithText("Mágina Olivo").assertIsDisplayed()
        composeRule.onNodeWithText("DEV").assertIsDisplayed()
    }
}
```

- [ ] **Step 4: Run lint and local tests**

```bash
./gradlew lintDevDebug testDevDebugUnitTest
```

Expected: PASS with zero lint errors.

- [ ] **Step 5: Run the instrumentation test when an Android device/emulator is available**

```bash
adb devices
./gradlew connectedDevDebugAndroidTest
```

Expected: `1` test, `0` failures.

The emulator run helps development, but it does not by itself satisfy Gate 1.

- [ ] **Step 6: Commit Task 4**

```bash
git add app/src/test app/src/androidTest
git commit -m "test(android): add foundation smoke coverage"
```

---

### Task 5: Add CI that proves every PR can lint, test and build the DEV APK

**Files:**
- Create: `.github/workflows/android-ci.yml`

**Interfaces:**
- Consumes: Gradle wrapper and DEV tasks.
- Produces: reproducible PR/push checks and downloadable `devDebug` APK artifact.

- [ ] **Step 1: Create the workflow**

Create `.github/workflows/android-ci.yml`:

```yaml
name: Android CI

on:
  pull_request:
    branches: [main]
  push:
    branches: [main]

permissions:
  contents: read

jobs:
  foundation:
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - name: Checkout
        uses: actions/checkout@v6

      - name: Set up JDK 17
        uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v6
        with:
          cache-provider: basic

      - name: Install Android 37 SDK
        shell: bash
        run: |
          yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null || true
          "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
            "platforms;android-37" \
            "build-tools;37.0.0"

      - name: Verify wrapper and toolchain
        run: ./gradlew --version

      - name: Lint, unit test and build DEV APK
        run: ./gradlew --no-daemon lintDevDebug testDevDebugUnitTest assembleDevDebug

      - name: Upload DEV APK
        uses: actions/upload-artifact@v6
        with:
          name: magina-olivo-dev-debug
          path: app/build/outputs/apk/dev/debug/app-dev-debug.apk
          if-no-files-found: error
          retention-days: 14
```

CI intentionally does not run a device matrix in Phase 1. Physical-device validation is the human Gate 1 step.

- [ ] **Step 2: Verify the same CI command locally**

```bash
./gradlew --no-daemon lintDevDebug testDevDebugUnitTest assembleDevDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit Task 5**

```bash
git add .github/workflows/android-ci.yml
git commit -m "ci(android): verify and publish dev debug APK"
```

---

### Task 6: Create the explicit Gate 1 physical-device checklist

**Files:**
- Create: `docs/06-testing/PHASE1-GATE-CHECKLIST.md`

**Interfaces:**
- Consumes: DEV APK from Task 5.
- Produces: human-verifiable evidence required before Phase 1 can be marked passed.

- [ ] **Step 1: Create the checklist**

Create `docs/06-testing/PHASE1-GATE-CHECKLIST.md`:

```markdown
# Phase 1 Gate — Android Foundation

Gate 1 can be marked PASS only after all items are evidenced.

## Automated

- [ ] `./gradlew --version` reports Gradle 9.4.1 and JDK 17.
- [ ] `./gradlew lintDevDebug` passes.
- [ ] `./gradlew testDevDebugUnitTest` passes.
- [ ] `./gradlew assembleDevDebug` passes.
- [ ] GitHub Android CI passes for the implementation PR.
- [ ] CI artifact `magina-olivo-dev-debug` exists.

## Physical Android device

Record:

- Device model:
- Android version/API:
- APK commit SHA:
- Test date:

Then verify:

- [ ] `adb devices` sees the physical phone as authorized, or APK is installed manually from the CI artifact.
- [ ] DEV APK installs without replacing production/staging package IDs.
- [ ] Launcher shows `Mágina Olivo Dev`.
- [ ] App opens without crash.
- [ ] Screen displays `Mágina Olivo` and `DEV`.
- [ ] Android back/home/reopen does not crash.
- [ ] Force-stop and reopen succeeds.
- [ ] Device rotation/configuration change does not crash where supported.
- [ ] `connectedDevDebugAndroidTest` passes on a device when ADB execution is available.

## Gate result

- [ ] PASS — only after the physical-device section is complete.

When PASS is checked, update `docs/00-master/CURRENT-STATE.md` to:

`✅ Phase 1 — Android Project Foundation`

and set:

`▶ Phase 2 — Base Architecture`
```

- [ ] **Step 2: Commit Task 6**

```bash
git add docs/06-testing/PHASE1-GATE-CHECKLIST.md
git commit -m "docs(test): define Phase 1 physical Android gate"
```

---

### Task 7: Final implementation verification and review before Gate 1

**Files:**
- Modify only if verification finds defects in Phase 1 files.
- Do **not** update `CURRENT-STATE.md` until physical Gate 1 evidence exists.

**Interfaces:**
- Consumes: all previous Phase 1 tasks.
- Produces: implementation PR ready for physical-device Gate evaluation.

- [ ] **Step 1: Verify the branch diff stays inside Phase 1 scope**

Run:

```bash
git diff --stat main...HEAD
git diff --name-only main...HEAD
```

Reject accidental additions of:

```text
Room
Supabase
MapLibre
WorkManager
farm/parcel/campaign business logic
bottom navigation
dashboard screens
```

- [ ] **Step 2: Run the complete non-device verification**

Linux/macOS:

```bash
./gradlew --version
./gradlew clean lintDevDebug testDevDebugUnitTest assembleDevDebug assembleStagingDebug assembleProductionDebug
```

Windows:

```powershell
.\gradlew.bat --version
.\gradlew.bat clean lintDevDebug testDevDebugUnitTest assembleDevDebug assembleStagingDebug assembleProductionDebug
```

Expected: every command exits `0`.

- [ ] **Step 3: Verify APK output**

Expected DEV file:

```text
app/build/outputs/apk/dev/debug/app-dev-debug.apk
```

Do not rename or manually copy an unrelated APK to satisfy the gate.

- [ ] **Step 4: Open the implementation PR**

PR title:

```text
feat: establish Mágina Olivo Android foundation
```

PR body must include:

```text
Phase: 1 — Android Project Foundation
Baseline: RC1-BASELINE-2026-09-17
Automated verification: exact commands + results
CI: link/status
Gate 1 physical device: PENDING or PASS with evidence
Out-of-scope features added: none
```

- [ ] **Step 5: Run physical device validation**

With an authorized phone connected:

```bash
adb devices
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb shell am force-stop com.isivoltpro.maginaolivo.dev
adb shell monkey -p com.isivoltpro.maginaolivo.dev -c android.intent.category.LAUNCHER 1
./gradlew connectedDevDebugAndroidTest
```

On Windows, use `gradlew.bat` for the last command.

Record model, Android API, commit SHA and outcomes in `PHASE1-GATE-CHECKLIST.md`.

- [ ] **Step 6: Only after Gate 1 PASS, update continuity state**

Modify `docs/00-master/CURRENT-STATE.md`:

```text
✅ Phase 1 — Android Project Foundation
▶ Phase 2 — Base Architecture
```

Also update the roadmap current-state header. Do not modify the baseline architecture itself.

- [ ] **Step 7: Commit Gate evidence**

```bash
git add docs/00-master/CURRENT-STATE.md docs/06-testing/PHASE1-GATE-CHECKLIST.md docs/07-plans/ROADMAP-RC1.md
git commit -m "docs: record Phase 1 Gate pass"
```

- [ ] **Step 8: Merge only after review and Gate evidence**

The implementation PR can be merged only when:

```text
lint PASS
unit tests PASS
all three debug variants build
GitHub CI PASS
physical DEV APK launch PASS
instrumented smoke test PASS on available physical device
review finds no Phase 2+ scope creep
```

After merge, Phase 2 becomes the only allowed active phase.

---

## Phase 1 acceptance summary

Phase 1 does **not** mean Mágina Olivo has farm-management functionality yet. It means we have proven the engineering delivery path:

```text
source
  ↓
reproducible Gradle build
  ↓
Kotlin + Compose app
  ↓
DEV / STAGING / PRODUCTION identities
  ↓
lint + unit tests
  ↓
CI
  ↓
APK artifact
  ↓
physical Android install + launch
```

That proof is intentionally completed before Phase 2 architecture and before any field-domain implementation.
