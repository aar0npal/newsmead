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

rootProject.name = "NewsMead"
include(":app")
// Explicit research build only; the ordinary app never depends on this module.
if (providers.gradleProperty("mgazenetBenchmark").orNull == "true") {
    include(":mgazenet-benchmark")
}
