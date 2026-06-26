// FuseMind Wear OS companion — app module.
//
// Phase 1 (P1-001): build the launchable Wear OS app that stands up the GATT
// server in FuseMindWatchService and advertises the FuseMind service UUID.
// Kept deliberately small — no Compose yet (status UI is P1-004).

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.fusemind.wearos"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.fusemind.wearos"
        // Wear OS 3 = API 30; Samsung Watch 6 ships Wear OS 4 (API 33/34).
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.0")
}
