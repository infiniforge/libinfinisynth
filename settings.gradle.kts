pluginManagement {
    resolutionStrategy.eachPlugin { if (requested.id.id.startsWith("com.android.")) useModule("com.android.tools.build:gradle:8.9.2") }
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "libinfinisynth"
include(":synth-core")
if (!providers.gradleProperty("synthCoreOnly").isPresent) include(":synth-android")
