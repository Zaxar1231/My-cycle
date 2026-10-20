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

rootProject.name = "TwilightCycle"

include(":app")
include(":core:domain")
include(":core:data")
include(":core:ui")
include(":core:update")
include(":feature:onboarding")
include(":feature:calendar")
include(":feature:stats")
include(":feature:daylog")
include(":feature:pet")
include(":feature:settings")
