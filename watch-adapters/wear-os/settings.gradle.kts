// FuseMind Wear OS companion — Gradle settings.
// One module (:app) holds the launcher Activity, the foreground Service, and
// FuseMindWatchService (the GATT server). See docs/issues/phase-1-ble.md (P1-001).

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

rootProject.name = "FuseMindWearOS"
include(":app")
