import java.net.URI

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
            name = "Central Portal Snapshots"
            url = URI("https://central.sonatype.com/repository/maven-snapshots/")

            // Only search this repository for the specific dependency
            content {
                includeModule("io.github.nasserkhosravi.devin", "write")
            }
        }
        maven { url = URI("https://jitpack.io") }
    }
}

rootProject.name = "DevinProj"
include(":sample-app")
include(":present-app")
include(":devin")
include(":devin-no-op")
//include(":devin-api")
include(":devin-write-okhttp")
include(":devin-write-okhttp-no-op")

include(":lib-calendar")
include(":lib-har")