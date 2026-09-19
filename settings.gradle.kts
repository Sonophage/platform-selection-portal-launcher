pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.android.application" || requested.id.id == "com.android.library") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PSPLauncher"

include(":app")

// Desktop companion (Compose Multiplatform Desktop — Windows/Linux/macOS)
include(":studio")
include(":baselineprofile")   // com.android.test: generates the startup profile, never shipped

// Core modules
include(":core:theme-kit")   // pure JVM: theme parsing/conversion shared with the desktop companion
include(":core:core-archive")  // pure JVM: bounded/confined ZIP ingestion shared by themes and backup
include(":core:core-common")
include(":core:core-domain")
include(":core:core-data")
include(":core:core-ui")
include(":core:core-navigation")

// Feature modules
include(":feature:feature-xmb")
include(":feature:feature-library")
include(":feature:feature-launcher")
include(":feature:feature-artwork")
include(":feature:feature-themes")
include(":feature:feature-settings")
include(":feature:feature-appbar")
include(":feature:feature-backup")
