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
include(":core:sync")
include(":feature:onboarding")
include(":feature:calendar")
include(":feature:stats")
include(":feature:insights")
include(":feature:wellness")
include(":feature:security")
include(":feature:account")
include(":feature:partner")
include(":feature:reminders")
include(":feature:widgets")
include(":feature:daylog")
include(":feature:pet")
include(":feature:settings")
