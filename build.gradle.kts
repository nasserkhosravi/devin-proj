// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        // repo.snapp.tech is a private mirror that CI runners (e.g. GitHub Actions, which sets CI=true) can't reach.
        if (System.getenv("CI") == "true") {
            google()
            mavenCentral()
        } else {
            maven {
                url = java.net.URI("https://repo.snapp.tech/repository/android-repo-google/")
            }
            maven {
                url = java.net.URI("https://repo.snapp.tech/repository/android-repo-maven-google/")
            }
            maven {
                url = java.net.URI("https://repo.snapp.tech/repository/android-repo-maven/")
            }
            maven {
                url = java.net.URI("https://repo.snapp.tech/repository/android-repo-plugins/")
            }
            maven {
                url = java.net.URI("https://repo.snapp.tech/repository/android-repo-jcenter-bintray/")
            }
        }
    }
    dependencies {
        classpath(libs.plugins.androidApplication.get().toString())
        classpath(libs.plugins.kotlinAndroid.get().toString())
    }
}

plugins {
    id("com.vanniktech.maven.publish") version "0.34.0" apply false
}

tasks.register("clean").configure {
    delete("build")
}
