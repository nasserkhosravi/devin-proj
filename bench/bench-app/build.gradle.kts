buildscript {
    repositories {
        // Same mirror setup as the main DevinProj build; CI uses the public repositories.
        if (System.getenv("CI") == "true") {
            google()
            mavenCentral()
        } else {
            maven { url = java.net.URI("https://repo.snapp.tech/repository/android-repo-google/") }
            maven { url = java.net.URI("https://repo.snapp.tech/repository/android-repo-maven-google/") }
            maven { url = java.net.URI("https://repo.snapp.tech/repository/android-repo-maven/") }
            maven { url = java.net.URI("https://repo.snapp.tech/repository/android-repo-plugins/") }
        }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.10.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.20")
    }
}
