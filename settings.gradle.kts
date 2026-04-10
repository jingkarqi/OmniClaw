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

rootProject.name = "OmniClaw"
include(":app")

include(
    ":core:common",
    ":core:model",
    ":core:storage",
)

include(
    ":bridge:api",
    ":bridge:impl",
)

include(
    ":runtime:api",
    ":runtime:impl",
    ":runtime:payloads",
)

include(
    ":domain:runtime",
    ":domain:bridge",
    ":domain:provider",
)

include(
    ":feature:home",
    ":feature:provider",
    ":feature:runtime",
    ":feature:permissions",
)

include(
    ":service:host",
    ":testing:fake",
)
 
