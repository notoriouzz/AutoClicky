pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
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

rootProject.name = "AutoClicky"
include(":app")

// Vendored from Smart AutoClicker (Klick'r) - see THIRD_PARTY_NOTICES.md
include(":core:common:base")
include(":core:common:settings")
include(":core:common:actions")
include(":core:dumb")
include(":core:common:bitmaps")
include(":core:common:display")
include(":core:common:ui")
include(":core:common:overlays")
include(":core:common:permissions")

 