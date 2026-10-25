import java.util.Properties

/**
 * Release signing credentials live in local.properties, which is git-ignored, so no
 * password is ever committed. Without them the release build simply falls back to
 * being unsigned rather than failing, which keeps a fresh clone buildable.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.twisham.attendance"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.twisham.attendance"
        // API 26 gives us java.time without core-library desugaring, and covers
        // essentially every phone still receiving apps.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            val storePath = rootProject.file("keystore/release.jks")
            val password = localProperties.getProperty("releaseStorePassword")
            if (storePath.exists() && password != null) {
                storeFile = storePath
                storePassword = password
                keyAlias = localProperties.getProperty("releaseKeyAlias") ?: "attendance"
                keyPassword = localProperties.getProperty("releaseKeyPassword") ?: password
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseStore = rootProject.file("keystore/release.jks")
            if (releaseStore.exists() && localProperties.getProperty("releaseStorePassword") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        checkDependencies = true
        // compileSdk is 37 so the newest APIs are available, but targetSdk stays at
        // 36: opting in to a platform's new runtime behaviours is a decision to make
        // deliberately after testing on that platform, not one to take from a lint
        // nudge. Raise this once the app has actually been exercised on API 37.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "OldTargetApi")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    // Compose UI tests run under Robolectric, so the whole suite stays on the JVM
    // and needs no emulator. testImplementation does not inherit the BOM, so it is
    // applied again here.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
}
