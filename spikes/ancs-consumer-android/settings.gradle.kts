// FuseMind — ANCS feasibility SPIKE (throwaway).
// Standalone Android project, intentionally NOT part of watch-adapters/wear-os.
// Purpose: prove whether a BLE central can read iPhone ANCS. See
// docs/spikes/ancs-spike-README.md and docs/adr/ADR-002-ancs-watch-consumer.md.

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

rootProject.name = "AncsConsumerSpike"
include(":app")
