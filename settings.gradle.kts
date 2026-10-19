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

rootProject.name = "LaunchOnMe"
include(":app")
include(":studio") // StickOnMe — kod studia naklejek (biblioteka)
include(":stickonme") // StickOnMe — osobna aplikacja (własny APK) zbudowana z :studio
include(":themes") // OnThemes — aplikacja motywów (osobny moduł, w pakiecie z launcherem)
 
 
include(":onhand") // OnHand — notatki (osobny moduł, w pakiecie z launcherem)
