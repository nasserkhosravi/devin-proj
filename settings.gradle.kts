import java.net.URI

include(":devin-write-okhttp-no-op")


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
        maven {
            url = URI("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        }
        maven { url = URI("https://jitpack.io") }
    }
}

rootProject.name = "DevinProj"
include(":sample-app")
include(":present-app")
include(":devin")
//include(":devin-api")
include(":devin-no-op")
include(":lib-calendar")
include(":lib-har")
include(":devin-write-okhttp")