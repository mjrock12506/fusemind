// ANCS spike — app module. Plain Android app (installs on an Android phone OR
// the Wear OS Watch 6 via adb) acting as a BLE central / ANCS consumer.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.fusemind.spike.ancs"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.fusemind.spike.ancs"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-spike"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
