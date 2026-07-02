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
        // Signal publishes current libsignal-android releases here rather than Maven Central.
        maven { url = uri("https://build-artifacts.signal.org/libraries/maven/") }
    }
}

rootProject.name = "P2P Messenger"
include(":app")
include(":tools:pc-client")
